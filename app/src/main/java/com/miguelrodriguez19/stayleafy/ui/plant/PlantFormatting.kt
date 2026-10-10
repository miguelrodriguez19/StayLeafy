package com.miguelrodriguez19.stayleafy.ui.plant

import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

internal fun formatEpochDay(epochDay: Long): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.getDefault())
        .format(LocalDate.ofEpochDay(epochDay))

internal fun formatTimestamp(epochMillis: Long): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
        .withLocale(Locale.getDefault())
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(epochMillis))

internal fun epochDayToPickerMillis(epochDay: Long?): Long? =
    epochDay?.let { LocalDate.ofEpochDay(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }

internal fun initialAcquisitionDatePickerMillis(
    acquiredEpochDay: Long?,
    todayEpochDay: Long = LocalDate.now().toEpochDay(),
): Long = epochDayToPickerMillis(acquiredEpochDay ?: todayEpochDay)!!

internal fun pickerMillisToEpochDay(millis: Long): Long =
    java.time.Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
