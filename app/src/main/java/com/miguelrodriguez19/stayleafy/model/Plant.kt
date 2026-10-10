package com.miguelrodriguez19.stayleafy.model

data class Plant(
    val id: String,
    val name: String,
    val scientificName: String?,
    val location: String?,
    val acquiredEpochDay: Long?,
    val description: String?,
    val mainPhotoPath: String?,
    val isFavorite: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)
