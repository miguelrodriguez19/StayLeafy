package com.miguelrodriguez19.stayleafy.model

data class BulkCareResult(
    val recordedPlantIds: List<String>,
    val skippedPlantIds: List<String>,
)
