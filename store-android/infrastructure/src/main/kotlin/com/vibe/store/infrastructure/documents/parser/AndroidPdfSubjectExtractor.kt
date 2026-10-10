package com.vibe.store.infrastructure.documents.parser

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import com.vibe.store.api.BoundedDocumentInput
import com.vibe.store.api.PdfSubjectExtractionFailure
import com.vibe.store.api.PdfSubjectFailureCode
import com.vibe.store.api.PdfSubjectExtractor
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** SAF input adapter to the UID-isolated Subject service. It has no repository or session handle. */
class AndroidPdfSubjectExtractor(context: Context) : PdfSubjectExtractor {
    private val applicationContext = context.applicationContext

    override suspend fun extract(input: BoundedDocumentInput): ByteArray = suspendCancellableCoroutine { continuation ->
        val completed = AtomicBoolean(false)
        val bindStarted = AtomicBoolean(false)
        val requestId = REQUEST_IDS.incrementAndGet()
        val pipeLock = Any()
        var inputPipe: Array<ParcelFileDescriptor>? = null
        var connection: ServiceConnection? = null

        fun closePipe() {
            val descriptors = synchronized(pipeLock) { inputPipe.also { inputPipe = null } }
            descriptors?.forEach { runCatching { it.close() } }
        }

        fun finish(failure: Throwable? = null, value: ByteArray? = null) {
            if (!completed.compareAndSet(false, true)) return
            closePipe()
            if (bindStarted.get()) connection?.let { target -> runCatching { applicationContext.unbindService(target) } }
            if (!continuation.isActive) return
            if (failure != null) continuation.resumeWithException(failure)
            else continuation.resume(checkNotNull(value))
        }

        val replyMessenger = Messenger(Handler(Looper.getMainLooper()) { message ->
            if (message.arg2 != requestId || message.what != PdfSubjectIsolatedService.RESPONSE_SUBJECT) return@Handler true
            if (message.arg1 != PdfSubjectIsolatedService.RESULT_OK) {
                finish(PdfSubjectExtractionFailure(
                    if (message.arg1 == PdfSubjectIsolatedService.RESULT_UNAVAILABLE) PdfSubjectFailureCode.UNAVAILABLE
                    else PdfSubjectFailureCode.REJECTED,
                ))
                return@Handler true
            }
            val data = message.data
            val descriptor = data.parcelable<ParcelFileDescriptor>(PdfSubjectIsolatedService.KEY_SUBJECT_FD)
            val length = data.getInt(PdfSubjectIsolatedService.KEY_SUBJECT_LENGTH, 0)
            if (descriptor == null || length !in 1..PdfSubjectExtractor.MAX_SUBJECT_BYTES || length % 2 != 0) {
                descriptor?.close()
                finish(PdfSubjectExtractionFailure(PdfSubjectFailureCode.REJECTED))
                return@Handler true
            }
            Thread({
                try {
                    val bytes = ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { stream ->
                        val output = ByteArrayOutputStream(length)
                        val buffer = ByteArray(16 * 1024)
                        while (output.size() < length) {
                            val count = stream.read(buffer, 0, minOf(buffer.size, length - output.size()))
                            if (count < 0) throw IOException("Truncated Subject pipe")
                            output.write(buffer, 0, count)
                        }
                        if (stream.read() != -1) throw IOException("Subject pipe exceeded declared length")
                        output.toByteArray()
                    }
                    finish(value = bytes)
                } catch (_: Exception) {
                    finish(PdfSubjectExtractionFailure(PdfSubjectFailureCode.REJECTED))
                }
            }, "pdf-subject-result-reader").apply { isDaemon = true; start() }
            true
        })

        connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                if (completed.get()) return
                val pipe = try { ParcelFileDescriptor.createPipe() }
                catch (_: Exception) {
                    finish(PdfSubjectExtractionFailure(PdfSubjectFailureCode.UNAVAILABLE)); return
                }
                synchronized(pipeLock) { inputPipe = pipe }
                try {
                    val request = Message.obtain(null, PdfSubjectIsolatedService.REQUEST_SUBJECT, requestId, 0).apply {
                        replyTo = replyMessenger
                        data = Bundle().apply { putParcelable(PdfSubjectIsolatedService.KEY_DESCRIPTOR, pipe[0]) }
                    }
                    Messenger(binder).send(request)
                    // Binder has duplicated the read-only pipe descriptor into the isolated process.
                    pipe[0].close()
                    Thread({
                        try {
                            ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { it.write(input.copyBytes()) }
                        } catch (_: Exception) {
                            runCatching { pipe[1].close() }
                        }
                    }, "pdf-subject-input-writer").apply { isDaemon = true; start() }
                } catch (_: RemoteException) {
                    finish(PdfSubjectExtractionFailure(PdfSubjectFailureCode.UNAVAILABLE))
                }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                finish(PdfSubjectExtractionFailure(PdfSubjectFailureCode.UNAVAILABLE))
            }
        }

        continuation.invokeOnCancellation { finish(PdfSubjectExtractionFailure(PdfSubjectFailureCode.REJECTED)) }
        try {
            bindStarted.set(applicationContext.bindService(
                Intent(applicationContext, PdfSubjectIsolatedService::class.java), checkNotNull(connection), Context.BIND_AUTO_CREATE,
            ))
            if (!bindStarted.get()) finish(PdfSubjectExtractionFailure(PdfSubjectFailureCode.UNAVAILABLE))
        } catch (_: SecurityException) {
            finish(PdfSubjectExtractionFailure(PdfSubjectFailureCode.UNAVAILABLE))
        }
    }

    private inline fun <reified T : android.os.Parcelable> Bundle.parcelable(key: String): T? =
        if (android.os.Build.VERSION.SDK_INT >= 33) getParcelable(key, T::class.java)
        else @Suppress("DEPRECATION") getParcelable(key)

    private companion object {
        val REQUEST_IDS = AtomicInteger(1)
    }
}
