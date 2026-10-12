package com.miguelrodriguez19.stayleafy.model

data class IntervalReminderRule(
    val id: String,
    val plantId: String,
    val careType: CareType,
    val enabled: Boolean,
    val intervalDays: Int,
    val nextDueEpochDay: Long,
    val createdAt: Long,
    val updatedAt: Long,
)

data class IntervalReminderDraft(
    val careType: CareType,
    val enabled: Boolean,
    val intervalDays: Int? = null,
    val dueSource: ReminderDueSource? = null,
)

sealed interface ReminderDueSource {
    data object LatestCare : ReminderDueSource

    data class Date(val epochDay: Long) : ReminderDueSource
}

enum class CareDueStatus {
    OVERDUE,
    DUE_TODAY,
    UPCOMING,
    LATER,
}

data class CareStatusSummary(
    val plantId: String,
    val careType: CareType,
    val nextDueEpochDay: Long,
    val status: CareDueStatus,
)
