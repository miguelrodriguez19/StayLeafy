package com.miguelrodriguez19.stayleafy.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeasonalReminderRuleTest {
    @Test
    fun sameYearWindow_isInclusive() {
        val occurrence = seasonalOccurrenceStartingInYear(
            2026,
            AnnualDate(3, 1),
            AnnualDate(5, 31),
        )

        assertEquals(LocalDate.of(2026, 3, 1).toEpochDay(), occurrence.startEpochDay)
        assertEquals(LocalDate.of(2026, 5, 31).toEpochDay(), occurrence.endEpochDay)
        assertEquals(occurrence, seasonalOccurrenceContaining(occurrence.endEpochDay, AnnualDate(3, 1), AnnualDate(5, 31)))
    }

    @Test
    fun endBeforeStart_wrapsIntoFollowingYear() {
        val occurrence = seasonalOccurrenceStartingInYear(
            2026,
            AnnualDate(11, 15),
            AnnualDate(2, 10),
        )

        assertEquals(LocalDate.of(2026, 11, 15).toEpochDay(), occurrence.startEpochDay)
        assertEquals(LocalDate.of(2027, 2, 10).toEpochDay(), occurrence.endEpochDay)
        assertEquals(
            occurrence,
            seasonalOccurrenceContaining(
                LocalDate.of(2027, 1, 1).toEpochDay(),
                AnnualDate(11, 15),
                AnnualDate(2, 10),
            ),
        )
    }

    @Test
    fun omittedEnd_createsOneDayWindow() {
        val occurrence = seasonalOccurrenceStartingInYear(2026, AnnualDate(8, 20), null)

        assertEquals(occurrence.startEpochDay, occurrence.endEpochDay)
        assertNull(
            seasonalOccurrenceContaining(
                LocalDate.of(2026, 8, 21).toEpochDay(),
                AnnualDate(8, 20),
                null,
            ),
        )
    }

    @Test
    fun february29_clampsToFebruary28InNonLeapYear() {
        assertEquals(LocalDate.of(2026, 2, 28), AnnualDate(2, 29).atYear(2026))
        assertEquals(LocalDate.of(2028, 2, 29), AnnualDate(2, 29).atYear(2028))
    }

    @Test
    fun currentOrNext_returnsCurrentCrossYearWindowThenNextOccurrence() {
        val start = AnnualDate(11, 15)
        val end = AnnualDate(2, 10)

        val current = currentOrNextSeasonalOccurrence(
            LocalDate.of(2027, 1, 10).toEpochDay(),
            start,
            end,
        )
        val next = currentOrNextSeasonalOccurrence(
            LocalDate.of(2027, 3, 1).toEpochDay(),
            start,
            end,
        )

        assertEquals(LocalDate.of(2026, 11, 15).toEpochDay(), current.startEpochDay)
        assertEquals(LocalDate.of(2027, 11, 15).toEpochDay(), next.startEpochDay)
    }
}
