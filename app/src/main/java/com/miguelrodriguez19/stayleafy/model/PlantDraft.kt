package com.miguelrodriguez19.stayleafy.model

data class PlantDraft(
    val name: String,
    val scientificName: String? = null,
    val location: String? = null,
    val acquiredEpochDay: Long? = null,
    val description: String? = null,
)
