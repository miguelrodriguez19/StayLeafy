package com.miguelrodriguez19.stayleafy.ui.plant

import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

internal fun formatEpochDay(epochDay: Long): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.getDefault())
        .format(LocalDate.ofEpochDay(epochDay))

internal fun epochDayToPickerMillis(epochDay: Long?): Long? =
    epochDay?.let { LocalDate.ofEpochDay(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }

internal fun pickerMillisToEpochDay(millis: Long): Long =
    java.time.Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
