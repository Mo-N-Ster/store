package com.vibe.store.infrastructure.delivery

import com.vibe.store.application.delivery.SmtpSettingsRecord
import com.vibe.store.application.delivery.SmtpTransportFailure
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket

class AngusSmtpTransportTest {
    @Test fun implicitTlsControlledServerAcceptsSyntheticDelivery() = runBlocking {
        val context = syntheticServerTlsContext()
        val listener = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).apply { soTimeout = 8_000 }
        val received = AtomicReference<String>()
        val authenticated = java.util.concurrent.atomic.AtomicBoolean(false)
        val serverFailure = AtomicReference<Throwable?>()
        val peer = Thread {
            try {
                listener.accept().use { plain ->
                    val tls = context.socketFactory.createSocket(plain, "127.0.0.1", listener.localPort, true) as SSLSocket
                    tls.use {
                        it.useClientMode = false
                        it.startHandshake()
                        serveAuthenticatedMail(it, received, authenticated)
                    }
                }
            } catch (failure: Throwable) { serverFailure.set(failure) }
        }.apply { isDaemon = true; start() }

        try {
            AngusSmtpTransport.forSyntheticServer("127.0.0.1").send(
                SmtpSettingsRecord("127.0.0.1", listener.localPort, "synthetic-user", "store@example.invalid", true, null),
                "synthetic-secret".toCharArray(),
                com.vibe.store.application.delivery.EmailRecord(
                    1, 10, "synthetic-generation", "recipient@example.invalid", "Synthetic report",
                    "report.pdf", "Synthetic body", byteArrayOf(1, 2, 3), "sending", 0,
                    java.time.Instant.EPOCH, null, null, null,
                ),
            )
        } finally { listener.close(); peer.join(8_000) }

        assertTrue("Controlled TLS SMTP server must receive the expected subject", received.get().contains("Subject: Synthetic report"))
        assertTrue("Controlled TLS SMTP server must receive the PDF attachment", received.get().contains("filename=report.pdf"))
        assertTrue("Controlled TLS SMTP server must receive the synthetic body", received.get().contains("Synthetic body"))
        assertTrue("SMTP authentication must complete inside the TLS session", authenticated.get())
        assertEquals(null, serverFailure.get())
    }

    @Test fun untrustedCertificateFailsBeforeAuthenticationOrMessageDelivery() = runBlocking {
        val context = syntheticServerTlsContext()
        val listener = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).apply { soTimeout = 5_000 }
        val acceptedMessage = AtomicReference<String>()
        val peer = Thread {
            runCatching {
                listener.accept().use { plain ->
                    (context.socketFactory.createSocket(plain, "127.0.0.1", listener.localPort, true) as SSLSocket).use {
                        it.useClientMode = false
                        it.startHandshake()
                        serveAuthenticatedMail(it, acceptedMessage, java.util.concurrent.atomic.AtomicBoolean(false))
                    }
                }
            }
        }.apply { isDaemon = true; start() }
        var failure: SmtpTransportFailure? = null
        try {
            AngusSmtpTransport().send(
                SmtpSettingsRecord("127.0.0.1", listener.localPort, "synthetic-user", "store@example.invalid", true, null),
                "synthetic-secret".toCharArray(),
                com.vibe.store.application.delivery.EmailRecord(2, 10, "generation", "recipient@example.invalid",
                    "Rejected TLS", "report.pdf", "body", byteArrayOf(1), "sending", 0,
                    java.time.Instant.EPOCH, null, null, null),
            )
        } catch (caught: SmtpTransportFailure) { failure = caught }
        finally { listener.close(); peer.join(5_000) }
        assertTrue("Untrusted certificate must fail closed", failure != null)
        assertEquals("SMTP_TLS_FAILURE", failure?.code)
        assertEquals("No message may be accepted over an untrusted TLS session", null, acceptedMessage.get())
    }

    @Test fun stalledSmtpGreetingTimesOutWithoutSendingCredentials() = runBlocking {
        val listener = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).apply { soTimeout = 15_000 }
        val peer = Thread {
            runCatching { listener.accept().use { it.soTimeout = 12_000; Thread.sleep(11_000) } }
        }.apply { isDaemon = true; start() }
        var failure: SmtpTransportFailure? = null
        val secret = "synthetic-never-send".toCharArray()
        try {
            AngusSmtpTransport().test(
                SmtpSettingsRecord("127.0.0.1", listener.localPort, "synthetic-user", "store@example.invalid", true, null),
                secret,
                "recipient@example.invalid",
            )
        } catch (caught: SmtpTransportFailure) { failure = caught }
        finally { secret.fill('\u0000'); listener.close(); peer.join(12_000) }
        assertTrue("The configured SMTP read timeout must fail the stalled server", failure != null)
        assertEquals("SMTP_TIMEOUT", failure?.code)
    }

    @Test fun requiredStartTlsRefusesServerBeforeSendingAuthentication() = runBlocking {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        server.soTimeout = 5_000
        val transcript = CopyOnWriteArrayList<String>()
        val serverFailure = AtomicReference<Throwable?>()
        val peer = Thread {
            try {
                server.accept().use { socket -> serveWithoutStartTls(socket, transcript) }
            } catch (failure: Throwable) { serverFailure.set(failure) }
        }.apply { isDaemon = true; start() }
        val secret = "synthetic-never-send".toCharArray()
        var failure: SmtpTransportFailure? = null
        try {
            AngusSmtpTransport().test(
                SmtpSettingsRecord("127.0.0.1", server.localPort, "synthetic-user", "store@example.invalid", false, null),
                secret,
                "synthetic-recipient@example.invalid",
            )
        } catch (caught: SmtpTransportFailure) { failure = caught }
        finally { secret.fill('\u0000'); server.close(); peer.join(5_000) }

        assertTrue("STARTTLS downgrade must fail closed", failure != null)
        assertFalse("No AUTH command may precede required TLS: $transcript", transcript.any { it.startsWith("AUTH", true) })
        assertFalse("Secret must never appear on plaintext SMTP: $transcript", transcript.any { it.contains("synthetic-never-send") })
        assertTrue("The server received the client greeting", transcript.any { it.startsWith("EHLO", true) })
        assertTrue("Test server must finish cleanly: ${serverFailure.get()}", serverFailure.get() == null || serverFailure.get() is java.net.SocketException)
    }

    private fun serveWithoutStartTls(socket: Socket, transcript: MutableList<String>) {
        socket.soTimeout = 5_000
        val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII))
        val writer = OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII)
        fun reply(value: String) { writer.write(value); writer.flush() }
        reply("220 synthetic ESMTP ready\r\n")
        while (true) {
            val line = reader.readLine() ?: return
            transcript += line
            when {
                line.startsWith("EHLO", true) -> reply("250-synthetic\r\n250 AUTH PLAIN LOGIN\r\n")
                line.startsWith("QUIT", true) -> { reply("221 closing\r\n"); return }
                else -> reply("500 unsupported\r\n")
            }
        }
    }

    private fun syntheticServerTlsContext(): SSLContext = SSLContext.getInstance("TLS").apply {
        val store = KeyStore.getInstance("PKCS12")
        AngusSmtpTransportTest::class.java.getResourceAsStream("/delivery/synthetic-smtp.p12")!!.use {
            store.load(it, "changeit".toCharArray())
        }
        val keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        keys.init(store, "changeit".toCharArray())
        init(keys.keyManagers, null, null)
    }

    private fun serveAuthenticatedMail(socket: Socket, received: AtomicReference<String>, authenticated: java.util.concurrent.atomic.AtomicBoolean) {
        socket.soTimeout = 8_000
        val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII))
        val writer = OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII)
        fun reply(value: String) { writer.write(value); writer.flush() }
        reply("220 synthetic TLS ESMTP ready\r\n")
        var authStep = 0
        while (true) {
            val line = reader.readLine() ?: return
            when {
                line.startsWith("EHLO", true) -> reply("250-synthetic\r\n250 AUTH PLAIN LOGIN\r\n")
                line.startsWith("AUTH PLAIN", true) -> { authenticated.set(true); reply("235 authenticated\r\n") }
                line.equals("AUTH LOGIN", true) -> { authStep = 1; reply("334 VXNlcm5hbWU6\r\n") }
                authStep == 1 -> { authStep = 2; reply("334 UGFzc3dvcmQ6\r\n") }
                authStep == 2 -> { authStep = 0; authenticated.set(true); reply("235 authenticated\r\n") }
                line.startsWith("MAIL FROM", true) -> reply(if (authenticated.get()) "250 sender accepted\r\n" else "530 authenticate first\r\n")
                line.startsWith("RCPT TO", true) -> reply("250 recipient accepted\r\n")
                line.equals("DATA", true) -> {
                    reply("354 finish with dot\r\n")
                    val message = ByteArrayOutputStream()
                    while (true) {
                        val data = reader.readLine() ?: error("SMTP DATA ended unexpectedly")
                        if (data == ".") break
                        message.write(data.toByteArray(StandardCharsets.UTF_8))
                        message.write('\n'.code)
                    }
                    received.set(String(message.toByteArray(), StandardCharsets.UTF_8))
                    reply("250 queued\r\n")
                }
                line.equals("QUIT", true) -> { reply("221 closing\r\n"); return }
                else -> reply("250 ok\r\n")
            }
        }
    }
}
