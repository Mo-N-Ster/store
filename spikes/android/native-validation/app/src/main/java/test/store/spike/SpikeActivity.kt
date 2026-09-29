package test.store.spike

import android.app.Activity
import android.os.Bundle
import android.os.Process
import android.util.AtomicFile
import android.util.Log
import android.widget.TextView
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import org.json.JSONObject

// Exported command entry is DEBUG SPIKE ONLY, never a production security pattern.
class SpikeActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(TextView(this).apply { text = "Isolated native evidence; no STORE data" })
        val action = intent.getStringExtra("action") ?: "recover"
        val case = intent.getStringExtra("case") ?: "default"
        require(case.matches(Regex("[a-zA-Z0-9_-]{1,60}")))
        val point = intent.getIntExtra("point", 0)
        val mode = intent.getStringExtra("mode") ?: "pause"
        Thread {
            try {
                val root = File(filesDir, "cases/$case").apply { mkdirs() }
                val recovery = Recovery(this, root)
                val result = when (action) {
                    "prepare" -> recovery.prepare(point, mode)
                    "recover" -> recovery.recover()
                    "pdf" -> PdfProbe.run(this)
                    else -> error("UNKNOWN_ACTION")
                }
                val output = JSONObject().put("status", "PASS").put("action", action)
                    .put("case", case).put("pid", Process.myPid()).put("detail", result)
                File(filesDir, "last-result.json").writeText(output.toString())
                Log.i("STORE_SPIKE", output.toString())
            } catch (e: Fault) {
                File(filesDir, "last-result.json").writeText(JSONObject().put("status", "INJECTED")
                    .put("point", e.point).put("pid", Process.myPid()).toString())
            } catch (e: Throwable) {
                val output = JSONObject().put("status", "FAIL").put("error", e.toString())
                File(filesDir, "last-result.json").writeText(output.toString())
                Log.e("STORE_SPIKE", "FAIL", e)
            }
        }.start()
    }
}
class Fault(val point: Int) : RuntimeException("INJECTED_$point")

class Recovery(private val context: Activity, private val root: File) {
    private val driver = BundledSQLiteDriver()
    private fun sql(c: SQLiteConnection, query: String): List<List<String>> = c.prepare(query).use { s ->
        buildList { while (s.step()) add((0 until s.getColumnCount()).map { s.getText(it) }) }
    }
    private fun open(name: String): SpikeDb = Room.databaseBuilder(context, SpikeDb::class.java,
        File(root, "$name/data.sqlite").absolutePath)
        .setDriver(driver).setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
        .addCallback(object : RoomDatabase.Callback() {
            override fun onOpen(connection: SQLiteConnection) {
                sql(connection, "PRAGMA foreign_keys=ON")
                sql(connection, "PRAGMA synchronous=FULL")
                sql(connection, "PRAGMA busy_timeout=5000")
            }
        }).build()
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun durable(file: File, bytes: ByteArray) { FileOutputStream(file).use { it.write(bytes); it.fd.sync() } }
    private fun writeMarker(name: String, data: String) {
        val f = AtomicFile(File(root, name)); val stream = f.startWrite()
        try { stream.write(data.toByteArray()); f.finishWrite(stream) } catch (e: Throwable) { f.failWrite(stream); throw e }
    }
    private fun marker(name: String) = AtomicFile(File(root, name)).readFully().toString(Charsets.UTF_8)
    private fun hit(at: Int, point: Int, mode: String) {
        if (at != point) return
        writeMarker("phase.json", JSONObject().put("point", point).put("pid", Process.myPid()).toString())
        Log.i("STORE_SPIKE", "READY point=$point pid=${Process.myPid()}")
        if (mode == "fault") throw Fault(point)
        // External adb must kill the still-alive process; no self-kill or normal close.
        while (true) Thread.sleep(1000)
    }
    private fun make(name: String, point: Int = 0, mode: String = "pause") {
        val directory = File(root, name).apply { check(mkdir()) }
        File(directory, "media").mkdir()
        val db = open(name)
        try {
            db.runInTransaction {
                db.data().parent(Parent().apply { id = 1; generation = name; cents = 125025 })
                for (i in 1..2) db.data().media(Media().apply {
                    id = i.toLong(); parentId = 1; filename = "$i.bin"; hash = hash("$name-$i".toByteArray())
                })
            }
            // Prove actual FK rejection plus rollback of a preceding valid write.
            var rejected = false
            try { db.runInTransaction {
                db.data().parent(Parent().apply { id = 9; generation = "must-rollback" })
                db.data().media(Media().apply { id = 9; parentId = 999; filename = "bad"; hash = "bad" })
            } } catch (_: Exception) { rejected = true }
            check(rejected && db.data().parents().size == 1 && db.data().media().size == 2)
        } finally { db.close() }
        // No open Room connections; a controlled maintenance connection checkpoints the same driver.
        driver.open(File(directory, "data.sqlite").absolutePath).use { c ->
            check(sql(c, "PRAGMA wal_checkpoint(TRUNCATE)").single() == listOf("0", "0", "0"))
        }
        for (i in 1..2) {
            durable(File(directory, "media/$i.bin"), "$name-$i".toByteArray())
            if (name == "new" && i == 1) hit(2, point, mode)
        }
    }
    private fun verify(name: String): JSONObject {
        check(name in listOf("old", "new"))
        val db = open(name)
        try {
            check(db.data().parents().single().let { it.generation == name && it.cents == 125025L })
            check(db.data().media().size == 2)
            db.data().media().forEach { check(hash(File(root, "$name/media/${it.filename}").readBytes()) == it.hash) }
        } finally { db.close() }
        return driver.open(File(root, "$name/data.sqlite").absolutePath).use { c ->
            check(sql(c, "PRAGMA integrity_check") == listOf(listOf("ok")))
            check(sql(c, "PRAGMA foreign_key_check").isEmpty())
            check(sql(c, "PRAGMA user_version").single().single() == "1")
            JSONObject().put("generation", name).put("integrity", "ok").put("fk", "ok")
                .put("schema", 1).put("parents", 1).put("media", 2)
                .put("sqlite", sql(c, "SELECT sqlite_version()").single().single())
        }
    }
    fun prepare(point: Int, mode: String): JSONObject {
        check(point in 1..7)
        check(!File(root, "active").exists())
        make("old"); verify("old"); writeMarker("active", "old")
        hit(1, point, mode)
        make("new", point, mode); verify("new"); hit(3, point, mode)
        // The old generation is retained as rollback material; snapshot taken only after closure.
        File(root, "old/data.sqlite").copyTo(File(root, "safety.sqlite"))
        writeMarker("journal", "PREPARED:RESTORE:old:new"); hit(4, point, mode)
        val active = AtomicFile(File(root, "active")); val stream = active.startWrite()
        stream.write("new".toByteArray()); stream.fd.sync()
        hit(5, point, mode) // partial AtomicFile publish, external death before finishWrite.
        active.finishWrite(stream)
        hit(6, point, mode) // DB/media become visible together through one pointer.
        verify("new"); hit(7, point, mode)
        return recover()
    }
    fun recover(): JSONObject {
        val active = marker("active")
        val result = verify(active)
        if (File(root, "journal").exists()) check(marker("journal") == "PREPARED:RESTORE:old:new")
        // Keep both generations and inactive staging; only AtomicFile handles its temporary file.
        writeMarker("verified", active)
        return result.put("recovery", "idempotent-candidate").put("pid", Process.myPid())
    }
}
