package com.miguelrodriguez19.stayleafy.platform.reminder

import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderScheduleTest {

    private val madrid = ZoneId.of("Europe/Madrid")

    @Test
    fun beforeNine_schedulesTodayAtNineLocal() {
        val now = Instant.parse("2026-10-11T05:30:00Z") // 07:30 in Madrid.

        assertEquals(Duration.ofMinutes(90), delayUntilNextReminderWindow(now, madrid))
    }

    @Test
    fun atNine_schedulesTheFollowingMorning() {
        val now = Instant.parse("2026-10-11T07:00:00Z") // 09:00 in Madrid.

        assertEquals(Duration.ofHours(24), delayUntilNextReminderWindow(now, madrid))
    }

    @Test
    fun nextWindow_usesLocalTimezoneAcrossDaylightSavingTransition() {
        val now = Instant.parse("2026-10-24T08:00:00Z") // 10:00 CEST, day before fallback.

        assertEquals(Duration.ofHours(24), delayUntilNextReminderWindow(now, madrid))
    }

    @Test
    fun customTimeBeforeWindow_schedulesTheSameLocalDay() {
        val now = Instant.parse("2026-10-11T05:30:00Z") // 07:30 in Madrid.

        assertEquals(
            Duration.ofMinutes(30),
            delayUntilNextReminderWindow(now, madrid, LocalTime.of(8, 0)),
        )
    }

    @Test
    fun customTimeAfterWindow_schedulesTheNextLocalDay() {
        val now = Instant.parse("2026-10-11T06:30:00Z") // 08:30 in Madrid.

        assertEquals(
            Duration.ofHours(23).plusMinutes(30),
            delayUntilNextReminderWindow(now, madrid, LocalTime.of(8, 0)),
        )
    }

    @Test
    fun customTime_usesTheNextOccurrenceAcrossDaylightSavingFallback() {
        val now = Instant.parse("2026-10-24T07:00:00Z") // 09:00 CEST.

        assertEquals(
            Duration.ofHours(25),
            delayUntilNextReminderWindow(now, madrid, LocalTime.of(9, 0)),
        )
    }
}
