package com.miguelrodriguez19.stayleafy.data.local.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(
    tableName = "plant_notes",
    foreignKeys = [
        ForeignKey(
            entity = PlantEntity::class,
            parentColumns = ["id"],
            childColumns = ["plantId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["plantId", "createdAt"]),
    ],
)
@Serializable
data class PlantNoteEntity(
    @PrimaryKey
    val id: String,
    val plantId: String,
    val body: String,
    val createdAt: Long,
    val updatedAt: Long,
)
