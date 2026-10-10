package com.miguelrodriguez19.stayleafy.model

data class CareEvent(
    val id: String,
    val plantId: String,
    val careType: CareType,
    val performedAt: Long,
    val note: String?,
    val createdAt: Long,
    val updatedAt: Long,
)
