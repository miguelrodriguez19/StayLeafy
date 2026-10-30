package com.miguelrodriguez19.stayleafy.model

sealed interface CareEventCreateResult {
    data class Created(val id: String) : CareEventCreateResult

    data object AlreadyRecorded : CareEventCreateResult
}
