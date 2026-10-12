package com.miguelrodriguez19.stayleafy.data

import com.miguelrodriguez19.stayleafy.data.local.database.CareEventDao
import com.miguelrodriguez19.stayleafy.data.local.database.INTERVAL_SCHEDULE_TYPE
import com.miguelrodriguez19.stayleafy.data.local.database.PlantDao
import com.miguelrodriguez19.stayleafy.data.local.database.ReminderRuleChange
import com.miguelrodriguez19.stayleafy.data.local.database.ReminderRuleDao
import com.miguelrodriguez19.stayleafy.data.local.database.ReminderRuleEntity
import com.miguelrodriguez19.stayleafy.model.CareDueStatus
import com.miguelrodriguez19.stayleafy.model.CareStatusSummary
import com.miguelrodriguez19.stayleafy.model.CareType
import com.miguelrodriguez19.stayleafy.model.IntervalReminderDraft
import com.miguelrodriguez19.stayleafy.model.IntervalReminderRule
import com.miguelrodriguez19.stayleafy.model.ReminderDueSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

interface ReminderRepository {
    fun observeRules(plantId: String): Flow<List<IntervalReminderRule>>

    fun observePlantSummaries(): Flow<List<CareStatusSummary>>

    suspend fun saveRules(plantId: String, drafts: List<IntervalReminderDraft>)
}

class DefaultReminderRepository @Inject constructor(
    private val reminderRuleDao: ReminderRuleDao,
    private val careEventDao: CareEventDao,
    private val plantDao: PlantDao,
    private val epochMillisProvider: EpochMillisProvider,
    private val zoneIdProvider: ZoneIdProvider,
    private val uuidProvider: UuidProvider,
) : ReminderRepository {
    override fun observeRules(plantId: String): Flow<List<IntervalReminderRule>> =
        reminderRuleDao.observeRules(plantId).map { rules -> rules.map(ReminderRuleEntity::toModel) }

    override fun observePlantSummaries(): Flow<List<CareStatusSummary>> =
        reminderRuleDao.observeEnabledRules().map { rules ->
            val today = epochDayAt(epochMillisProvider.now(), zoneIdProvider.currentZoneId())
            val upcomingEnd = Math.addExact(today, 7L)
            rules.map { rule ->
                CareStatusSummary(
                    plantId = rule.plantId,
                    careType = CareType.valueOf(rule.careType),
                    nextDueEpochDay = rule.nextDueEpochDay,
                    status = when {
                        rule.nextDueEpochDay < today -> CareDueStatus.OVERDUE
                        rule.nextDueEpochDay == today -> CareDueStatus.DUE_TODAY
                        rule.nextDueEpochDay <= upcomingEnd -> CareDueStatus.UPCOMING
                        else -> CareDueStatus.LATER
                    },
                )
            }
        }

    override suspend fun saveRules(plantId: String, drafts: List<IntervalReminderDraft>) {
        require(plantDao.getPlant(plantId) != null) { "Plant does not exist" }
        require(drafts.size == CareType.entries.size && drafts.map { it.careType }.toSet() == CareType.entries.toSet()) {
            "Exactly one reminder draft per care type is required"
        }

        val now = epochMillisProvider.now()
        val zoneId = zoneIdProvider.currentZoneId()
        val existingByType = reminderRuleDao.getRules(plantId).associateBy { CareType.valueOf(it.careType) }
        val changes = drafts.mapNotNull { draft ->
            val existing = existingByType[draft.careType]
            val hasCompleteConfiguration = draft.intervalDays != null && draft.dueSource != null
            require(
                (draft.intervalDays == null) == (draft.dueSource == null),
            ) { "Interval and due source must be provided together" }

            if (!draft.enabled && existing == null && !hasCompleteConfiguration) {
                return@mapNotNull null
            }
            if (draft.enabled) {
                require(hasCompleteConfiguration) { "Enabled reminders require an interval and due source" }
            }

            val intervalDays = draft.intervalDays ?: existing?.intervalDays
            require(intervalDays != null && intervalDays > 0) { "intervalDays must be positive" }
            val nextDueEpochDay = when (val source = draft.dueSource) {
                ReminderDueSource.LatestCare -> {
                    val performedAt = requireNotNull(
                        careEventDao.getLatestPerformedAt(plantId, draft.careType.name),
                    ) { "Latest care is unavailable for ${draft.careType.name}" }
                    Math.addExact(epochDayAt(performedAt, zoneId), intervalDays.toLong())
                }

                is ReminderDueSource.Date -> source.epochDay
                null -> requireNotNull(existing?.nextDueEpochDay) { "A due source is required" }
            }
            val entity = ReminderRuleEntity(
                id = existing?.id ?: uuidProvider.newUuid(),
                plantId = plantId,
                careType = draft.careType.name,
                scheduleType = INTERVAL_SCHEDULE_TYPE,
                enabled = draft.enabled,
                intervalDays = intervalDays,
                nextDueEpochDay = nextDueEpochDay,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            )
            ReminderRuleChange(
                entity = entity,
                resolveCurrentOccurrence = existing != null && (
                    existing.nextDueEpochDay != nextDueEpochDay ||
                        existing.intervalDays != intervalDays ||
                        existing.enabled && !draft.enabled
                    ),
            )
        }

        reminderRuleDao.applyChanges(changes, now)
    }
}

private fun ReminderRuleEntity.toModel(): IntervalReminderRule = IntervalReminderRule(
    id = id,
    plantId = plantId,
    careType = CareType.valueOf(careType),
    enabled = enabled,
    intervalDays = intervalDays,
    nextDueEpochDay = nextDueEpochDay,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
