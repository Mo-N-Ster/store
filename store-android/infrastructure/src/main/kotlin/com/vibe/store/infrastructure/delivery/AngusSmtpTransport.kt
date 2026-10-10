package com.vibe.store.infrastructure.delivery

import com.vibe.store.application.delivery.EmailRecord
import com.vibe.store.application.delivery.SmtpSettingsRecord
import com.vibe.store.application.delivery.SmtpTransport
import com.vibe.store.application.delivery.SmtpTransportFailure
import jakarta.activation.DataHandler
import jakarta.mail.Message
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import jakarta.mail.util.ByteArrayDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.SocketTimeoutException
import java.util.Properties
import javax.net.ssl.SSLException

/** SMTP adapter with mandatory authenticated TLS and fixed network timeouts. Never enables protocol debug. */
class AngusSmtpTransport private constructor(private val syntheticTrustHost: String?) : SmtpTransport {
    constructor() : this(null)

    companion object {
        /** Test-only trust pin for a loopback SMTP fixture; production composition always uses the no-arg constructor. */
        internal fun forSyntheticServer(host: String) = AngusSmtpTransport(host)
    }

    override suspend fun test(settings: SmtpSettingsRecord, secret: CharArray, recipient: String) =
        withContext(Dispatchers.IO) {
            try {
                val session = session(settings, secret)
                session.getTransport("smtp").use { transport ->
                    transport.connect(settings.host, settings.port, settings.username, String(secret))
                    val message = MimeMessage(session).apply {
                        setFrom(InternetAddress(settings.sender, true))
                        setRecipient(Message.RecipientType.TO, InternetAddress(recipient, true))
                        subject = "STORE SMTP test"
                        setText("SMTP configuration test", "UTF-8")
                    }
                    transport.sendMessage(message, message.allRecipients)
                }
            } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
            catch (failure: Exception) { throw classify(failure) }
        }

    override suspend fun send(settings: SmtpSettingsRecord, secret: CharArray, email: EmailRecord) =
        withContext(Dispatchers.IO) {
            try {
                val attachment = email.attachment
                require(attachment != null && attachment.size <= 25_000_000)
                val session = session(settings, secret)
                session.getTransport("smtp").use { transport ->
                    transport.connect(settings.host, settings.port, settings.username, String(secret))
                    val message = MimeMessage(session).apply {
                        setFrom(InternetAddress(settings.sender, true))
                        setRecipient(Message.RecipientType.TO, InternetAddress(email.recipient, true))
                        subject = email.subject
                        val body = MimeBodyPart().apply { setText(email.message, "UTF-8") }
                        val attachmentPart = MimeBodyPart().apply {
                            val source = ByteArrayDataSource(attachment, "application/pdf")
                            dataHandler = DataHandler(source)
                            fileName = email.filename
                        }
                        setContent(MimeMultipart().apply { addBodyPart(body); addBodyPart(attachmentPart) })
                    }
                    transport.sendMessage(message, message.allRecipients)
                }
            } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
            catch (failure: Exception) { throw classify(failure) }
        }

    private fun session(settings: SmtpSettingsRecord, secret: CharArray): Session {
        require(settings.host.matches(Regex("[A-Za-z0-9.-]{1,253}")) && settings.port in 1..65535)
        require(settings.username.length <= 320 && settings.sender.length <= 320)
        val props = Properties().apply {
            setProperty("mail.transport.protocol", "smtp")
            setProperty("mail.smtp.host", settings.host)
            setProperty("mail.smtp.port", settings.port.toString())
            setProperty("mail.smtp.auth", "true")
            setProperty("mail.smtp.connectiontimeout", "10000")
            setProperty("mail.smtp.timeout", "10000")
            setProperty("mail.smtp.writetimeout", "10000")
            setProperty("mail.smtp.ssl.checkserveridentity", "true")
            syntheticTrustHost?.let { setProperty("mail.smtp.ssl.trust", it) }
            if (settings.implicitTls) setProperty("mail.smtp.ssl.enable", "true") else {
                setProperty("mail.smtp.starttls.enable", "true")
                setProperty("mail.smtp.starttls.required", "true")
            }
        }
        return Session.getInstance(props, object : jakarta.mail.Authenticator() {
            override fun getPasswordAuthentication() = PasswordAuthentication(settings.username, String(secret))
        }).apply { debug = false }
    }

    private fun classify(failure: Throwable): SmtpTransportFailure {
        val causes = ArrayDeque<Throwable>()
        val seen = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Throwable, Boolean>())
        causes.add(failure)
        val observed = mutableListOf<Throwable>()
        while (causes.isNotEmpty()) {
            val current = causes.removeFirst()
            if (!seen.add(current)) continue
            observed += current
            current.cause?.let(causes::addLast)
            (current as? jakarta.mail.MessagingException)?.nextException?.let(causes::addLast)
        }
        val code = when {
            observed.any { it is SocketTimeoutException } -> "SMTP_TIMEOUT"
            observed.any { it is SSLException } -> "SMTP_TLS_FAILURE"
            observed.any { it is jakarta.mail.AuthenticationFailedException } -> "SMTP_AUTH_FAILURE"
            observed.any { it is jakarta.mail.SendFailedException } -> "SMTP_REJECTED"
            else -> "SMTP_NETWORK_FAILURE"
        }
        return SmtpTransportFailure(code)
    }
}
