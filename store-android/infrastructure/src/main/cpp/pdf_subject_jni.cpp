#include <jni.h>
#include <algorithm>
#include <fcntl.h>
#include <unistd.h>
#include <cerrno>
#include <cstdint>
#include <cstdlib>
#include <memory>
#include <mutex>

#include "fpdfview.h"
#include "fpdf_doc.h"

namespace {
constexpr size_t kMaxPdfBytes = 25U * 1024U * 1024U;
// UTF-16LE bytes for the canonical 14-char prefix plus the codec's bounded
// 2,666,668 Base64 chars, including PDFium's 2-byte terminator.
constexpr unsigned long kMaxSubjectBytes = 5'333'366U;
std::once_flag g_pdfium_initialized;
std::mutex g_pdfium_mutex;

void InitializePdfium() { FPDF_InitLibrary(); }

struct PdfDocument final {
    explicit PdfDocument(FPDF_DOCUMENT value) : value(value) {}
    ~PdfDocument() { if (value != nullptr) FPDF_CloseDocument(value); }
    FPDF_DOCUMENT value;
};

bool ReadBounded(int fd, uint8_t** bytes, size_t* size) {
    size_t capacity = 64U * 1024U;
    *bytes = static_cast<uint8_t*>(malloc(capacity));
    if (*bytes == nullptr) return false;
    size_t used = 0;
    while (true) {
        if (used == capacity && capacity == kMaxPdfBytes) {
            uint8_t extra = 0;
            ssize_t count;
            do { count = read(fd, &extra, 1); } while (count < 0 && errno == EINTR);
            if (count != 0) return false;
            *size = used;
            return true;
        }
        if (used == capacity && capacity < kMaxPdfBytes) {
            const size_t grown = capacity > kMaxPdfBytes / 2 ? kMaxPdfBytes : capacity * 2;
            auto* replacement = static_cast<uint8_t*>(realloc(*bytes, grown));
            if (replacement == nullptr) return false;
            *bytes = replacement;
            capacity = grown;
        }
        const ssize_t count = read(fd, *bytes + used, capacity - used);
        if (count == 0) { *size = used; return used > 0; }
        if (count < 0) {
            if (errno == EINTR) continue;
            return false;
        }
        used += static_cast<size_t>(count);
    }
}
}  // namespace

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_vibe_store_infrastructure_documents_parser_PdfiumNative_extractSubject(
        JNIEnv* env, jobject /* receiver */, jint fd) {
    const int access = fcntl(fd, F_GETFL);
    if (access < 0 || (access & O_ACCMODE) != O_RDONLY) return nullptr;

    uint8_t* input_raw = nullptr;
    size_t input_size = 0;
    if (!ReadBounded(fd, &input_raw, &input_size)) { free(input_raw); return nullptr; }
    std::unique_ptr<uint8_t, decltype(&free)> input(input_raw, &free);

    std::call_once(g_pdfium_initialized, InitializePdfium);
    std::lock_guard<std::mutex> lock(g_pdfium_mutex);
    // PDFium retains the supplied bytes for the document lifetime. Keep the owner
    // alive until after PdfDocument's destructor closes the native handle.
    PdfDocument document(FPDF_LoadMemDocument64(input.get(), input_size, nullptr));
    if (document.value == nullptr) return nullptr;

    const unsigned long required = FPDF_GetMetaText(document.value, "Subject", nullptr, 0);
    if (required < 2 || required > kMaxSubjectBytes || (required & 1U) != 0U) return nullptr;
    auto* output = static_cast<uint8_t*>(malloc(required));
    if (output == nullptr) return nullptr;
    const unsigned long written = FPDF_GetMetaText(document.value, "Subject", output, required);
    if (written != required || output[required - 1] != 0 || output[required - 2] != 0) {
        free(output);
        return nullptr;
    }

    const jsize payload_size = static_cast<jsize>(required - 2);
    jbyteArray result = env->NewByteArray(payload_size);
    if (result != nullptr && payload_size > 0) {
        env->SetByteArrayRegion(result, 0, payload_size, reinterpret_cast<const jbyte*>(output));
    }
    free(output);
    return result;
}
