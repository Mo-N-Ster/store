package com.vibe.store.infrastructure.persistence

import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Bounded streaming archive primitives. Hashes detect corruption, not malicious re-signing. */
internal object RecoveryArchive {
    const val MAX_ARCHIVE_BYTES = 512L * 1024 * 1024
    const val MAX_ENTRIES = 10_002
    private const val MAX_MANIFEST_BYTES = 2 * 1024 * 1024
    private const val READ_DEADLINE_MILLIS = 120_000L
    private const val HEADER = "STORE_BACKUP_V1"

    data class Source(val name: String, val file: File)
    data class Entry(val name: String, val size: Long, val sha256: String)
    data class Manifest(val schema: Int, val createdAt: String, val entries: List<Entry>)

    fun write(output: OutputStream, schema: Int, createdAt: String, sources: List<Source>) {
        require(schema in 1..3 && createdAt.matches(Regex("[0-9TZ:+.-]{1,40}")))
        val normalized = sources.sortedBy(Source::name)
        require(normalized.size in 1 until MAX_ENTRIES && normalized.map(Source::name).toSet().size == normalized.size)
        require(normalized.any { it.name == "database/store.db" })
        val entries = normalized.map { source ->
            requireName(source.name)
            require(source.file.isFile)
            Entry(source.name, source.file.length(), digest(source.file))
        }
        requireDeclaredBounds(entries)
        val manifest = Manifest(schema, createdAt, entries)
        val bytes = encode(manifest)
        require(bytes.size <= MAX_MANIFEST_BYTES)
        var written = 0L
        ZipOutputStream(output.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.txt"))
            zip.write(bytes); zip.closeEntry()
            for (source in normalized) {
                val expected = entries.single { it.name == source.name }
                check(source.file.length() == expected.size && digest(source.file) == expected.sha256) { "Source changed during archive creation" }
                zip.putNextEntry(ZipEntry(source.name))
                source.file.inputStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        written += count
                        require(written <= MAX_ARCHIVE_BYTES) { "Archive data limit exceeded" }
                        zip.write(buffer, 0, count)
                    }
                }
                zip.closeEntry()
            }
            zip.finish()
        }
    }

    /** Extracts only manifest-listed database/media files into a new empty staging directory. */
    fun read(input: InputStream, staging: File): Manifest {
        require(!staging.exists() || staging.isDirectory && staging.listFiles().isNullOrEmpty())
        require(staging.isDirectory || staging.mkdirs())
        val deadline = System.nanoTime() + READ_DEADLINE_MILLIS * 1_000_000L
        val bounded = object : FilterInputStream(input) {
            var count = 0L
            override fun read(): Int = super.read().also { if (it >= 0) add(1) }
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int = super.read(buffer, offset, length).also { if (it > 0) add(it) }
            private fun add(amount: Int) { count += amount; require(count <= MAX_ARCHIVE_BYTES) { "Archive input exceeds limit" } }
        }
        try { ZipInputStream(bounded.buffered()).use { zip ->
            val first = zip.nextEntry ?: error("Empty archive")
            require(first.name == "manifest.txt" && !first.isDirectory)
            val manifestBytes = zip.readBounded(MAX_MANIFEST_BYTES)
            val manifest = decode(manifestBytes)
            val expected = manifest.entries.associateBy(Entry::name)
            require(expected.size == manifest.entries.size && expected.size < MAX_ENTRIES)
            var total = 0L
            val observed = HashSet<String>()
            while (true) {
                val entry = zip.nextEntry ?: break
                require(!entry.isDirectory && entry.name != "manifest.txt")
                requireName(entry.name)
                val spec = expected[entry.name] ?: error("Unexpected archive entry")
                require(observed.add(entry.name)) { "Duplicate archive entry" }
                val target = File(staging, entry.name)
                require(target.canonicalPath.startsWith(staging.canonicalPath + File.separator))
                require(target.parentFile?.isDirectory == true || target.parentFile?.mkdirs() == true)
                val digest = MessageDigest.getInstance("SHA-256")
                var size = 0L
                target.outputStream().buffered().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        require(System.nanoTime() <= deadline) { "Archive processing deadline exceeded" }
                        val count = zip.read(buffer)
                        if (count < 0) break
                        size += count; total += count
                        require(size <= spec.size && total <= MAX_ARCHIVE_BYTES) { "Expanded archive exceeds declared bounds" }
                        digest.update(buffer, 0, count); out.write(buffer, 0, count)
                    }
                }
                val hash = digest.digest().hex()
                require(size == spec.size && hash == spec.sha256) { "Archive content verification failed" }
                zip.closeEntry()
            }
            require(observed == expected.keys) { "Archive is incomplete" }
            require("database/store.db" in observed)
            return manifest
        } } catch (failure: Throwable) {
            staging.deleteRecursively()
            throw failure
        }
    }

    private fun encode(manifest: Manifest): ByteArray = buildString {
        append(HEADER).append("\napp=3.0\nschema=").append(manifest.schema).append('\n')
        append("created=").append(manifest.createdAt).append('\n')
        for (entry in manifest.entries) append("entry=")
            .append(entry.name.toByteArray(Charsets.UTF_8).joinToString("") { "%02x".format(it) })
            .append('|').append(entry.size).append('|').append(entry.sha256).append('\n')
    }.toByteArray(Charsets.UTF_8)

    private fun decode(bytes: ByteArray): Manifest {
        val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val lines = decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString().split('\n')
        require(lines.size >= 5 && lines[0] == HEADER && lines[1] == "app=3.0" && lines.last().isEmpty())
        val schema = lines[2].removePrefix("schema=").also { require(lines[2].startsWith("schema=")) }.toInt()
        val created = lines[3].removePrefix("created=").also { require(lines[3].startsWith("created=")) }
        require(created.matches(Regex("[0-9TZ:+.-]{1,40}")))
        val entries = lines.drop(4).dropLast(1).map { line ->
            require(line.startsWith("entry="))
            val fields = line.removePrefix("entry=").split('|')
            require(fields.size == 3)
            require(fields[0].length % 2 == 0 && fields[0].matches(Regex("[0-9a-f]*")))
            val nameBytes = ByteArray(fields[0].length / 2) { fields[0].substring(it * 2, it * 2 + 2).toInt(16).toByte() }
            val name = nameBytes.toString(Charsets.UTF_8)
            requireName(name)
            val size = fields[1].toLong().also { require(it >= 0) }
            val hash = fields[2].also { require(it.matches(Regex("[0-9a-f]{64}"))) }
            Entry(name, size, hash)
        }
        require(schema in 1..3 && entries.isNotEmpty() && entries.size < MAX_ENTRIES)
        require(entries.map(Entry::name).toSet().size == entries.size)
        requireDeclaredBounds(entries)
        return Manifest(schema, created, entries)
    }

    private fun requireDeclaredBounds(entries: List<Entry>) {
        require(entries.isNotEmpty() && entries.size < MAX_ENTRIES)
        var total = 0L
        for (entry in entries) {
            require(entry.size <= MAX_ARCHIVE_BYTES - total) { "Declared archive data exceeds limit" }
            total += entry.size
        }
    }

    private fun requireName(name: String) {
        require(name == "database/store.db" || name.matches(Regex("media/[A-Za-z0-9_-]{1,80}\\.(jpg|png|webp)")))
    }

    private fun InputStream.readBounded(maximum: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            require(count <= maximum - output.size()) { "Manifest exceeds limit" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun digest(file: File): String = file.inputStream().buffered().use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
        digest.digest().hex()
    }

    private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
}
