package com.vibe.store.infrastructure.documents.parser

import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Debug
import android.os.Handler
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.Process
import android.os.RemoteException
import android.os.SystemClock
import android.os.ParcelFileDescriptor
import android.util.Log
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.Executors

/**
 * Untrusted PDF metadata parser in an Android isolated UID/process. The only request value is a
 * read-only descriptor; no path, app authority, database, or business object crosses this boundary.
 * Import remains intentionally uncomposed until device security/resource qualification passes.
 */
class PdfSubjectIsolatedService : Service() {
    private lateinit var messenger: Messenger
    private val parserExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "pdf-subject-native").apply { isDaemon = true }
    }
    private val busy = AtomicBoolean(false)
    private val nativeParser by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { PdfiumNative() }

    override fun onCreate() {
        super.onCreate()
        messenger = Messenger(Handler(mainLooper, ::handle))
    }

    override fun onBind(intent: Intent): IBinder = messenger.binder

    private fun handle(message: Message): Boolean {
        val reply = message.replyTo
        if (reply == null) {
            message.data?.getParcelableCompat<ParcelFileDescriptor>(KEY_DESCRIPTOR)?.close()
            return true
        }
        if (message.what != REQUEST_SUBJECT || !busy.compareAndSet(false, true)) {
            message.data?.getParcelableCompat<ParcelFileDescriptor>(KEY_DESCRIPTOR)?.close()
            reply.sendResult(message.arg1, RESULT_REJECTED)
            return true
        }
        val descriptor = message.data?.getParcelableCompat<ParcelFileDescriptor>(KEY_DESCRIPTOR)
        if (descriptor == null) {
            busy.set(false)
            reply.sendResult(message.arg1, RESULT_REJECTED)
            return true
        }
        // Binder recycles the incoming Message as soon as the Handler returns; never
        // capture it in the asynchronous parser task.
        val requestId = message.arg1

        parserExecutor.execute {
            val started = SystemClock.elapsedRealtime()
            val peakPssKb = AtomicLong(0)
            val peakNativeHeapBytes = AtomicLong(0)
            peakPssKb.set(Debug.getPss().toLong())
            peakNativeHeapBytes.set(Debug.getNativeHeapAllocatedSize())
            val watchdog = Thread {
                while (busy.get()) {
                    val pss = Debug.getPss().toLong()
                    updateMaximum(peakPssKb, pss)
                    updateMaximum(peakNativeHeapBytes, Debug.getNativeHeapAllocatedSize())
                    if (pss >= MAX_PARSER_PSS_KB) {
                        // A PSS over-budget parser is disposable; kill only this isolated process.
                        Process.killProcess(Process.myPid())
                        return@Thread
                    }
                    if (SystemClock.elapsedRealtime() - started >= PARSE_TIMEOUT_MS) {
                        // Native PDFium calls are not cooperatively cancellable. Terminating this
                        // isolated process is the cancellation boundary; the caller observes Binder death.
                        Process.killProcess(Process.myPid())
                        return@Thread
                    }
                    try { Thread.sleep(WATCHDOG_INTERVAL_MS) } catch (_: InterruptedException) { return@Thread }
                }
            }.apply { name = "pdf-subject-watchdog"; isDaemon = true; start() }

            try {
                if (Build.VERSION.SDK_INT >= 29 && !Process.isIsolated()) {
                    reply.sendResult(requestId, RESULT_REJECTED)
                    return@execute
                }
                val subject = descriptor.use {
                    val fd = it.detachFd()
                    try { nativeParser.extractSubject(fd) }
                    finally { ParcelFileDescriptor.adoptFd(fd).close() }
                }
                if (subject == null || subject.isEmpty() || subject.size > MAX_SUBJECT_BYTES) {
                    reply.sendResult(requestId, RESULT_REJECTED)
                } else {
                    reply.sendSubject(requestId, subject)
                }
            } catch (_: UnsatisfiedLinkError) {
                reply.sendResult(requestId, RESULT_UNAVAILABLE)
            } catch (_: RemoteException) {
                // Client disappeared; no import/result state exists in this process.
            } catch (_: Exception) {
                reply.sendResult(requestId, RESULT_REJECTED)
            } finally {
                // Debug metrics contain no document data and are retained only in debug builds.
                if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                    Log.i(QUALIFICATION_TAG, "peakPssKb=${peakPssKb.get()} peakNativeHeapBytes=${peakNativeHeapBytes.get()} elapsedMs=${SystemClock.elapsedRealtime() - started}")
                }
                busy.set(false)
                watchdog.interrupt()
            }
        }
        return true
    }

    override fun onDestroy() {
        parserExecutor.shutdownNow()
        super.onDestroy()
    }

    /** AtomicLong.updateAndGet is unavailable on the minimum supported Android API (23). */
    private fun updateMaximum(target: AtomicLong, candidate: Long) {
        while (true) {
            val current = target.get()
            if (candidate <= current || target.compareAndSet(current, candidate)) return
        }
    }

    private fun Messenger.sendResult(id: Int, result: Int, bundle: Bundle = Bundle()) {
        val response = Message.obtain(null, RESPONSE_SUBJECT, result, id).apply { data = bundle }
        try { send(response) } catch (_: RemoteException) { }
    }

    /** Large codec payloads travel over a bounded pipe, never a Binder byte array. */
    private fun Messenger.sendSubject(id: Int, subject: ByteArray) {
        val pipe = ParcelFileDescriptor.createPipe()
        val response = Message.obtain(null, RESPONSE_SUBJECT, RESULT_OK, id).apply {
            data = Bundle().apply {
                putParcelable(KEY_SUBJECT_FD, pipe[0])
                putInt(KEY_SUBJECT_LENGTH, subject.size)
            }
        }
        try {
            send(response)
            pipe[0].close()
            ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { output ->
                output.write(subject)
                output.flush()
            }
        } catch (_: Exception) {
            runCatching { pipe[0].close() }
            runCatching { pipe[1].close() }
        }
    }

    private inline fun <reified T : android.os.Parcelable> Bundle.getParcelableCompat(key: String): T? =
        if (Build.VERSION.SDK_INT >= 33) getParcelable(key, T::class.java) else @Suppress("DEPRECATION") getParcelable(key)

    companion object {
        const val REQUEST_SUBJECT = 1
        const val RESPONSE_SUBJECT = 2
        const val KEY_DESCRIPTOR = "pdf_subject.read_only_fd"
        const val KEY_SUBJECT_FD = "pdf_subject.utf16le_fd"
        const val KEY_SUBJECT_LENGTH = "pdf_subject.length"
        const val RESULT_OK = 0
        const val RESULT_REJECTED = 1
        const val RESULT_UNAVAILABLE = 2
        // UTF-16LE payload (terminator excluded) for STORE_DATA_V1's current codec bound.
        const val MAX_SUBJECT_BYTES = 5_333_364
        const val MAX_PDF_BYTES = 25 * 1024 * 1024
        const val PARSE_TIMEOUT_MS = 5_000L
        // Technical per-isolated-process budget; this is not a business/document rule.
        const val MAX_PARSER_PSS_KB = 256 * 1024
        const val QUALIFICATION_TAG = "PdfSubjectQual"
        private const val WATCHDOG_INTERVAL_MS = 25L
    }
}
