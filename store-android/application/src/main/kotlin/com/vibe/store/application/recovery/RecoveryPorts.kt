package com.vibe.store.application.recovery

import com.vibe.store.api.RecoveryPreview
import java.io.InputStream

interface RecoveryArtifact {
    val byteCount: Long
    val sha256: String
    fun openInputStream(): InputStream
}

interface RecoveryBackend {
    suspend fun createVerifiedBackup(): RecoveryArtifact
    suspend fun createAutomaticBackup(): RecoveryArtifact
    suspend fun inspect(input: InputStream, actorId: Long): RecoveryPreview
    suspend fun restore(input: InputStream, actorId: Long)
    suspend fun reset(actorId: Long)
}
