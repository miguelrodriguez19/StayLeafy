package com.miguelrodriguez19.stayleafy.data

import com.miguelrodriguez19.stayleafy.data.local.database.NoticeDao
import com.miguelrodriguez19.stayleafy.data.local.database.NoticeEntity
import com.miguelrodriguez19.stayleafy.data.local.database.NoticeRecord
import com.miguelrodriguez19.stayleafy.data.local.database.ReminderRuleEntity
import com.miguelrodriguez19.stayleafy.model.NoticeDeliveryState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DefaultNoticeRepositoryTest {
    @Test
    fun createPendingNotice_deduplicatesRuleOccurrence() = runTest {
        val dao = RecordingNoticeDao()
        val repository = repository(dao)

        assertEquals("notice-1", repository.createPendingNotice("rule-1"))
        assertNull(repository.createPendingNotice("rule-1"))
        assertEquals("interval:rule-1:21000", dao.notices.value.single().occurrenceKey)
        assertEquals(NoticeDeliveryState.PENDING.name, dao.notices.value.single().deliveryState)
    }

    @Test
    fun deliveryAndReadMutationsUseDeterministicTimestamp() = runTest {
        val dao = RecordingNoticeDao()
        val repository = repository(dao)
        val id = requireNotNull(repository.createPendingNotice("rule-1"))

        repository.setDeliveryState(id, NoticeDeliveryState.DELIVERED)
        repository.markRead(id)

        assertEquals(NoticeDeliveryState.DELIVERED.name, dao.notices.value.single().deliveryState)
        assertEquals(5_000L, dao.notices.value.single().readAt)
    }

    private fun repository(dao: RecordingNoticeDao) = DefaultNoticeRepository(
        noticeDao = dao,
        epochMillisProvider = object : EpochMillisProvider { override fun now() = 5_000L },
        uuidProvider = object : UuidProvider { override fun newUuid() = "notice-1" },
    )
}

private class RecordingNoticeDao : NoticeDao {
    val notices = MutableStateFlow<List<NoticeEntity>>(emptyList())
    private val rule = ReminderRuleEntity(
        id = "rule-1",
        plantId = "plant-1",
        careType = "WATERING",
        scheduleType = "INTERVAL",
        enabled = true,
        intervalDays = 7,
        nextDueEpochDay = 21_000L,
        createdAt = 1L,
        updatedAt = 1L,
    )

    override fun observeNotices(): Flow<List<NoticeRecord>> = flowOf(emptyList())
    override fun observeNotice(noticeId: String): Flow<NoticeRecord?> = flowOf(null)
    override fun observeUnreadCount(): Flow<Int> = flowOf(notices.value.count { it.readAt == null })
    override suspend fun getPendingNotices(): List<NoticeRecord> = emptyList()
    override suspend fun getEnabledRule(ruleId: String): ReminderRuleEntity? =
        rule.takeIf { it.id == ruleId && it.enabled }
    override suspend fun insertNotice(notice: NoticeEntity): Long {
        if (notices.value.any { it.occurrenceKey == notice.occurrenceKey }) return -1L
        notices.value = notices.value + notice
        return 1L
    }
    override suspend fun markRead(noticeId: String, readAt: Long): Int {
        val found = notices.value.count { it.id == noticeId && it.readAt == null }
        notices.value = notices.value.map { if (it.id == noticeId && it.readAt == null) it.copy(readAt = readAt) else it }
        return found
    }
    override suspend fun markAllRead(readAt: Long): Int {
        val found = notices.value.count { it.readAt == null }
        notices.value = notices.value.map { if (it.readAt == null) it.copy(readAt = readAt) else it }
        return found
    }
    override suspend fun updateDeliveryState(noticeId: String, deliveryState: String): Int {
        val found = notices.value.count { it.id == noticeId }
        notices.value = notices.value.map { if (it.id == noticeId) it.copy(deliveryState = deliveryState) else it }
        return found
    }
}
