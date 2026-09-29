package com.vibe.store.application
import com.vibe.store.api.FoundationService
import com.vibe.store.api.FoundationSnapshot
import com.vibe.store.domain.FoundationStage

/** Narrow I01 wiring probe. Never opens data or grants authority. */
fun interface FoundationEnvironment { fun storageAdapterName(): String }
class FoundationServiceImpl(private val environment: FoundationEnvironment) : FoundationService {
    override fun inspect() = FoundationSnapshot(FoundationStage.NATIVE_FOUNDATION.name, environment.storageAdapterName())
}
