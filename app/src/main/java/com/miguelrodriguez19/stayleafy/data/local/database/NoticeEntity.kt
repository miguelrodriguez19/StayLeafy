package com.miguelrodriguez19.stayleafy.data.local.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notices",
    foreignKeys = [
        ForeignKey(
            entity = ReminderRuleEntity::class,
            parentColumns = ["id"],
            childColumns = ["ruleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["ruleId"]),
        Index(value = ["occurrenceKey"], unique = true),
        Index(value = ["readAt", "createdAt"]),
    ],
)
data class NoticeEntity(
    @PrimaryKey
    val id: String,
    val ruleId: String,
    val occurrenceKey: String,
    val careType: String,
    val dueEpochDay: Long,
    val createdAt: Long,
    val readAt: Long?,
    val deliveryState: String,
)

data class NoticeRecord(
    val id: String,
    val ruleId: String,
    val occurrenceKey: String,
    val plantId: String,
    val plantName: String,
    val plantPhotoPath: String?,
    val careType: String,
    val scheduleType: String,
    val dueEpochDay: Long,
    val createdAt: Long,
    val readAt: Long?,
    val deliveryState: String,
)
