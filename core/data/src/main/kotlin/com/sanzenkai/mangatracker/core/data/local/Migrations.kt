package com.sanzenkai.mangatracker.core.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 =
    object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `domain_parser_config` RENAME TO `site_configuration`")
            db.execSQL("ALTER TABLE `monitoring_state` RENAME TO `monitoring_settings`")
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS
                `index_chapter_mangaId_firstDetectedAtEpochMillis`
                ON `chapter` (`mangaId`, `firstDetectedAtEpochMillis`)
                """.trimIndent(),
            )
        }
    }

val MIGRATION_2_3 =
    object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `pending_chapter_notification` (
                    `chapterId` TEXT NOT NULL,
                    PRIMARY KEY(`chapterId`),
                    FOREIGN KEY(`chapterId`) REFERENCES `chapter`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
        }
    }
