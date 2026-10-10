package com.miguelrodriguez19.stayleafy.data.local.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "care_events",
    foreignKeys = [
        ForeignKey(
            entity = PlantEntity::class,
            parentColumns = ["id"],
            childColumns = ["plantId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["plantId", "performedAt"]),
        Index(value = ["plantId", "careType", "performedAt"]),
    ],
)
data class CareEventEntity(
    @PrimaryKey
    val id: String,
    val plantId: String,
    val careType: String,
    val performedAt: Long,
    val note: String?,
    val createdAt: Long,
    val updatedAt: Long,
)
