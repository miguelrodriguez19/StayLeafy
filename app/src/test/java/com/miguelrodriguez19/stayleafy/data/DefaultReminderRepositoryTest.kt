package com.miguelrodriguez19.stayleafy.data

import com.miguelrodriguez19.stayleafy.data.local.database.CareEventDao
import com.miguelrodriguez19.stayleafy.data.local.database.CareEventEntity
import com.miguelrodriguez19.stayleafy.data.local.database.PlantDao
import com.miguelrodriguez19.stayleafy.data.local.database.PlantEntity
import com.miguelrodriguez19.stayleafy.data.local.database.PlantNoteEntity
import com.miguelrodriguez19.stayleafy.data.local.database.ReminderRuleChange
import com.miguelrodriguez19.stayleafy.data.local.database.ReminderRuleDao
import com.miguelrodriguez19.stayleafy.data.local.database.ReminderRuleEntity
import com.miguelrodriguez19.stayleafy.model.CareDueStatus
import com.miguelrodriguez19.stayleafy.model.CareType
import com.miguelrodriguez19.stayleafy.model.IntervalReminderDraft
import com.miguelrodriguez19.stayleafy.model.ReminderDueSource
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultReminderRepositoryTest {
    private val now = Instant.parse("2026-10-11T10:00:00Z").toEpochMilli()

    @Test
    fun saveRules_resolvesExplicitSourcesAndSkipsUnconfiguredDisabledRule() = runTest {
        val latestWatering = Instant.parse("2026-10-01T23:30:00Z").toEpochMilli()
        val ruleDao = RecordingReminderRuleDao()
        val repository = repository(
            ruleDao = ruleDao,
            careDao = ReminderCareEventDao(mapOf(CareType.WATERING.name to latestWatering)),
            uuids = listOf("watering-rule", "pruning-rule"),
        )

        repository.saveRules(
            plantId = PLANT_ID,
            drafts = listOf(
                IntervalReminderDraft(CareType.WATERING, true, 7, ReminderDueSource.LatestCare),
                IntervalReminderDraft(CareType.PRUNING, true, 30, ReminderDueSource.Date(21_000L)),
                IntervalReminderDraft(CareType.FERTILIZING, false),
            ),
        )

        val rules = ruleDao.rules.value.associateBy(ReminderRuleEntity::careType)
        assertEquals(2, rules.size)
        assertEquals(
            epochDayAt(latestWatering, ZoneOffset.UTC) + 7,
            rules.getValue(CareType.WATERING.name).nextDueEpochDay,
        )
        assertEquals(21_000L, rules.getValue(CareType.PRUNING.name).nextDueEpochDay)
        assertEquals(now, rules.getValue(CareType.WATERING.name).createdAt)
        assertTrue(ruleDao.appliedAtomically)
    }

    @Test
    fun saveRules_missingLatestCareRejectsWholeWrite() = runTest {
        val ruleDao = RecordingReminderRuleDao()
        val repository = repository(ruleDao, ReminderCareEventDao(), listOf("unused"))

        val error = runCatching {
            repository.saveRules(
                PLANT_ID,
                listOf(
                    IntervalReminderDraft(CareType.WATERING, true, 7, ReminderDueSource.Date(20_000L)),
                    IntervalReminderDraft(CareType.PRUNING, true, 14, ReminderDueSource.LatestCare),
                    IntervalReminderDraft(CareType.FERTILIZING, false),
                ),
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(ruleDao.rules.value.isEmpty())
        assertEquals(false, ruleDao.appliedAtomically)
    }

    @Test
    fun saveRules_reschedulingAndDisablingResolveOnlyChangedOccurrences() = runTest {
        val existing = listOf(
            rule("water", CareType.WATERING, true, 20_100L),
            rule("prune", CareType.PRUNING, true, 20_200L),
            rule("feed", CareType.FERTILIZING, true, 20_300L),
        )
        val ruleDao = RecordingReminderRuleDao(existing)
        val repository = repository(ruleDao, ReminderCareEventDao(), emptyList())

        repository.saveRules(
            PLANT_ID,
            listOf(
                IntervalReminderDraft(CareType.WATERING, false),
                IntervalReminderDraft(CareType.PRUNING, true, 14, ReminderDueSource.Date(20_250L)),
                IntervalReminderDraft(CareType.FERTILIZING, true, 14, ReminderDueSource.Date(20_300L)),
            ),
        )

        assertEquals(setOf("water", "prune"), ruleDao.resolvedRuleIds.toSet())
        assertEquals(false, ruleDao.rules.value.first { it.id == "water" }.enabled)
        assertEquals(20_250L, ruleDao.rules.value.first { it.id == "prune" }.nextDueEpochDay)
    }

    @Test
    fun observePlantSummaries_usesInjectedClockAndZone() = runTest {
        val today = epochDayAt(now, ZoneOffset.UTC)
        val ruleDao = RecordingReminderRuleDao(
            listOf(
                rule("overdue", CareType.WATERING, true, today - 1),
                rule("today", CareType.PRUNING, true, today),
                rule("upcoming", CareType.FERTILIZING, true, today + 7),
            ),
        )
        val repository = repository(ruleDao, ReminderCareEventDao(), emptyList())

        val statuses = repository.observePlantSummaries().first().associateBy { it.careType }

        assertEquals(CareDueStatus.OVERDUE, statuses.getValue(CareType.WATERING).status)
        assertEquals(CareDueStatus.DUE_TODAY, statuses.getValue(CareType.PRUNING).status)
        assertEquals(CareDueStatus.UPCOMING, statuses.getValue(CareType.FERTILIZING).status)
    }

    private fun repository(
        ruleDao: RecordingReminderRuleDao,
        careDao: CareEventDao,
        uuids: List<String>,
    ) = DefaultReminderRepository(
        reminderRuleDao = ruleDao,
        careEventDao = careDao,
        plantDao = ReminderPlantDao,
        epochMillisProvider = object : EpochMillisProvider { override fun now() = now },
        zoneIdProvider = object : ZoneIdProvider { override fun currentZoneId(): ZoneId = ZoneOffset.UTC },
        uuidProvider = SequenceReminderUuidProvider(uuids),
    )

    private fun rule(id: String, type: CareType, enabled: Boolean, due: Long) = ReminderRuleEntity(
        id = id,
        plantId = PLANT_ID,
        careType = type.name,
        scheduleType = "INTERVAL",
        enabled = enabled,
        intervalDays = 14,
        nextDueEpochDay = due,
        createdAt = 1_000L,
        updatedAt = 1_000L,
    )
}

private const val PLANT_ID = "plant-1"

private class SequenceReminderUuidProvider(values: List<String>) : UuidProvider {
    private val values = ArrayDeque(values)
    override fun newUuid(): String = values.removeFirst()
}

private class RecordingReminderRuleDao(initial: List<ReminderRuleEntity> = emptyList()) : ReminderRuleDao {
    val rules = MutableStateFlow(initial)
    val resolvedRuleIds = mutableListOf<String>()
    var appliedAtomically = false

    override fun observeRules(plantId: String): Flow<List<ReminderRuleEntity>> =
        flowOf(rules.value.filter { it.plantId == plantId })

    override suspend fun getRules(plantId: String): List<ReminderRuleEntity> =
        rules.value.filter { it.plantId == plantId }

    override suspend fun getRule(ruleId: String): ReminderRuleEntity? =
        rules.value.firstOrNull { it.id == ruleId }

    override fun observeEnabledRules(): Flow<List<ReminderRuleEntity>> =
        flowOf(rules.value.filter(ReminderRuleEntity::enabled))

    override suspend fun getDueRules(todayEpochDay: Long): List<ReminderRuleEntity> =
        rules.value.filter { it.enabled && it.nextDueEpochDay <= todayEpochDay }

    override suspend fun upsertRule(rule: ReminderRuleEntity) {
        rules.value = rules.value.filterNot { it.id == rule.id } + rule
    }

    override suspend fun markUnreadNoticesRead(ruleId: String, readAt: Long): Int {
        resolvedRuleIds += ruleId
        return 1
    }

    override suspend fun applyChanges(changes: List<ReminderRuleChange>, changedAt: Long) {
        appliedAtomically = true
        super.applyChanges(changes, changedAt)
    }
}

private class ReminderCareEventDao(
    private val latest: Map<String, Long> = emptyMap(),
) : CareEventDao {
    override fun observeEvents(plantId: String): Flow<List<CareEventEntity>> = flowOf(emptyList())
    override fun observeEvent(plantId: String, eventId: String): Flow<CareEventEntity?> = flowOf(null)
    override suspend fun insertEvent(event: CareEventEntity) = Unit
    override suspend fun getLatestPerformedAt(plantId: String, careType: String): Long? = latest[careType]
    override suspend fun getIntervalRule(plantId: String, careType: String): ReminderRuleEntity? = null
    override suspend fun updateRuleDueDate(ruleId: String, nextDueEpochDay: Long, updatedAt: Long) = 0
    override suspend fun markRuleNoticesRead(ruleId: String, readAt: Long) = 0
    override suspend fun updateEvent(
        plantId: String,
        eventId: String,
        careType: String,
        performedAt: Long,
        note: String?,
        updatedAt: Long,
    ) = 0
    override suspend fun deleteEvent(plantId: String, eventId: String) = 0
    override suspend fun deleteEventsByType(plantId: String, careType: String) = 0
}

private object ReminderPlantDao : PlantDao {
    private val plant = PlantEntity(
        id = PLANT_ID,
        name = "Ficus",
        nameSearch = "ficus",
        scientificName = null,
        scientificNameSearch = "",
        location = null,
        acquiredEpochDay = null,
        description = null,
        mainPhotoPath = null,
        isFavorite = false,
        createdAt = 1L,
        updatedAt = 1L,
    )

    override fun observePlants(
        escapedQuery: String,
        favoritesOnly: Boolean,
        upcomingOnly: Boolean,
        overdueOnly: Boolean,
        todayEpochDay: Long,
        upcomingEndEpochDay: Long,
    ) = flowOf(listOf(plant))
    override fun observePlant(id: String) = flowOf(plant.takeIf { it.id == id })
    override suspend fun getPlant(id: String) = plant.takeIf { it.id == id }
    override suspend fun insertPlant(plant: PlantEntity) = Unit
    override suspend fun insertInitialNotes(notes: List<PlantNoteEntity>) = Unit
    override suspend fun updatePlant(
        id: String,
        name: String,
        nameSearch: String,
        scientificName: String?,
        scientificNameSearch: String,
        location: String?,
        acquiredEpochDay: Long?,
        description: String?,
        updatedAt: Long,
    ) = Unit
    override suspend fun setFavorite(id: String, favorite: Boolean, updatedAt: Long) = Unit
    override suspend fun updateMainPhotoPath(id: String, relativePath: String?, updatedAt: Long) = 0
    override suspend fun getReferencedPhotoPaths(): List<String> = emptyList()
    override suspend fun deletePlant(id: String) = Unit
}
