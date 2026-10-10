package com.miguelrodriguez19.stayleafy.model

data class PlantListFilter(
    val query: String = "",
    val favoritesOnly: Boolean = false,
)
