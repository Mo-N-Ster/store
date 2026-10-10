package com.vibe.store.application.catalog

import com.vibe.store.domain.CatalogImportPreview
import com.vibe.store.domain.StructuredCatalogPayload

/** Pure payload codec port. Implementations receive only bounded Subject text, never PDF/DB/session. */
interface StructuredCatalogCodec {
    fun encode(payload: StructuredCatalogPayload): String
    fun preview(subject: String): CatalogImportPreview
}
