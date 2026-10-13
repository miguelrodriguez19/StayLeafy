package com.miguelrodriguez19.stayleafy.model

data class AppNotice(
    val id: String,
    val ruleId: String,
    val plantId: String,
    val plantName: String,
    val careType: CareType,
    val scheduleType: ReminderScheduleType = ReminderScheduleType.INTERVAL,
    val dueEpochDay: Long,
    val createdAt: Long,
    val readAt: Long?,
    val deliveryState: NoticeDeliveryState,
)

enum class NoticeDeliveryState {
    PENDING,
    DELIVERED,
    SUPPRESSED,
}
