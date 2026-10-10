package com.miguelrodriguez19.stayleafy.model

data class CareEventDraft(
    val careType: CareType,
    val performedAt: Long,
    val note: String?,
)
