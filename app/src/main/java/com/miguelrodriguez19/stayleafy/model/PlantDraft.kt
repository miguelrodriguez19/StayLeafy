package com.miguelrodriguez19.stayleafy.model

data class PlantDraft(
    val displayName: String,
    val commonName: String? = null,
    val scientificName: String? = null,
    val location: String? = null,
    val acquiredEpochDay: Long? = null,
    val description: String? = null,
)
