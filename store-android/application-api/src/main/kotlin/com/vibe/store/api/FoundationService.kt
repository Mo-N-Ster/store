package com.vibe.store.api
data class FoundationSnapshot(val milestone: String, val storageAdapter: String)
fun interface FoundationService { fun inspect(): FoundationSnapshot }
