package com.vibe.store.infrastructure.media

import android.content.Context
import android.net.Uri
import android.system.Os
import android.system.OsConstants
import com.vibe.store.api.*
import com.vibe.store.application.catalog.*
import com.vibe.store.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.UUID

/** An opaque, short-lived SAF selection. Never persist the external URI. */
private class ContentSelection(val uri: Uri) : SelectedImage

class AndroidProductMedia private constructor(
    private val root: File,
    private val open: (Uri) -> InputStream?,
    private val beforeWrite: (Int) -> Unit = {},
) : ProductMedia {
    companion object {
        fun create(context: Context) = AndroidProductMedia(
            File(context.noBackupFilesDir, "generations/main/media/articles"),
            { context.contentResolver.openInputStream(it) },
        )
        fun selection(uri: Uri): SelectedImage {
            if (uri.scheme != "content") throw CatalogFailure(CatalogError.INVALID_MEDIA)
            return ContentSelection(uri)
        }
        internal fun fixture(root: File, beforeWrite: (Int) -> Unit = {}, open: (Uri) -> InputStream?) = AndroidProductMedia(root, open, beforeWrite)
    }

    private fun directory(): File {
        fun ensure(path: File) {
            if (path.exists()) return
            val parent = path.parentFile ?: throw CatalogFailure(CatalogError.STORAGE_UNAVAILABLE)
            ensure(parent)
            if (!path.mkdir() && !path.isDirectory) throw CatalogFailure(CatalogError.STORAGE_UNAVAILABLE)
            val fd = Os.open(parent.path, OsConstants.O_RDONLY, 0)
            try { Os.fsync(fd) } finally { Os.close(fd) }
        }
        ensure(root)
        if (!root.isDirectory) throw CatalogFailure(CatalogError.STORAGE_UNAVAILABLE)
        return root
    }

    private fun file(reference: String): File {
        if (!MediaSignature.validReference(reference)) throw CatalogFailure(CatalogError.INVALID_MEDIA)
        val candidate = File(directory(), reference)
        if (candidate.canonicalFile.parentFile != directory().canonicalFile) throw CatalogFailure(CatalogError.INVALID_MEDIA)
        return candidate
    }

    private fun syncDirectory() {
        val fd = Os.open(directory().path, OsConstants.O_RDONLY, 0)
        try { Os.fsync(fd) } finally { Os.close(fd) }
    }

    private inline fun <T> storage(block: () -> T): T = try { block() }
    catch (failure: CatalogFailure) { throw failure }
    catch (_: SecurityException) { throw CatalogFailure(CatalogError.MEDIA_UNAVAILABLE) }
    catch (_: IOException) { throw CatalogFailure(CatalogError.STORAGE_UNAVAILABLE) }
    catch (_: android.system.ErrnoException) { throw CatalogFailure(CatalogError.STORAGE_UNAVAILABLE) }

    override suspend fun prepare(selection: SelectedImage?, policy: MediaPolicy): PreparedMedia? = withContext(Dispatchers.IO) {
        if (selection == null) return@withContext null
        val source = selection as? ContentSelection ?: throw CatalogFailure(CatalogError.INVALID_MEDIA)
        storage {
            val id = UUID.randomUUID().toString()
            val temporary = File(directory(), "$id.part")
            // Exclusive creation avoids following any pre-existing file or link.
            if (!temporary.createNewFile()) throw CatalogFailure(CatalogError.STORAGE_UNAVAILABLE)
            var total = 0
            val prefix = ByteArray(12)
            var prefixSize = 0
            val input = try { open(source.uri) } catch (_: SecurityException) {
                throw CatalogFailure(CatalogError.MEDIA_UNAVAILABLE)
            } ?: throw CatalogFailure(CatalogError.MEDIA_UNAVAILABLE)
            input.use {
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = it.read(buffer)
                        if (count == -1) break
                        if (count == 0) continue
                        if (count > policy.maximumBytes - total) throw CatalogFailure(CatalogError.MEDIA_TOO_LARGE)
                        val copy = minOf(count, prefix.size - prefixSize)
                        if (copy > 0) { buffer.copyInto(prefix, prefixSize, 0, copy); prefixSize += copy }
                        beforeWrite(count)
                        output.write(buffer, 0, count)
                        total += count
                    }
                    output.fd.sync()
                }
            }
            val kind = MediaSignature.detect(prefix.copyOf(prefixSize)) ?: throw CatalogFailure(CatalogError.INVALID_MEDIA)
            val reference = "$id.${kind.extension}"
            val destination = file(reference)
            if (destination.exists() || !temporary.renameTo(destination)) throw CatalogFailure(CatalogError.STORAGE_UNAVAILABLE)
            syncDirectory()
            // A failed preparation retains only private unreferenced files; no
            // database reference is returned until the durable copy is verified.
            readFile(reference, policy.maximumBytes)
            PreparedMedia(reference)
        }
    }

    private fun readFile(reference: String, maximum: Int): ProductImage {
        val source = file(reference)
        if (!source.isFile) throw CatalogFailure(CatalogError.MEDIA_UNAVAILABLE)
        val bytes = source.inputStream().use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count == -1) break
                if (count > maximum - output.size()) throw CatalogFailure(CatalogError.MEDIA_TOO_LARGE)
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        val kind = MediaSignature.detect(bytes) ?: throw CatalogFailure(CatalogError.INVALID_MEDIA)
        if (!reference.endsWith(".${kind.extension}")) throw CatalogFailure(CatalogError.INVALID_MEDIA)
        return ProductImage(kind.mimeType, bytes)
    }

    override suspend fun read(reference: String): ProductImage = withContext(Dispatchers.IO) {
        storage { readFile(reference, MediaPolicy.PRODUCT.maximumBytes) }
    }

    override suspend fun removeUnreferenced(reference: String, references: Set<String>) = withContext(Dispatchers.IO) {
        storage {
            if (reference !in references) {
                val target = file(reference)
                if (target.exists() && !target.delete()) throw CatalogFailure(CatalogError.STORAGE_UNAVAILABLE)
                syncDirectory()
            }
        }
    }

    override suspend fun inspect(references: Set<String>): MediaInspection = withContext(Dispatchers.IO) {
        storage {
            val missing = references.count { reference ->
                try { readFile(reference, MediaPolicy.PRODUCT.maximumBytes); false }
                catch (failure: CatalogFailure) {
                    if (failure.code == CatalogError.STORAGE_UNAVAILABLE) throw failure
                    true
                }
            }
            val files = directory().listFiles() ?: throw CatalogFailure(CatalogError.STORAGE_UNAVAILABLE)
            MediaInspection(missing, files.count { it.name !in references })
        }
    }
}
