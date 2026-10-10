package com.vibe.store.infrastructure.documents.parser

/** Native boundary intentionally exposes only PDF Subject bytes. */
internal class PdfiumNative {
    external fun extractSubject(readOnlyFd: Int): ByteArray?

    companion object {
        init {
            System.loadLibrary("pdfium")
            System.loadLibrary("store_pdf_subject")
        }
    }
}
