package com.miguelrodriguez19.stayleafy.data.room

import com.miguelrodriguez19.stayleafy.data.local.database.PlantEntity

internal fun testPlant(
    id: String,
    name: String,
    scientificName: String? = null,
    isFavorite: Boolean = false,
    createdAt: Long = 1_000L,
    updatedAt: Long = 2_000L,
): PlantEntity = PlantEntity(
    id = id,
    name = name,
    nameSearch = name.lowercase(java.util.Locale.ROOT),
    scientificName = scientificName,
    scientificNameSearch = scientificName?.lowercase(java.util.Locale.ROOT).orEmpty(),
    location = "Living room",
    acquiredEpochDay = 20_000L,
    description = "Test plant",
    mainPhotoPath = "photos/$id.jpg",
    isFavorite = isFavorite,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
