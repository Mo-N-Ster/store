package com.vibe.store.infrastructure.persistence

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import java.math.BigDecimal

/**
 * Room runs this within its upgrade transaction. The owner first checkpoints and
 * verifies its durable snapshot/journal. No FK disabling, destructive fallback,
 * source deletion on error, or recovery-journal changes are allowed here.
 *
 * V1 REAL values are interpreted with BigDecimal.valueOf: the shortest round-trip
 * decimal spelling of the finite stored binary64 value, then normalized. This
 * cannot reconstruct decimal precision already lost before this migration.
 */
internal object MoneyMigration1To2 : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        data class Table(val name: String, val sql: String)
        val tables = connection.prepare("SELECT name,sql FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name!='room_master_table' ORDER BY rowid").use { query ->
            buildList { while (query.step()) add(Table(query.getText(0), query.getText(1))) }
        }
        check(MoneyColumns.tables.keys.all { name -> tables.any { it.name == name } }) { "Incomplete legacy monetary schema" }
        val objects = connection.prepare("SELECT name,sql FROM sqlite_master WHERE type IN ('index','trigger') AND sql IS NOT NULL ORDER BY rowid").use { query ->
            buildList { while (query.step()) add(query.getText(0) to query.getText(1)) }
        }
        // Backups have no FKs/triggers. All rows (including nonmonetary dependents)
        // survive CASCADE during table replacement; only a single row is held in JVM memory.
        // TEMP tables and source DDL participate in Room's same atomic transaction.
        connection.execSQL("PRAGMA defer_foreign_keys=ON")
        for (table in tables) {
            check(table.name.matches(Regex("[a-z_]+"))) { "Unexpected legacy table name" }
            val columns = connection.prepare("PRAGMA table_info(`${table.name}`)").use { query ->
                buildList { while (query.step()) add(query.getText(1)) }
            }
            val monetary = MoneyColumns.tables[table.name].orEmpty()
            check(columns.containsAll(monetary))
            connection.execSQL("CREATE TEMP TABLE `money_backup_${table.name}` AS SELECT * FROM `${table.name}`")
            // Copy without affinity coercion: replace monetary column affinities in
            // the destination schema, bind canonical TEXT directly during restore.
            for (column in monetary) {
                val declaration = "`$column` REAL"
                check(table.sql.contains(declaration)) { "Unexpected legacy monetary affinity: ${table.name}.$column" }
            }
        }
        // Drop children before parents where possible. Deferred FKs cover any
        // different creation order; backups already contain all dependent records.
        for (table in tables.asReversed()) connection.execSQL("DROP TABLE `${table.name}`")
        for (table in tables) {
            var sql = table.sql
            for (column in MoneyColumns.tables[table.name].orEmpty()) sql = sql.replace("`$column` REAL", "`$column` TEXT")
            connection.execSQL(sql)
        }
        for ((name, sql) in objects) {
            if (!name.startsWith("check_") && !name.startsWith("cash_one_open_")) connection.execSQL(sql)
        }
        for (table in tables) {
            val monetary = MoneyColumns.tables[table.name].orEmpty()
            connection.prepare("SELECT * FROM `money_backup_${table.name}`").use { row ->
                val columns = (0 until row.getColumnCount()).map { row.getColumnName(it) }
                val names = columns.joinToString(",") { "`$it`" }
                connection.prepare("INSERT INTO `${table.name}` ($names) VALUES (${columns.joinToString(",") { "?" }})").use { insert ->
                    while (row.step()) {
                        for ((index, column) in columns.withIndex()) {
                            val parameter = index + 1
                            if (row.isNull(index)) insert.bindNull(parameter)
                            else if (column in monetary) {
                                // Reject corrupt storage instead of coercing TEXT/BLOB to a number.
                                check(row.getColumnType(index) in 1..2) { "Invalid legacy monetary storage: ${table.name}.$column" }
                                val value = row.getDouble(index)
                                require(value.isFinite()) { "Nonfinite legacy monetary value: ${table.name}.$column" }
                                val decimal = BigDecimal.valueOf(value)
                                require(MoneyColumns.signed(table.name, column) || decimal.signum() >= 0) { "Negative legacy monetary value" }
                                insert.bindText(parameter, MoneyText.canonical(decimal))
                            } else when (row.getColumnType(index)) {
                                1 -> insert.bindLong(parameter, row.getLong(index))
                                3 -> insert.bindText(parameter, row.getText(index))
                                4 -> insert.bindBlob(parameter, row.getBlob(index))
                                else -> error("Unexpected legacy nonmonetary storage: ${table.name}.$column")
                            }
                        }
                        insert.step()
                        insert.reset()
                        insert.clearBindings()
                    }
                }
            }
            connection.execSQL("DROP TABLE `money_backup_${table.name}`")
        }
        StoreConstraints.create(connection)
        check(connection.healthy() == com.vibe.store.application.persistence.IntegrityResult(true, true, true)) { "Migrated database integrity failed" }
    }
}
