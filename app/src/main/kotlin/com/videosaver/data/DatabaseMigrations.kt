package com.videosaver.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS history_new (
                    id TEXT NOT NULL PRIMARY KEY,
                    filename TEXT NOT NULL,
                    sourceUrl TEXT NOT NULL,
                    localPath TEXT NOT NULL,
                    platform TEXT NOT NULL,
                    timestamp INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO history_new (id, filename, sourceUrl, localPath, platform, timestamp)
                SELECT lower(hex(randomblob(16))), filename, sourceUrl, localPath, platform, timestamp
                FROM history
                """.trimIndent()
            )
            db.execSQL("DROP TABLE history")
            db.execSQL("ALTER TABLE history_new RENAME TO history")
        }
    }
}
