package com.miguelrodriguez19.stayleafy.data.room

import com.miguelrodriguez19.stayleafy.data.local.database.PlantEntity

internal fun testPlant(
    id: String,
    displayName: String,
    commonName: String? = null,
    scientificName: String? = null,
    isFavorite: Boolean = false,
    createdAt: Long = 1_000L,
    updatedAt: Long = 2_000L,
): PlantEntity = PlantEntity(
    id = id,
    displayName = displayName,
    displayNameSearch = displayName.lowercase(java.util.Locale.ROOT),
    commonName = commonName,
    commonNameSearch = commonName?.lowercase(java.util.Locale.ROOT).orEmpty(),
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
