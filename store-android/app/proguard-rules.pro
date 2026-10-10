# No business reflection or custom keep rules in I01.
# PDFium resolves this exact JNI symbol from the native library at runtime.
-keep class com.vibe.store.infrastructure.documents.parser.PdfiumNative {
    native <methods>;
}
