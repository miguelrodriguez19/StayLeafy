package com.miguelrodriguez19.stayleafy.model

data class PlantNote(
    val id: String,
    val plantId: String,
    val body: String,
    val createdAt: Long,
    val updatedAt: Long,
)
