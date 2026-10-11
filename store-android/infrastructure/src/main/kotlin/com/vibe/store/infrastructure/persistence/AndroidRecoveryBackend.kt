package com.vibe.store.infrastructure.persistence

import com.vibe.store.application.recovery.RecoveryArtifact
import com.vibe.store.application.recovery.RecoveryBackend
import java.io.File
import java.io.InputStream

internal class AndroidRecoveryBackend(private val owner: ActiveStoreOwner) : RecoveryBackend {
    override suspend fun createVerifiedBackup(): RecoveryArtifact = Artifact(owner.createBackup())
    override suspend fun createAutomaticBackup(): RecoveryArtifact = Artifact(owner.createBackup(automatic = true))
    override suspend fun inspect(input: InputStream, actorId: Long) = owner.inspectBackup(input, actorId)
    override suspend fun restore(input: InputStream, actorId: Long) { owner.restoreBackup(input, actorId) }
    override suspend fun reset(actorId: Long) { owner.resetStore(actorId) }

    private inner class Artifact(private val file: File) : RecoveryArtifact {
        override val byteCount: Long get() = file.length()
        override val sha256: String by lazy { fileSha256(file) }
        override fun openInputStream(): InputStream = file.inputStream()
    }
}
