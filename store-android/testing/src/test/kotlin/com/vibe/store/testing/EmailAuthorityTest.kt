package com.vibe.store.testing

import com.vibe.store.api.*
import com.vibe.store.application.delivery.*
import com.vibe.store.application.persistence.*
import com.vibe.store.application.reporting.ReportingAuthority
import com.vibe.store.application.reporting.ReportingRepository
import com.vibe.store.application.security.*
import com.vibe.store.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class EmailAuthorityTest {
    private class Memory : DatabaseOwner, UnitOfWork, TransactionRepositories, SecurityRepository,
        DeliveryRecords, SettingsWriter {
        override var generation = GenerationId("main")
        val accounts = mutableMapOf<Long, AuthAccount>()
        val denials = mutableMapOf<Long, Set<String>>()
        val settingsMap = mutableMapOf<String, String>()
        val messages = mutableListOf<EmailRecord>()
        override val security get() = this
        override val delivery get() = this
        override val settings get() = this
        override val identity: IdentityRepository get() = error("unused")
        override val cash: CashRepository get() = error("unused")
        override val catalog: CatalogRepository get() = error("unused")
        override val sales: SalesRepository get() = error("unused")
        override val purchases: PurchaseRepository get() = error("unused")
        override val inventories: InventoryRepository get() = error("unused")
        override val employees: EmployeeRepository get() = error("unused")
        override val attendance: AttendanceRepository get() = error("unused")
        override val messaging: MessagingRepository get() = error("unused")
        override val audit: AuditRepository get() = error("unused")
        override suspend fun <T> read(block: suspend ReadRepositories.() -> T): T = block(this)
        override suspend fun <T> transaction(block: suspend TransactionRepositories.() -> T): T = block(this)
        override suspend fun verifyIntegrity() = IntegrityResult(true, true, true)
        override suspend fun checkpointCloseReopen() = Unit
        override suspend fun close() = Unit
        override suspend fun accounts() = accounts.values.toList()
        override suspend fun account(id: Long) = accounts[id]
        override suspend fun inherited(id: Long) = RolePolicy.inherited(accounts.getValue(id).role)
        override suspend fun denied(id: Long) = denials[id] ?: emptySet()
        override suspend fun createOwner(input: NewOwner): Long = error("unused")
        override suspend fun createManaged(input: NewManagedAccount): Long = error("unused")
        override suspend fun updateManaged(input: ManagedAccountChange) = error("unused")
        override suspend fun replaceDenials(id: Long, codes: Set<String>) { denials[id] = codes }
        override suspend fun loginResult(id: Long, failures: Int, until: Long?, successAt: String?) = Unit
        override suspend fun recoveryResult(id: Long, failures: Int, until: Long?, verifier: String?) = Unit
        override suspend fun hasOpenCash(id: Long) = false
        override suspend fun appendAudit(event: SecurityAudit) = Unit
        override suspend fun value(key: String) = settingsMap[key]
        override suspend fun put(key: String, value: String) { settingsMap[key] = value }
        override suspend fun nextEmailId() = (messages.maxOfOrNull { it.id } ?: 0) + 1
        override suspend fun insertEmail(record: EmailRecord) { messages += record }
        override suspend fun email(id: Long) = messages.firstOrNull { it.id == id }
        override suspend fun emailPage(actorId: Long, limit: Int, offset: Int) = messages.filter { it.actorId == actorId }.drop(offset).take(limit)
        override suspend fun dueEmail(actorId: Long, generation: String, now: Instant) = messages.firstOrNull { row ->
            row.actorId == actorId && row.generation == generation && row.status == "pending" &&
                (row.nextAttemptAt?.let { it <= now } ?: true)
        }
        override suspend fun nextPendingAt(actorId: Long, generation: String) = messages.filter { it.actorId == actorId && it.generation == generation && it.status == "pending" }.minOfOrNull { it.nextAttemptAt ?: it.createdAt }
        override suspend fun updateEmail(record: EmailRecord): Boolean { val i = messages.indexOfFirst { it.id == record.id }; if (i < 0) return false; messages[i] = record; return true }
        override suspend fun recoverInterrupted(actorId: Long, generation: String): Int {
            var count = 0
            messages.indices.forEach { i -> if (messages[i].actorId == actorId && messages[i].generation == generation && messages[i].status == "sending") { messages[i] = messages[i].copy(status = "pending"); count++ } }
            return count
        }
    }

    private class FakeReports : ReportingRepository {
        override suspend fun snapshot(filter: ReportFilter) = DocumentModel("report", filter.tab, filter, "EUR",
            listOf(ReportColumn("total", "Total")), emptyList(), emptyList(),
            ReportMetricSnapshot(BigDecimal.ZERO, 0, BigDecimal.ZERO, 0, BigDecimal.ZERO, MarginDisclosure.ESTIMATED_SNAPSHOT_COST))
        override suspend fun dashboard(filter: ReportFilter, includePresence: Boolean) = snapshot(filter)
    }

    private fun identity(db: Memory): IdentityAuthority {
        listOf("owner" to "owner", "manager" to "manager", "employee" to "employee").forEachIndexed { i, (name, role) ->
            db.accounts[i + 1L] = AuthAccount(i + 1L, name, "$name@example.invalid", "Synthetic", "User", role,
                true, null, "test:$name-password", null, null, 0, null, 0, null)
        }
        val hasher = object : PasswordHasher {
            override fun hash(value: String) = "test:$value"
            override fun verifies(value: String, verifier: String) = hash(value) == verifier
        }
        return IdentityAuthority(db, CommandCoordinator(db), hasher, clock = { 1_000_000L })
    }

    private fun filter() = ReportFilter(ReportTab.SALES, LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-02"), timeZoneId = "Europe/Rome")
    private fun docService() = object : DocumentService {
        override suspend fun render(request: DocumentRequest) =
            DocumentResult(request.requestId, request.type, "report.pdf", "application/pdf", BoundedDocumentInput(byteArrayOf(1, 2, 3)))
    }
    private fun secrets() = object : SecretAdapter {
        override fun seal(value: ByteArray) = ByteArray(29) { it.toByte() } + value
        override fun open(envelope: ByteArray) = if (envelope.size >= 29) envelope.copyOfRange(29, envelope.size) else null
    }
    private fun transport() = object : SmtpTransport {
        override suspend fun test(settings: SmtpSettingsRecord, secret: CharArray, recipient: String) = Unit
        override suspend fun send(settings: SmtpSettingsRecord, secret: CharArray, email: EmailRecord) = Unit
    }

    @Test fun employeeCannotConfigureSmtpOrReadDeliveryQueue() = runBlocking {
        val db = Memory(); val identity = identity(db); identity.login(Credentials("employee", "employee-password"))
        val service = EmailAuthority(identity, ReportingAuthority(identity, FakeReports()), docService(), secrets(), transport())
        try { service.smtpConfiguration(); fail("settings permission required") } catch (e: SecurityFailure) { assertEquals(SecurityError.FORBIDDEN, e.code) }
        try { service.emailQueue(); fail("finance permission required") } catch (e: SecurityFailure) { assertEquals(SecurityError.FORBIDDEN, e.code) }
        assertTrue(db.messages.isEmpty())
    }

    @Test fun queuedReportIsAuthorizedAndBoundToCurrentActorAndGeneration() = runBlocking {
        val db = Memory(); val identity = identity(db); identity.login(Credentials("manager", "manager-password"))
        val service = EmailAuthority(identity, ReportingAuthority(identity, FakeReports()), docService(), secrets(), transport(),
            clock = { Instant.parse("2026-10-10T12:00:00Z") })
        val entry = service.queueReport(EmailReportRequest(filter(), "synthetic@example.invalid", language = "fr"))
        assertEquals(1L, entry.id)
        assertEquals(2L, db.messages.single().actorId)
        assertEquals("main", db.messages.single().generation)
        assertEquals("report.pdf", entry.fileName)
        assertTrue(db.messages.single().message.contains("Europe/Rome"))
        assertEquals(1, service.emailQueue().size)
        identity.logout()
        identity.login(Credentials("owner", "owner-password"))
        assertTrue(service.emailQueue().isEmpty())
        assertFalse(service.retryEmail(entry.id))
    }

    @Test fun settingsNeverReturnSecretAndOwnerOnlyTestRequiresConfiguredSecret() = runBlocking {
        val db = Memory(); val identity = identity(db); identity.login(Credentials("owner", "owner-password"))
        val service = EmailAuthority(identity, ReportingAuthority(identity, FakeReports()), docService(), secrets(), transport())
        val saved = service.configureSmtp(SmtpConfigurationInput("smtp.example.invalid", 587, "synthetic", "store@example.invalid",
            SmtpSecurityMode.STARTTLS_REQUIRED, "synthetic-secret"))
        assertTrue(saved.passwordConfigured); assertTrue(saved.passwordAvailable)
        assertFalse(saved.toString().contains("synthetic-secret"))
        assertNotEquals("synthetic-secret", db.settingsMap["smtpPasswordEnvelope"])
        assertEquals(SmtpProbeResult.ACCEPTED, service.testSmtp("synthetic-recipient@example.invalid"))
    }

    @Test fun accountSwitchDuringPdfRenderRejectsQueueCommitWithNoEmailRecord() = runBlocking {
        val db = Memory(); val identity = identity(db); identity.login(Credentials("manager", "manager-password"))
        val service = EmailAuthority(identity, ReportingAuthority(identity, FakeReports()), object : DocumentService {
            override suspend fun render(request: DocumentRequest): DocumentResult {
                identity.logout()
                identity.login(Credentials("owner", "owner-password"))
                return DocumentResult(request.requestId, request.type, "report.pdf", "application/pdf",
                    BoundedDocumentInput(byteArrayOf(1, 2, 3)))
            }
        }, secrets(), transport())
        try {
            service.queueReport(EmailReportRequest(filter(), "synthetic@example.invalid"))
            fail("session switch must invalidate the queued request")
        } catch (_: WorkflowFailure) { }
        assertTrue(db.messages.isEmpty())
    }

    @Test fun missingSmtpSecretKeepsQueuePendingWithoutConsumingAnAttempt() = runBlocking {
        val db = Memory(); val identity = identity(db); identity.login(Credentials("owner", "owner-password"))
        var sends = 0
        val service = EmailAuthority(identity, ReportingAuthority(identity, FakeReports()), docService(), secrets(), object : SmtpTransport {
            override suspend fun test(settings: SmtpSettingsRecord, secret: CharArray, recipient: String) = Unit
            override suspend fun send(settings: SmtpSettingsRecord, secret: CharArray, email: EmailRecord) { sends++ }
        }, clock = { Instant.parse("2026-10-10T12:00:00Z") })
        service.queueReport(EmailReportRequest(filter(), "synthetic@example.invalid"))

        assertEquals(DeliveryRunResult.CONFIGURATION_REQUIRED, service.deliverNextAuthorized())
        val row = db.messages.single()
        assertEquals("pending", row.status)
        assertEquals(0, row.attempts)
        assertEquals(Instant.parse("2026-10-10T12:15:00Z"), row.nextAttemptAt)
        assertEquals(0, sends)
    }

    @Test fun authorizedDeliveryPersistsSentOnlyAfterTransportAcceptsMessage() = runBlocking {
        val db = Memory(); val identity = identity(db); identity.login(Credentials("owner", "owner-password"))
        var sends = 0
        val service = EmailAuthority(identity, ReportingAuthority(identity, FakeReports()), docService(), secrets(), object : SmtpTransport {
            override suspend fun test(settings: SmtpSettingsRecord, secret: CharArray, recipient: String) = Unit
            override suspend fun send(settings: SmtpSettingsRecord, secret: CharArray, email: EmailRecord) {
                assertEquals("sending", email.status)
                assertTrue(email.attachment!!.isNotEmpty())
                sends++
            }
        }, clock = { Instant.parse("2026-10-10T12:00:00Z") })
        service.configureSmtp(SmtpConfigurationInput("smtp.example.invalid", 587, "synthetic", "store@example.invalid",
            SmtpSecurityMode.STARTTLS_REQUIRED, "synthetic-secret"))
        val queued = service.queueReport(EmailReportRequest(filter(), "synthetic@example.invalid"))

        assertEquals(DeliveryRunResult.SENT, service.deliverNextAuthorized())
        assertEquals(1, sends)
        assertEquals("sent", db.messages.single().status)
        assertEquals(Instant.parse("2026-10-10T12:00:00Z"), db.messages.single().sentAt)
        assertEquals(queued.id, db.messages.single().id)
    }

    @Test fun revokedFinanceCreateDeniesWorkerBeforeTransport() = runBlocking {
        val db = Memory(); val identity = identity(db); identity.login(Credentials("owner", "owner-password"))
        var sends = 0
        val service = EmailAuthority(identity, ReportingAuthority(identity, FakeReports()), docService(), secrets(), object : SmtpTransport {
            override suspend fun test(settings: SmtpSettingsRecord, secret: CharArray, recipient: String) = Unit
            override suspend fun send(settings: SmtpSettingsRecord, secret: CharArray, email: EmailRecord) { sends++ }
        })
        service.configureSmtp(SmtpConfigurationInput("smtp.example.invalid", 587, "synthetic", "store@example.invalid",
            SmtpSecurityMode.STARTTLS_REQUIRED, "synthetic-secret"))
        identity.logout()
        identity.login(Credentials("manager", "manager-password"))
        service.queueReport(EmailReportRequest(filter(), "synthetic@example.invalid"))
        db.denials[2L] = setOf("FINANCES:CREATE")

        try { service.deliverNextAuthorized(); fail("revoked effective permission must deny worker") }
        catch (failure: SecurityFailure) { assertEquals(SecurityError.FORBIDDEN, failure.code) }
        assertEquals(0, sends)
        assertEquals("pending", db.messages.single().status)
    }

    @Test fun interruptedSendingRowRecoversOnlyForItsCurrentGeneration() = runBlocking {
        val db = Memory(); val identity = identity(db); identity.login(Credentials("manager", "manager-password"))
        val service = EmailAuthority(identity, ReportingAuthority(identity, FakeReports()), docService(), secrets(), transport())
        val queued = service.queueReport(EmailReportRequest(filter(), "synthetic@example.invalid"))
        db.messages[0] = db.messages[0].copy(status = "sending")

        assertEquals(1, service.recoverInterruptedAuthorized())
        assertEquals("pending", db.messages.single().status)
        identity.logout()
        identity.login(Credentials("owner", "owner-password"))
        assertEquals(0, service.recoverInterruptedAuthorized())
        assertEquals(queued.id, db.messages.single().id)
        assertEquals("pending", db.messages.single().status)
    }
}
