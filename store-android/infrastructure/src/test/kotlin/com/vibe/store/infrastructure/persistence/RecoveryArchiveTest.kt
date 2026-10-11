package com.vibe.store.infrastructure.persistence

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.zip.CRC32
import java.security.MessageDigest

class RecoveryArchiveTest {
    @Test fun streamedArchiveRoundTripsDatabaseAndAllowedMediaWithHashes() {
        val root = Files.createTempDirectory("recovery-archive").toFile()
        try {
            val db = File(root, "source.db").apply { writeBytes(ByteArray(4096) { (it % 251).toByte() }) }
            val image = File(root, "image.webp").apply { writeBytes(byteArrayOf(0x52, 0x49, 0x46, 0x46)) }
            val bytes = ByteArrayOutputStream()
            RecoveryArchive.write(bytes, 3, "2026-10-10T10:00:00Z", listOf(
                RecoveryArchive.Source("database/store.db", db),
                RecoveryArchive.Source("media/a_123.webp", image),
            ))
            val stage = File(root, "stage")
            val manifest = RecoveryArchive.read(ByteArrayInputStream(bytes.toByteArray()), stage)
            assertEquals(3, manifest.schema)
            assertEquals(2, manifest.entries.size)
            assertArrayEquals(db.readBytes(), File(stage, "database/store.db").readBytes())
            assertArrayEquals(image.readBytes(), File(stage, "media/a_123.webp").readBytes())
        } finally { root.deleteRecursively() }
    }

    @Test fun rejectsTraversalDuplicateUnexpectedAndTruncatedArchives() {
        fun archive(vararg entries: Pair<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip -> entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry()
            } }
        }.toByteArray()
        val root = Files.createTempDirectory("recovery-reject").toFile()
        try {
            val allowedName = "database/store.db"
            val name = allowedName.toByteArray().joinToString("") { "%02x".format(it) }
            val hash = MessageDigest.getInstance("SHA-256").digest(byteArrayOf(1)).joinToString("") { "%02x".format(it) }
            val duplicateManifest = "STORE_BACKUP_V1\napp=3.0\nschema=3\ncreated=2026-10-10T10:00:00Z\nentry=$name|1|$hash\nentry=$name|1|$hash\n".toByteArray()
            val traversal = "../evil".toByteArray().joinToString("") { "%02x".format(it) }
            val manifest = "STORE_BACKUP_V1\napp=3.0\nschema=3\ncreated=2026-10-10T10:00:00Z\nentry=$traversal|1|${"0".repeat(64)}\n".toByteArray()
            val rejected = listOf(
                archive("manifest.txt" to manifest, "../evil" to byteArrayOf(1)),
                archive("manifest.txt" to duplicateManifest),
                archive("manifest.txt" to "STORE_BACKUP_V1\napp=3.0\nschema=3\ncreated=2026-10-10T10:00:00Z\n".toByteArray()),
                archive("wrong-first" to byteArrayOf(1)),
                byteArrayOf(0x50, 0x4b, 0x03),
            )
            rejected.forEachIndexed { index, bytes ->
                val stage = File(root, "stage-$index")
                val threw = runCatching { RecoveryArchive.read(ByteArrayInputStream(bytes), stage) }.isFailure
                assertTrue("case $index", threw)
            }
        } finally { root.deleteRecursively() }
    }

    @Test fun contentDigestMismatchAndUnsupportedSchemaAreRejected() {
        val root = Files.createTempDirectory("recovery-integrity").toFile()
        try {
            val database = File(root, "db").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val archive = ByteArrayOutputStream().also { output ->
                ZipOutputStream(output).use { zip ->
                    fun stored(name: String, bytes: ByteArray) {
                        val crc = CRC32().apply { update(bytes) }
                        zip.putNextEntry(ZipEntry(name).apply {
                            method = ZipEntry.STORED; size = bytes.size.toLong(); compressedSize = size; this.crc = crc.value
                        })
                        zip.write(bytes); zip.closeEntry()
                    }
                    val data = database.readBytes()
                    val encodedName = "database/store.db".toByteArray().joinToString("") { "%02x".format(it) }
                    val falseDigest = "0".repeat(64)
                    stored("manifest.txt", "STORE_BACKUP_V1\napp=3.0\nschema=3\ncreated=2026-10-10T10:00:00Z\nentry=$encodedName|${data.size}|$falseDigest\n".toByteArray())
                    stored("database/store.db", data)
                }
            }.toByteArray()
            assertTrue(runCatching { RecoveryArchive.read(ByteArrayInputStream(archive), File(root, "tampered")) }.isFailure)
            assertTrue(runCatching {
                RecoveryArchive.write(ByteArrayOutputStream(), 0, "2026-10-10T10:00:00Z", listOf(RecoveryArchive.Source("database/store.db", database)))
            }.isFailure)
            assertTrue(runCatching {
                RecoveryArchive.write(ByteArrayOutputStream(), 4, "2026-10-10T10:00:00Z", listOf(RecoveryArchive.Source("database/store.db", database)))
            }.isFailure)
            val legacy = ByteArrayOutputStream().also { output ->
                RecoveryArchive.write(output, 2, "2026-10-10T10:00:00Z", listOf(RecoveryArchive.Source("database/store.db", database)))
            }
            assertEquals(2, RecoveryArchive.read(ByteArrayInputStream(legacy.toByteArray()), File(root, "legacy")).schema)
        } finally { root.deleteRecursively() }
    }

    @Test fun rejectsDeclaredArchiveExpansionBeyondBoundBeforeWritingEntries() {
        val root = Files.createTempDirectory("recovery-oversize").toFile()
        try {
            val encodedName = "database/store.db".toByteArray().joinToString("") { "%02x".format(it) }
            val digest = "0".repeat(64)
            val manifest = "STORE_BACKUP_V1\napp=3.0\nschema=3\ncreated=2026-10-10T10:00:00Z\n" +
                "entry=$encodedName|${RecoveryArchive.MAX_ARCHIVE_BYTES + 1}|$digest\n"
            val bytes = ByteArrayOutputStream().also { output ->
                ZipOutputStream(output).use { zip ->
                    zip.putNextEntry(ZipEntry("manifest.txt")); zip.write(manifest.toByteArray()); zip.closeEntry()
                    zip.putNextEntry(ZipEntry("database/store.db")); zip.write(byteArrayOf(1)); zip.closeEntry()
                }
            }.toByteArray()
            val stage = File(root, "stage")
            assertTrue(runCatching { RecoveryArchive.read(ByteArrayInputStream(bytes), stage) }.isFailure)
            assertTrue(stage.listFiles().isNullOrEmpty())
        } finally { root.deleteRecursively() }
    }

}
