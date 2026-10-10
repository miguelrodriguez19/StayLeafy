package com.miguelrodriguez19.stayleafy.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.UUID

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val migrationTimestamp = System.currentTimeMillis()

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `plants` (
                `id` TEXT NOT NULL,
                `displayName` TEXT NOT NULL,
                `displayNameSearch` TEXT NOT NULL,
                `commonName` TEXT,
                `commonNameSearch` TEXT NOT NULL,
                `scientificName` TEXT,
                `scientificNameSearch` TEXT NOT NULL,
                `location` TEXT,
                `acquiredEpochDay` INTEGER,
                `description` TEXT,
                `mainPhotoPath` TEXT,
                `isFavorite` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_plants_displayName` ON `plants` (`displayName`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_plants_isFavorite` ON `plants` (`isFavorite`)")
        db.query("SELECT `uid`, `name` FROM `Plant`").use { cursor ->
            val uidIndex = cursor.getColumnIndexOrThrow("uid")
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) {
                val legacyUid = cursor.getLong(uidIndex)
                val displayName = cursor.getString(nameIndex)
                    ?.trim()
                    .orEmpty()
                    .ifBlank { "Plant $legacyUid" }
                db.execSQL(
                    """
                    INSERT INTO `plants`(
                        `id`, `displayName`, `displayNameSearch`, `commonName`,
                        `commonNameSearch`, `scientificName`, `scientificNameSearch`,
                        `location`, `acquiredEpochDay`, `description`, `mainPhotoPath`,
                        `isFavorite`, `createdAt`, `updatedAt`
                    ) VALUES (?, ?, ?, NULL, '', NULL, '', NULL, NULL, NULL, NULL, 0, ?, ?)
                    """.trimIndent(),
                    arrayOf<Any?>(
                        UUID.randomUUID().toString(),
                        displayName,
                        displayName.normalizeForPlantSearch(),
                        migrationTimestamp,
                        migrationTimestamp,
                    ),
                )
            }
        }
        db.execSQL("DROP TABLE `Plant`")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `plant_notes` (
                `id` TEXT NOT NULL,
                `plantId` TEXT NOT NULL,
                `body` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`plantId`) REFERENCES `plants`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_plant_notes_plantId_createdAt` " +
                "ON `plant_notes` (`plantId`, `createdAt`)",
        )
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE `plants_new` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `nameSearch` TEXT NOT NULL,
                `scientificName` TEXT,
                `scientificNameSearch` TEXT NOT NULL,
                `location` TEXT,
                `acquiredEpochDay` INTEGER,
                `description` TEXT,
                `mainPhotoPath` TEXT,
                `isFavorite` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE `plant_notes_backup` (
                `id` TEXT NOT NULL,
                `plantId` TEXT NOT NULL,
                `body` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO `plants_new`(
                `id`, `name`, `nameSearch`, `scientificName`, `scientificNameSearch`,
                `location`, `acquiredEpochDay`, `description`, `mainPhotoPath`,
                `isFavorite`, `createdAt`, `updatedAt`
            )
            SELECT
                `id`, `displayName`, `displayNameSearch`, `scientificName`, `scientificNameSearch`,
                `location`, `acquiredEpochDay`, `description`, `mainPhotoPath`,
                `isFavorite`, `createdAt`, `updatedAt`
            FROM `plants`
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO `plant_notes_backup`(`id`, `plantId`, `body`, `createdAt`, `updatedAt`)
            SELECT `id`, `plantId`, `body`, `createdAt`, `updatedAt`
            FROM `plant_notes`
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE `plant_notes`")
        db.execSQL("DROP TABLE `plants`")
        db.execSQL("ALTER TABLE `plants_new` RENAME TO `plants`")
        db.execSQL(
            """
            CREATE TABLE `plant_notes` (
                `id` TEXT NOT NULL,
                `plantId` TEXT NOT NULL,
                `body` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`plantId`) REFERENCES `plants`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO `plant_notes`(`id`, `plantId`, `body`, `createdAt`, `updatedAt`)
            SELECT `id`, `plantId`, `body`, `createdAt`, `updatedAt`
            FROM `plant_notes_backup`
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE `plant_notes_backup`")
        db.execSQL("CREATE INDEX `index_plants_name` ON `plants` (`name`)")
        db.execSQL("CREATE INDEX `index_plants_isFavorite` ON `plants` (`isFavorite`)")
        db.execSQL(
            "CREATE INDEX `index_plant_notes_plantId_createdAt` " +
                "ON `plant_notes` (`plantId`, `createdAt`)",
        )
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `care_events` (
                `id` TEXT NOT NULL,
                `plantId` TEXT NOT NULL,
                `careType` TEXT NOT NULL,
                `performedAt` INTEGER NOT NULL,
                `note` TEXT,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`plantId`) REFERENCES `plants`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_care_events_plantId_performedAt` " +
                "ON `care_events` (`plantId`, `performedAt`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_care_events_plantId_careType_performedAt` " +
                "ON `care_events` (`plantId`, `careType`, `performedAt`)",
        )
    }
}
