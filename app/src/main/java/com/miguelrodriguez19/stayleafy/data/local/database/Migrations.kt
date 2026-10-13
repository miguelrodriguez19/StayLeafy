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

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `reminder_rules` (
                `id` TEXT NOT NULL,
                `plantId` TEXT NOT NULL,
                `careType` TEXT NOT NULL,
                `scheduleType` TEXT NOT NULL,
                `enabled` INTEGER NOT NULL,
                `intervalDays` INTEGER NOT NULL,
                `nextDueEpochDay` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`plantId`) REFERENCES `plants`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_reminder_rules_plantId_careType_scheduleType` " +
                "ON `reminder_rules` (`plantId`, `careType`, `scheduleType`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_reminder_rules_enabled_nextDueEpochDay` " +
                "ON `reminder_rules` (`enabled`, `nextDueEpochDay`)",
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `notices` (
                `id` TEXT NOT NULL,
                `ruleId` TEXT NOT NULL,
                `occurrenceKey` TEXT NOT NULL,
                `careType` TEXT NOT NULL,
                `dueEpochDay` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `readAt` INTEGER,
                `deliveryState` TEXT NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`ruleId`) REFERENCES `reminder_rules`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_notices_ruleId` ON `notices` (`ruleId`)",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_notices_occurrenceKey` " +
                "ON `notices` (`occurrenceKey`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_notices_readAt_createdAt` " +
                "ON `notices` (`readAt`, `createdAt`)",
        )
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE `notices_backup` (
                `id` TEXT NOT NULL,
                `ruleId` TEXT NOT NULL,
                `occurrenceKey` TEXT NOT NULL,
                `careType` TEXT NOT NULL,
                `dueEpochDay` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `readAt` INTEGER,
                `deliveryState` TEXT NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO `notices_backup`
            SELECT `id`, `ruleId`, `occurrenceKey`, `careType`, `dueEpochDay`,
                   `createdAt`, `readAt`, `deliveryState`
            FROM `notices`
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE `notices`")
        db.execSQL("ALTER TABLE `reminder_rules` RENAME TO `reminder_rules_v6`")
        db.execSQL(
            """
            CREATE TABLE `reminder_rules` (
                `id` TEXT NOT NULL,
                `plantId` TEXT NOT NULL,
                `careType` TEXT NOT NULL,
                `scheduleType` TEXT NOT NULL,
                `enabled` INTEGER NOT NULL,
                `intervalDays` INTEGER,
                `nextDueEpochDay` INTEGER,
                `seasonStartMonth` INTEGER,
                `seasonStartDay` INTEGER,
                `seasonEndMonth` INTEGER,
                `seasonEndDay` INTEGER,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`plantId`) REFERENCES `plants`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO `reminder_rules`(
                `id`, `plantId`, `careType`, `scheduleType`, `enabled`,
                `intervalDays`, `nextDueEpochDay`, `seasonStartMonth`, `seasonStartDay`,
                `seasonEndMonth`, `seasonEndDay`, `createdAt`, `updatedAt`
            )
            SELECT `id`, `plantId`, `careType`, `scheduleType`, `enabled`,
                   `intervalDays`, `nextDueEpochDay`, NULL, NULL, NULL, NULL,
                   `createdAt`, `updatedAt`
            FROM `reminder_rules_v6`
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE `reminder_rules_v6`")
        db.execSQL(
            "CREATE UNIQUE INDEX `index_reminder_rules_plantId_careType_scheduleType` " +
                "ON `reminder_rules` (`plantId`, `careType`, `scheduleType`)",
        )
        db.execSQL(
            "CREATE INDEX `index_reminder_rules_enabled_nextDueEpochDay` " +
                "ON `reminder_rules` (`enabled`, `nextDueEpochDay`)",
        )
        db.execSQL(
            "CREATE INDEX `index_reminder_rules_enabled_scheduleType` " +
                "ON `reminder_rules` (`enabled`, `scheduleType`)",
        )
        db.execSQL(
            """
            CREATE TABLE `notices` (
                `id` TEXT NOT NULL,
                `ruleId` TEXT NOT NULL,
                `occurrenceKey` TEXT NOT NULL,
                `careType` TEXT NOT NULL,
                `dueEpochDay` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `readAt` INTEGER,
                `deliveryState` TEXT NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`ruleId`) REFERENCES `reminder_rules`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO `notices`
            SELECT `id`, `ruleId`, `occurrenceKey`, `careType`, `dueEpochDay`,
                   `createdAt`, `readAt`, `deliveryState`
            FROM `notices_backup`
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE `notices_backup`")
        db.execSQL("CREATE INDEX `index_notices_ruleId` ON `notices` (`ruleId`)")
        db.execSQL("CREATE UNIQUE INDEX `index_notices_occurrenceKey` ON `notices` (`occurrenceKey`)")
        db.execSQL(
            "CREATE INDEX `index_notices_readAt_createdAt` ON `notices` (`readAt`, `createdAt`)",
        )
    }
}
