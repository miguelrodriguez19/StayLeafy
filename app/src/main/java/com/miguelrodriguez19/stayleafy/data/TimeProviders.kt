package com.miguelrodriguez19.stayleafy.data

import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

interface ZoneIdProvider {
    fun currentZoneId(): ZoneId
}

class SystemZoneIdProvider @Inject constructor() : ZoneIdProvider {
    override fun currentZoneId(): ZoneId = ZoneId.systemDefault()
}

internal fun epochDayAt(
    epochMillis: Long,
    zoneId: ZoneId,
): Long = Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalDate().toEpochDay()
