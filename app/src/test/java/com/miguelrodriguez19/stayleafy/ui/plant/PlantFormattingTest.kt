package com.miguelrodriguez19.stayleafy.ui.plant

import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class PlantFormattingTest {

    @Test
    fun acquisitionDatePicker_defaultsToTodayWhenPlantHasNoDate() {
        val today = LocalDate.of(2026, 10, 10)

        assertEquals(
            today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            initialAcquisitionDatePickerMillis(
                acquiredEpochDay = null,
                todayEpochDay = today.toEpochDay(),
            ),
        )
    }

    @Test
    fun acquisitionDatePicker_preservesExistingDate() {
        val acquired = LocalDate.of(2024, 4, 3)

        assertEquals(
            acquired.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            initialAcquisitionDatePickerMillis(
                acquiredEpochDay = acquired.toEpochDay(),
                todayEpochDay = LocalDate.of(2026, 10, 10).toEpochDay(),
            ),
        )
    }
}
