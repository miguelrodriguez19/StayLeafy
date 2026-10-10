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
