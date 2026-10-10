package com.vibe.store.application.delivery

import com.vibe.store.api.*
import com.vibe.store.application.persistence.*
import com.vibe.store.application.security.IdentityAuthority
import com.vibe.store.application.security.SecretAdapter
import com.vibe.store.api.WorkflowError
import com.vibe.store.api.WorkflowFailure
import kotlinx.coroutines.CancellationException
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Locale

/** Authorization and durable queue orchestration. SMTP is always outside the Room transaction. */
class EmailAuthority(
    private val identity: IdentityAuthority,
    private val reporting: ReportingService,
    private val documents: DocumentService,
    private val secrets: SecretAdapter,
    private val smtp: SmtpTransport,
    private val clock: () -> Instant = Instant::now,
) : DeliveryService {
    private data class MailConfig(val public: SmtpConfiguration, val envelope: String?)
    private data class Claim(val record: EmailRecord, val nonce: String, val config: SmtpConfiguration)
    private data class BoundConfig(val actor: Long, val nonce: String, val generation: String, val config: MailConfig)

    override suspend fun smtpConfiguration(): SmtpConfiguration = identity.authorizedRead("SETTINGS:READ") {
        configuration(settings).public
    }

    override suspend fun configureSmtp(input: SmtpConfigurationInput): SmtpConfiguration =
        identity.authorizedWriteWithSession("SETTINGS:UPDATE") { _, _, _ ->
            validate(input)
            val values = settings
            values.put(KEY_HOST, input.host.trim())
            values.put(KEY_PORT, input.port.toString())
            values.put(KEY_USER, input.username.trim())
            values.put(KEY_FROM, input.sender.trim())
            values.put(KEY_TLS, input.securityMode.name)
            when {
                input.clearPassword -> values.put(KEY_SECRET, "")
                input.replacementPassword.isNotEmpty() -> {
                    val raw = input.replacementPassword.toByteArray(StandardCharsets.UTF_8)
                    try { values.put(KEY_SECRET, secrets.seal(raw).toHex()) } finally { raw.fill(0) }
                }
            }
            configuration(values).public
        }

    override suspend fun testSmtp(recipient: String): SmtpProbeResult {
        if (!validEmail(recipient)) throw WorkflowFailure(WorkflowError.INVALID_INPUT)
        val bound = identity.authorizedReadWithSession("SETTINGS:VALIDATE") { actor, nonce, generation ->
            BoundConfig(actor, nonce, generation, configuration(settings))
        }
        val loaded = bound.config
        val config = loaded.public
        val secret = readSecret(loaded.envelope) ?: return SmtpProbeResult.CONFIGURATION_REQUIRED
        val probe = SmtpSettingsRecord(config.host, config.port, config.username, config.sender,
            config.securityMode == SmtpSecurityMode.IMPLICIT_TLS, null)
        return try {
            val stillAuthorized = identity.authorizedReadWithSession("SETTINGS:VALIDATE") { actor, nonce, generation ->
                val fresh = configuration(settings)
                actor == bound.actor && nonce == bound.nonce && generation == bound.generation &&
                    fresh.envelope == loaded.envelope && fresh.public.host == config.host && fresh.public.port == config.port &&
                    fresh.public.username == config.username && fresh.public.sender == config.sender &&
                    fresh.public.securityMode == config.securityMode
            }
            if (!stillAuthorized) return SmtpProbeResult.REJECTED
            smtp.test(probe, secret, recipient)
            SmtpProbeResult.ACCEPTED
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: SmtpTransportFailure) { SmtpProbeResult.REJECTED }
        finally { secret.fill('\u0000') }
    }

    override suspend fun queueReport(request: EmailReportRequest): EmailQueueEntry {
        validateRecipientAndNote(request.recipient, request.note)
        val session = identity.authorizedReadWithSession("FINANCES:CREATE") { actor, nonce, generation ->
            Triple(actor, nonce, generation)
        }
        val model = reporting.snapshot(request.filter)
        val rendered = documents.render(DocumentRequest(
            requestId = "mail-${model.reportId}", type = DocumentType.REPORT,
            title = if (request.language == "fr") "Rapport STORE" else "STORE report",
            createdAt = clock(), snapshot = DocumentSnapshot.Report(model), language = request.language,
        ))
        val bytes = rendered.content.copyBytes()
        try {
            val now = clock()
            val row = identity.authorizedWriteWithSession("FINANCES:CREATE") { actor, nonce, generation ->
                if (actor != session.first || nonce != session.second || generation != session.third)
                    throw WorkflowFailure(WorkflowError.CONFLICT)
                val id = delivery.nextEmailId()
                val period = "${request.filter.fromInclusive}/${request.filter.toExclusive} (${request.filter.timeZoneId})"
                val message = buildString {
                    append(if (request.language == "fr") "Rapport: " else "Report: ")
                    append(model.tab.name).append("; ").append(period).append("; ")
                    append(if (model.currencyAvailability == ReportCurrencyAvailability.AVAILABLE) model.currency
                        else if (request.language == "fr") "devise indisponible" else "currency unavailable")
                    if (request.note.isNotBlank()) append("\n\n").append(request.note.trim())
                }.take(10_000)
                val record = EmailRecord(id, actor, generation, request.recipient.trim(),
                    (if (request.language == "fr") "Rapport STORE " else "STORE report ") + model.tab.name,
                    rendered.fileName, message, bytes.copyOf(), "pending", 0, now, now, null, null)
                delivery.insertEmail(record)
                record
            }
            return row.toEntry()
        } finally { bytes.fill(0) }
    }

    override suspend fun emailQueue(offset: Int, limit: Int): List<EmailQueueEntry> {
        require(offset >= 0 && limit in 1..100)
        return identity.authorizedReadWithSession("FINANCES:READ") { actor, _, _ ->
            delivery.emailPage(actor, limit, offset).map { it.toEntry() }
        }
    }

    override suspend fun retryEmail(id: Long): Boolean = identity.authorizedWriteWithSession("FINANCES:CREATE") { actor, _, generation ->
        val row = delivery.email(id) ?: return@authorizedWriteWithSession false
        if (row.actorId != actor || row.generation != generation || row.status != "failed") return@authorizedWriteWithSession false
        delivery.updateEmail(row.copy(status = "pending", attempts = 0, nextAttemptAt = clock(), lastError = null))
    }

    override suspend fun deliverNextAuthorized(): DeliveryRunResult {
        val claimed = identity.authorizedWriteWithSession("FINANCES:CREATE") { actor, nonce, generation ->
            val loaded = configuration(settings)
            val config = loaded.public
            val row = delivery.dueEmail(actor, generation, clock()) ?: return@authorizedWriteWithSession null to loaded
            if (!config.passwordConfigured || !config.passwordAvailable) {
                val code = if (config.passwordConfigured) "SMTP_SECRET_UNAVAILABLE" else "SMTP_NOT_CONFIGURED"
                val retry = com.vibe.store.domain.EmailDeliveryPolicy.failure(row.attempts, code, clock())
                delivery.updateEmail(row.copy(status = retry.status, attempts = retry.attempts,
                    nextAttemptAt = retry.nextAttemptAt, lastError = code))
                return@authorizedWriteWithSession null to loaded
            }
            val sending = row.copy(status = "sending")
            if (!delivery.updateEmail(sending)) return@authorizedWriteWithSession null to loaded
            Claim(sending, nonce, config) to loaded
        }
        val claim = claimed.first ?: return if (!claimed.second.public.passwordConfigured || !claimed.second.public.passwordAvailable)
            DeliveryRunResult.CONFIGURATION_REQUIRED else DeliveryRunResult.NOTHING_DUE
        val record = claim.record
        val config = claim.config
        val secret = readSecret(claimed.second.envelope) ?: run {
            restorePending(record, claim.nonce)
            return DeliveryRunResult.CONFIGURATION_REQUIRED
        }
        val transportSettings = SmtpSettingsRecord(config.host, config.port, config.username, config.sender,
            config.securityMode == SmtpSecurityMode.IMPLICIT_TLS, null)
        return try {
            smtp.send(transportSettings, secret, record)
            identity.authorizedWriteWithSession("FINANCES:CREATE") { actor, nonce, generation ->
                if (actor == record.actorId && generation == record.generation && nonce == claim.nonce) {
                    delivery.updateEmail(record.copy(status = "sent", sentAt = clock(), nextAttemptAt = null, lastError = null))
                }
            }
            DeliveryRunResult.SENT
        } catch (cancel: CancellationException) { throw cancel }
        catch (failure: SmtpTransportFailure) {
            identity.authorizedWriteWithSession("FINANCES:CREATE") { actor, nonce, generation ->
                if (actor == record.actorId && generation == record.generation && nonce == claim.nonce) {
                    val retry = com.vibe.store.domain.EmailDeliveryPolicy.failure(record.attempts, failure.code, clock())
                    delivery.updateEmail(record.copy(status = retry.status, attempts = retry.attempts,
                        nextAttemptAt = retry.nextAttemptAt, lastError = failure.code))
                }
            }
            DeliveryRunResult.RETAINED_FOR_RETRY
        } catch (_: Exception) {
            identity.authorizedWriteWithSession("FINANCES:CREATE") { actor, nonce, generation ->
                if (actor == record.actorId && generation == record.generation && nonce == claim.nonce) {
                    val retry = com.vibe.store.domain.EmailDeliveryPolicy.failure(record.attempts, "SMTP_NETWORK_FAILURE", clock())
                    delivery.updateEmail(record.copy(status = retry.status, attempts = retry.attempts,
                        nextAttemptAt = retry.nextAttemptAt, lastError = "SMTP_NETWORK_FAILURE"))
                }
            }
            DeliveryRunResult.RETAINED_FOR_RETRY
        } finally { secret.fill('\u0000') }
    }

    override suspend fun recoverInterruptedAuthorized(): Int = identity.authorizedWriteWithSession("FINANCES:CREATE") { actor, _, generation ->
        delivery.recoverInterrupted(actor, generation)
    }

    override suspend fun nextAuthorizedDeliveryDelayMillis(): Long? = identity.authorizedReadWithSession("FINANCES:CREATE") { actor, _, generation ->
        delivery.nextPendingAt(actor, generation)?.let { java.time.Duration.between(clock(), it).toMillis().coerceAtLeast(0L) }
    }

    private suspend fun restorePending(record: EmailRecord, expectedNonce: String) {
        identity.authorizedWriteWithSession("FINANCES:CREATE") { actor, nonce, generation ->
            if (actor == record.actorId && nonce == expectedNonce && generation == record.generation)
                delivery.updateEmail(record.copy(status = "pending", nextAttemptAt = clock()))
        }
    }

    private fun readSecret(envelope: String?): CharArray? {
        if (envelope.isNullOrEmpty()) return null
        val bytes = try { secrets.open(envelope.fromHex()) } catch (_: Exception) { null } ?: return null
        return try { bytes.toString(StandardCharsets.UTF_8).toCharArray() } finally { bytes.fill(0) }
    }

    private suspend fun ReadRepositories.configuration(reader: SettingsReader): MailConfig {
        val host = reader.value(KEY_HOST).orEmpty()
        val port = reader.value(KEY_PORT)?.toIntOrNull() ?: 0
        val username = reader.value(KEY_USER).orEmpty()
        val sender = reader.value(KEY_FROM).orEmpty()
        val mode = reader.value(KEY_TLS)?.let { runCatching { SmtpSecurityMode.valueOf(it) }.getOrNull() }
            ?: SmtpSecurityMode.STARTTLS_REQUIRED
        val envelope = reader.value(KEY_SECRET).orEmpty()
        val available = envelope.isNotEmpty() && runCatching {
            secrets.open(envelope.fromHex())?.also { it.fill(0) } != null
        }.getOrDefault(false)
        return MailConfig(SmtpConfiguration(host, port, username, sender, mode, envelope.isNotEmpty(), available), envelope.takeIf(String::isNotEmpty))
    }

    private fun validate(input: SmtpConfigurationInput) {
        val host = input.host.trim(); val user = input.username.trim(); val sender = input.sender.trim()
        if (!host.matches(Regex("[A-Za-z0-9.-]{1,253}")) || host.startsWith('.') || host.endsWith('.') ||
            input.port !in 1..65535 || user.length > 320 || user.any(::unsafe) ||
            !validEmail(sender) || input.replacementPassword.length > 4096 ||
            input.replacementPassword.any(::unsafe) || input.clearPassword && input.replacementPassword.isNotEmpty())
            throw WorkflowFailure(WorkflowError.INVALID_INPUT)
        val passwordBytes = input.replacementPassword.toByteArray(StandardCharsets.UTF_8)
        try { if (passwordBytes.size > 4096) throw WorkflowFailure(WorkflowError.INVALID_INPUT) }
        finally { passwordBytes.fill(0) }
    }

    private fun validateRecipientAndNote(recipient: String, note: String) {
        if (!validEmail(recipient) || note.length > 2000 || note.any(::unsafe)) throw WorkflowFailure(WorkflowError.INVALID_INPUT)
    }
    private fun validEmail(value: String) = value.length in 3..320 && !value.any(::unsafe) && Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+", RegexOption.IGNORE_CASE).matches(value)
    private fun unsafe(c: Char) = c == '\r' || c == '\n' || c == '\u0000'
    private fun EmailRecord.toEntry() = EmailQueueEntry(id, recipient, subject, filename,
        EmailQueueStatus.valueOf(status.uppercase(Locale.ROOT)), attempts, createdAt, nextAttemptAt,
        lastError?.takeIf { it in SAFE_ERRORS } ?: lastError?.let { "SMTP_FAILURE" })
    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
    private fun String.fromHex(): ByteArray {
        require(length in 58..8250 && length % 2 == 0)
        return ByteArray(length / 2) { index -> substring(index * 2, index * 2 + 2).toInt(16).toByte() }
    }

    companion object {
        private const val KEY_HOST = "smtpHost"; private const val KEY_PORT = "smtpPort"
        private const val KEY_USER = "smtpUser"; private const val KEY_FROM = "smtpFrom"
        private const val KEY_TLS = "smtpSecurity"; private const val KEY_SECRET = "smtpPasswordEnvelope"
        private val SAFE_ERRORS = setOf("SMTP_TIMEOUT", "SMTP_TLS_FAILURE", "SMTP_AUTH_FAILURE", "SMTP_REJECTED",
            "SMTP_NETWORK_FAILURE", "SMTP_NOT_CONFIGURED", "SMTP_SECRET_UNAVAILABLE", "legacy_sender_unknown")
    }
}
