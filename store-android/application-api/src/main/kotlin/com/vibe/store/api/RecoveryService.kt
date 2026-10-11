package com.vibe.store.api

import java.io.InputStream
import java.io.OutputStream

/** SAF-backed opaque streams; callers never provide paths. */
interface RecoverySource { fun openInputStream(): InputStream? }
interface RecoveryTarget { fun openOutputStream(): OutputStream? }
data class RecoveryEntryView(val name: String, val bytes: Long, val sha256: String)
class RecoveryPreview(val appVersion: String, val schema: Int, val createdAt: String,
    entries: List<RecoveryEntryView>, val totalBytes: Long) {
    val entries: List<RecoveryEntryView> = java.util.Collections.unmodifiableList(ArrayList(entries))
}
data class RecoveryReceipt(val filename: String, val bytes: Long, val sha256: String, val externallyVerified: Boolean)
enum class RecoveryError { CANCELLED, FORBIDDEN, INVALID_ARCHIVE, UNSUPPORTED_VERSION, INSUFFICIENT_SPACE, STORAGE_UNAVAILABLE, CONFIRMATION_REQUIRED }
class RecoveryFailure(val code: RecoveryError) : Exception(code.name)

interface RecoveryService {
    suspend fun createBackup(target: RecoveryTarget): RecoveryReceipt
    /** Internal scheduled snapshot; returns null when no active Owner remains. */
    suspend fun createAutomaticBackup(): RecoveryReceipt? = null
    suspend fun inspect(source: RecoverySource): RecoveryPreview
    suspend fun restore(source: RecoverySource, confirmed: Boolean)
    suspend fun reset(password: String, confirmed: Boolean)
}
