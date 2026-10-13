package com.miguelrodriguez19.stayleafy.model

import java.time.LocalDate
import java.time.Month
import java.time.MonthDay

enum class ReminderScheduleType {
    INTERVAL,
    SEASONAL,
}

enum class CareScheduleMode {
    DISABLED,
    INTERVAL,
    SEASONAL,
}

data class AnnualDate(
    val month: Int,
    val day: Int,
) {
    init {
        MonthDay.of(month, day)
    }

    fun atYear(year: Int): LocalDate {
        val maximumDay = Month.of(month).length(java.time.Year.isLeap(year.toLong()))
        return LocalDate.of(year, month, day.coerceAtMost(maximumDay))
    }
}

data class SeasonalReminderRule(
    val id: String,
    val plantId: String,
    val careType: CareType,
    val enabled: Boolean,
    val start: AnnualDate,
    val end: AnnualDate?,
    val createdAt: Long,
    val updatedAt: Long,
)

data class IntervalReminderConfiguration(
    val intervalDays: Int,
    val dueSource: ReminderDueSource,
)

data class SeasonalReminderConfiguration(
    val start: AnnualDate,
    val end: AnnualDate? = null,
)

data class CareScheduleDraft(
    val careType: CareType,
    val mode: CareScheduleMode,
    val interval: IntervalReminderConfiguration? = null,
    val seasonal: SeasonalReminderConfiguration? = null,
)

enum class SeasonalCareState {
    UPCOMING,
    ACTIVE,
    SATISFIED,
}

data class SeasonalCareSummary(
    val plantId: String,
    val careType: CareType,
    val startEpochDay: Long,
    val endEpochDay: Long,
    val state: SeasonalCareState,
)

data class SeasonalOccurrence(
    val startEpochDay: Long,
    val endEpochDay: Long,
) {
    operator fun contains(epochDay: Long): Boolean = epochDay in startEpochDay..endEpochDay
}

fun seasonalOccurrenceStartingInYear(
    startYear: Int,
    start: AnnualDate,
    end: AnnualDate?,
): SeasonalOccurrence {
    val startDate = start.atYear(startYear)
    val endDate = when {
        end == null -> startDate
        end.month > start.month || end.month == start.month && end.day >= start.day -> end.atYear(startYear)
        else -> end.atYear(startYear + 1)
    }
    return SeasonalOccurrence(startDate.toEpochDay(), endDate.toEpochDay())
}

fun seasonalOccurrenceContaining(
    epochDay: Long,
    start: AnnualDate,
    end: AnnualDate?,
): SeasonalOccurrence? {
    val year = LocalDate.ofEpochDay(epochDay).year
    return sequenceOf(year, year - 1)
        .map { seasonalOccurrenceStartingInYear(it, start, end) }
        .firstOrNull { epochDay in it }
}

fun currentOrNextSeasonalOccurrence(
    todayEpochDay: Long,
    start: AnnualDate,
    end: AnnualDate?,
): SeasonalOccurrence {
    seasonalOccurrenceContaining(todayEpochDay, start, end)?.let { return it }
    val year = LocalDate.ofEpochDay(todayEpochDay).year
    val thisYear = seasonalOccurrenceStartingInYear(year, start, end)
    return if (thisYear.startEpochDay >= todayEpochDay) {
        thisYear
    } else {
        seasonalOccurrenceStartingInYear(year + 1, start, end)
    }
}
