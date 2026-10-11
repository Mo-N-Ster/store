package com.vibe.store.application.recovery

import com.vibe.store.api.*
import com.vibe.store.application.catalog.CatalogAuthority
import com.vibe.store.application.security.IdentityAuthority
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.security.MessageDigest

class RecoveryAuthority(
    private val identity: IdentityAuthority,
    private val catalog: CatalogAuthority,
    private val backend: RecoveryBackend,
) : RecoveryService {
    override suspend fun createAutomaticBackup(): RecoveryReceipt? = identity.persistedOwnerMaintenance { actorId ->
        // Match the global lock order: identity gate -> catalog/media barrier -> single Room owner.
        catalog.withMaintenanceBarrier {
            val artifact = try { backend.createAutomaticBackup() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { throw RecoveryFailure(RecoveryError.STORAGE_UNAVAILABLE) }
            try {
                identity.appendMaintenanceAudit(actorId, "recovery_automatic_backup_created", "backup", artifact.sha256)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { throw RecoveryFailure(RecoveryError.STORAGE_UNAVAILABLE) }
            RecoveryReceipt("automatic.storebackup", artifact.byteCount, artifact.sha256, externallyVerified = false)
        }
    }

    override suspend fun createBackup(target: RecoveryTarget): RecoveryReceipt = identity.ownerMaintenance { actorId ->
        catalog.withMaintenanceBarrier {
            val artifact = try { backend.createVerifiedBackup() } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { throw RecoveryFailure(RecoveryError.STORAGE_UNAVAILABLE) }
            val output = try { target.openOutputStream() } catch (_: SecurityException) { null }
                ?: throw RecoveryFailure(RecoveryError.STORAGE_UNAVAILABLE)
            var count = 0L
            val digest = MessageDigest.getInstance("SHA-256")
            try {
                withContext(Dispatchers.IO) {
                    artifact.openInputStream().use { input -> output.use { out ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            count += read
                            if (count > artifact.byteCount || count > 512L * 1024 * 1024) throw IOException("Backup copy bound exceeded")
                            digest.update(buffer, 0, read); out.write(buffer, 0, read)
                        }
                        out.flush()
                    } }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { throw RecoveryFailure(RecoveryError.STORAGE_UNAVAILABLE) }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            if (count != artifact.byteCount || hash != artifact.sha256) throw RecoveryFailure(RecoveryError.STORAGE_UNAVAILABLE)
            identity.appendMaintenanceAudit(actorId, "recovery_backup_exported", "backup", hash)
            RecoveryReceipt("store-backup.storebackup", count, hash, externallyVerified = false)
        }
    }

    override suspend fun inspect(source: RecoverySource): RecoveryPreview = identity.ownerMaintenance { actorId ->
        catalog.withMaintenanceBarrier {
            val input = try { source.openInputStream() } catch (_: SecurityException) { null }
                ?: throw RecoveryFailure(RecoveryError.STORAGE_UNAVAILABLE)
            try { withContext(Dispatchers.IO) { input.use { stream -> backend.inspect(stream, actorId) } } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { throw RecoveryFailure(RecoveryError.INVALID_ARCHIVE) }
        }
    }

    override suspend fun restore(source: RecoverySource, confirmed: Boolean) {
        if (!confirmed) throw RecoveryFailure(RecoveryError.CONFIRMATION_REQUIRED)
        identity.ownerMaintenance(invalidateSession = true) { actorId ->
            catalog.withMaintenanceBarrier {
                val input = try { source.openInputStream() } catch (_: SecurityException) { null }
                    ?: throw RecoveryFailure(RecoveryError.STORAGE_UNAVAILABLE)
                try { withContext(Dispatchers.IO) { backend.restore(input, actorId) } }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: RecoveryFailure) { throw failure }
                catch (_: Exception) { throw RecoveryFailure(RecoveryError.INVALID_ARCHIVE) }
            }
        }
    }

    override suspend fun reset(password: String, confirmed: Boolean) {
        if (!confirmed) throw RecoveryFailure(RecoveryError.CONFIRMATION_REQUIRED)
        identity.ownerPasswordMaintenance(password) { actorId ->
            catalog.withMaintenanceBarrier { backend.reset(actorId) }
        }
    }
}
