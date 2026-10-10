package com.miguelrodriguez19.stayleafy.data.local.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "plants",
    indices = [
        Index(value = ["name"]),
        Index(value = ["isFavorite"]),
    ],
)
data class PlantEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val nameSearch: String,
    val scientificName: String?,
    val scientificNameSearch: String,
    val location: String?,
    val acquiredEpochDay: Long?,
    val description: String?,
    val mainPhotoPath: String?,
    val isFavorite: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)
