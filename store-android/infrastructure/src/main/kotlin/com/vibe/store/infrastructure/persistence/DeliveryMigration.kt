package com.vibe.store.infrastructure.persistence

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** Adds queue ownership without deleting historical rows. Unknown legacy queue entries fail closed. */
internal object DeliveryMigration2To3 : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
        val columns = buildSet {
            connection.prepare("PRAGMA table_info(email_report_logs)").use { statement ->
                while (statement.step()) add(statement.getText(1))
            }
        }
        if ("actorId" !in columns) {
            connection.execSQL("ALTER TABLE email_report_logs ADD COLUMN actorId INTEGER NOT NULL DEFAULT 0")
        }
        if ("generation" !in columns) {
            connection.execSQL("ALTER TABLE email_report_logs ADD COLUMN generation TEXT NOT NULL DEFAULT ''")
        }
        connection.execSQL("UPDATE email_report_logs SET status='failed',nextAttemptAt=NULL,lastError='legacy_sender_unknown' WHERE status IN ('pending','sending')")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_email_report_logs_status_nextAttemptAt_createdAt ON email_report_logs(status,nextAttemptAt,createdAt)")
    }
}
