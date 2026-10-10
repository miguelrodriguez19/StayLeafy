package com.miguelrodriguez19.stayleafy.data.local.database

import java.util.Locale

internal fun String?.normalizeForPlantSearch(): String =
    this?.lowercase(Locale.ROOT).orEmpty()
