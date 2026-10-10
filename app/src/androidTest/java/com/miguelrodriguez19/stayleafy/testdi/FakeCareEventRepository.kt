package com.miguelrodriguez19.stayleafy.testdi

import com.miguelrodriguez19.stayleafy.data.CareEventRepository
import com.miguelrodriguez19.stayleafy.model.CareEvent
import com.miguelrodriguez19.stayleafy.model.CareEventDraft
import com.miguelrodriguez19.stayleafy.model.CareType
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

@Singleton
class FakeCareEventRepository @Inject constructor() : CareEventRepository {
    private val events = MutableStateFlow<List<CareEvent>>(emptyList())

    @Volatile
    var failObservation: Boolean = false

    @Volatile
    var failMutation: Boolean = false

    val currentEvents: List<CareEvent>
        get() = events.value

    fun replaceEvents(items: List<CareEvent>) {
        events.value = items
    }

    fun reset() {
        failObservation = false
        failMutation = false
        events.value = emptyList()
    }

    override fun observeEvents(plantId: String): Flow<List<CareEvent>> =
        events.map { items ->
            check(!failObservation) { "care observation failed" }
            items.filter { it.plantId == plantId }
                .sortedWith(compareByDescending<CareEvent> { it.performedAt }.thenByDescending { it.id })
        }

    override fun observeEvent(plantId: String, eventId: String): Flow<CareEvent?> =
        events.map { items ->
            check(!failObservation) { "care observation failed" }
            items.firstOrNull { it.plantId == plantId && it.id == eventId }
        }

    override suspend fun createEvent(plantId: String, draft: CareEventDraft): String {
        check(!failMutation) { "care mutation failed" }
        val id = "care-${events.value.size + 1}"
        val event = CareEvent(
            id = id,
            plantId = plantId,
            careType = draft.careType,
            performedAt = draft.performedAt,
            note = draft.note?.trim()?.takeIf { it.isNotEmpty() },
            createdAt = draft.performedAt,
            updatedAt = draft.performedAt,
        )
        events.value = events.value + event
        return id
    }

    override suspend fun updateEvent(plantId: String, eventId: String, draft: CareEventDraft) {
        check(!failMutation) { "care mutation failed" }
        events.value = events.value.map { event ->
            if (event.plantId == plantId && event.id == eventId) {
                event.copy(
                    careType = draft.careType,
                    performedAt = draft.performedAt,
                    note = draft.note?.trim()?.takeIf { it.isNotEmpty() },
                    updatedAt = event.updatedAt + 1,
                )
            } else {
                event
            }
        }
    }

    override suspend fun deleteEvent(plantId: String, eventId: String) {
        check(!failMutation) { "care mutation failed" }
        events.value = events.value.filterNot { it.plantId == plantId && it.id == eventId }
    }

    override suspend fun deleteEventsByType(plantId: String, careType: CareType) {
        check(!failMutation) { "care mutation failed" }
        events.value = events.value.filterNot {
            it.plantId == plantId && it.careType == careType
        }
    }
}
