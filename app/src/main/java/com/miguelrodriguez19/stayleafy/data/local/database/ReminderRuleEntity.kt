package com.miguelrodriguez19.stayleafy.data.local.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reminder_rules",
    foreignKeys = [
        ForeignKey(
            entity = PlantEntity::class,
            parentColumns = ["id"],
            childColumns = ["plantId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["plantId", "careType", "scheduleType"], unique = true),
        Index(value = ["enabled", "nextDueEpochDay"]),
        Index(value = ["enabled", "scheduleType"]),
    ],
)
data class ReminderRuleEntity(
    @PrimaryKey
    val id: String,
    val plantId: String,
    val careType: String,
    val scheduleType: String,
    val enabled: Boolean,
    val intervalDays: Int?,
    val nextDueEpochDay: Long?,
    val seasonStartMonth: Int? = null,
    val seasonStartDay: Int? = null,
    val seasonEndMonth: Int? = null,
    val seasonEndDay: Int? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

const val INTERVAL_SCHEDULE_TYPE = "INTERVAL"
const val SEASONAL_SCHEDULE_TYPE = "SEASONAL"
