package com.miguelrodriguez19.stayleafy.data

import com.miguelrodriguez19.stayleafy.data.local.database.CareEventDao
import com.miguelrodriguez19.stayleafy.data.local.database.INTERVAL_SCHEDULE_TYPE
import com.miguelrodriguez19.stayleafy.data.local.database.PlantDao
import com.miguelrodriguez19.stayleafy.data.local.database.ReminderRuleChange
import com.miguelrodriguez19.stayleafy.data.local.database.ReminderRuleDao
import com.miguelrodriguez19.stayleafy.data.local.database.ReminderRuleEntity
import com.miguelrodriguez19.stayleafy.data.local.database.SEASONAL_SCHEDULE_TYPE
import com.miguelrodriguez19.stayleafy.model.AnnualDate
import com.miguelrodriguez19.stayleafy.model.CareDueStatus
import com.miguelrodriguez19.stayleafy.model.CareScheduleDraft
import com.miguelrodriguez19.stayleafy.model.CareScheduleMode
import com.miguelrodriguez19.stayleafy.model.CareStatusSummary
import com.miguelrodriguez19.stayleafy.model.CareType
import com.miguelrodriguez19.stayleafy.model.IntervalReminderConfiguration
import com.miguelrodriguez19.stayleafy.model.IntervalReminderDraft
import com.miguelrodriguez19.stayleafy.model.IntervalReminderRule
import com.miguelrodriguez19.stayleafy.model.ReminderDueSource
import com.miguelrodriguez19.stayleafy.model.SeasonalCareState
import com.miguelrodriguez19.stayleafy.model.SeasonalCareSummary
import com.miguelrodriguez19.stayleafy.model.SeasonalReminderRule
import com.miguelrodriguez19.stayleafy.model.currentOrNextSeasonalOccurrence
import com.miguelrodriguez19.stayleafy.platform.reminder.ReminderNotificationGateway
import javax.inject.Inject
import com.miguelrodriguez19.stayleafy.data.backup.DataOperationCoordinator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

interface ReminderRepository {
    fun observeRules(plantId: String): Flow<List<IntervalReminderRule>>

    fun observeSeasonalRules(plantId: String): Flow<List<SeasonalReminderRule>>

    fun observePlantSummaries(): Flow<List<CareStatusSummary>>

    fun observeSeasonalSummaries(plantId: String): Flow<List<SeasonalCareSummary>>

    fun observeHasEnabledRules(): Flow<Boolean>

    suspend fun saveRules(plantId: String, drafts: List<IntervalReminderDraft>)

    suspend fun saveSchedules(plantId: String, drafts: List<CareScheduleDraft>)
}

class DefaultReminderRepository @Inject constructor(
    private val reminderRuleDao: ReminderRuleDao,
    private val careEventDao: CareEventDao,
    private val plantDao: PlantDao,
    private val epochMillisProvider: EpochMillisProvider,
    private val zoneIdProvider: ZoneIdProvider,
    private val uuidProvider: UuidProvider,
    private val notificationGateway: ReminderNotificationGateway,
    private val operations: DataOperationCoordinator = DataOperationCoordinator(),
) : ReminderRepository {
    override fun observeRules(plantId: String): Flow<List<IntervalReminderRule>> =
        reminderRuleDao.observeRules(plantId).map { rules -> rules.map(ReminderRuleEntity::toIntervalModel) }

    override fun observeSeasonalRules(plantId: String): Flow<List<SeasonalReminderRule>> =
        reminderRuleDao.observeSeasonalRules(plantId).map { rules ->
            rules.map(ReminderRuleEntity::toSeasonalModel)
        }

    override fun observePlantSummaries(): Flow<List<CareStatusSummary>> =
        reminderRuleDao.observeEnabledRules().map { rules ->
            val today = epochDayAt(epochMillisProvider.now(), zoneIdProvider.currentZoneId())
            val upcomingEnd = Math.addExact(today, 7L)
            rules.map { rule ->
                val due = requireNotNull(rule.nextDueEpochDay)
                CareStatusSummary(
                    plantId = rule.plantId,
                    careType = CareType.valueOf(rule.careType),
                    nextDueEpochDay = due,
                    status = when {
                        due < today -> CareDueStatus.OVERDUE
                        due == today -> CareDueStatus.DUE_TODAY
                        due <= upcomingEnd -> CareDueStatus.UPCOMING
                        else -> CareDueStatus.LATER
                    },
                )
            }
        }

    override fun observeSeasonalSummaries(plantId: String): Flow<List<SeasonalCareSummary>> =
        combine(
            reminderRuleDao.observeSeasonalRules(plantId),
            careEventDao.observeEvents(plantId),
        ) { rules, events ->
            val zoneId = zoneIdProvider.currentZoneId()
            val today = epochDayAt(epochMillisProvider.now(), zoneId)
            rules.filter(ReminderRuleEntity::enabled).map { rule ->
                val occurrence = currentOrNextSeasonalOccurrence(
                    today,
                    rule.requireSeasonStart(),
                    rule.optionalSeasonEnd(),
                )
                val satisfied = today in occurrence && events.any { event ->
                    event.careType == rule.careType && epochDayAt(event.performedAt, zoneId) in occurrence
                }
                SeasonalCareSummary(
                    plantId = plantId,
                    careType = CareType.valueOf(rule.careType),
                    startEpochDay = occurrence.startEpochDay,
                    endEpochDay = occurrence.endEpochDay,
                    state = when {
                        today !in occurrence -> SeasonalCareState.UPCOMING
                        satisfied -> SeasonalCareState.SATISFIED
                        else -> SeasonalCareState.ACTIVE
                    },
                )
            }
        }

    override fun observeHasEnabledRules(): Flow<Boolean> = reminderRuleDao.observeHasEnabledRules()

    override suspend fun saveRules(plantId: String, drafts: List<IntervalReminderDraft>) {
        saveSchedules(
            plantId = plantId,
            drafts = drafts.map { draft ->
                val interval = draft.intervalDays?.takeIf { draft.dueSource != null }?.let { days ->
                    IntervalReminderConfiguration(days, requireNotNull(draft.dueSource))
                }
                CareScheduleDraft(
                    careType = draft.careType,
                    mode = if (draft.enabled) CareScheduleMode.INTERVAL else CareScheduleMode.DISABLED,
                    interval = interval,
                )
            },
        )
    }

    override suspend fun saveSchedules(plantId: String, drafts: List<CareScheduleDraft>): Unit = operations.withOperation {
        require(plantDao.getPlant(plantId) != null) { "Plant does not exist" }
        require(drafts.size == CareType.entries.size && drafts.map { it.careType }.toSet() == CareType.entries.toSet()) {
            "Exactly one schedule draft per care type is required"
        }

        val now = epochMillisProvider.now()
        val zoneId = zoneIdProvider.currentZoneId()
        val existing = reminderRuleDao.getAllRules(plantId).associateBy { rule ->
            CareType.valueOf(rule.careType) to rule.scheduleType
        }
        val changes = buildList {
            drafts.forEach { draft ->
                val intervalExisting = existing[draft.careType to INTERVAL_SCHEDULE_TYPE]
                val seasonalExisting = existing[draft.careType to SEASONAL_SCHEDULE_TYPE]
                require(draft.mode != CareScheduleMode.INTERVAL || draft.interval != null) {
                    "An active interval schedule requires interval configuration"
                }
                require(draft.mode != CareScheduleMode.SEASONAL || draft.seasonal != null) {
                    "An active seasonal schedule requires seasonal configuration"
                }

                draft.interval?.let { configuration ->
                    require(configuration.intervalDays > 0) { "intervalDays must be positive" }
                    val due = when (val source = configuration.dueSource) {
                        ReminderDueSource.LatestCare -> {
                            val performedAt = requireNotNull(
                                careEventDao.getLatestPerformedAt(plantId, draft.careType.name),
                            ) { "Latest care is unavailable for ${draft.careType.name}" }
                            Math.addExact(epochDayAt(performedAt, zoneId), configuration.intervalDays.toLong())
                        }
                        is ReminderDueSource.Date -> source.epochDay
                    }
                    add(
                        intervalChange(
                            existing = intervalExisting,
                            plantId = plantId,
                            careType = draft.careType,
                            enabled = draft.mode == CareScheduleMode.INTERVAL,
                            intervalDays = configuration.intervalDays,
                            due = due,
                            now = now,
                        ),
                    )
                } ?: intervalExisting?.let { rule ->
                    add(rule.enabledChange(draft.mode == CareScheduleMode.INTERVAL, now))
                }

                draft.seasonal?.let { configuration ->
                    add(
                        seasonalChange(
                            existing = seasonalExisting,
                            plantId = plantId,
                            careType = draft.careType,
                            enabled = draft.mode == CareScheduleMode.SEASONAL,
                            start = configuration.start,
                            end = configuration.end,
                            now = now,
                        ),
                    )
                } ?: seasonalExisting?.let { rule ->
                    add(rule.enabledChange(draft.mode == CareScheduleMode.SEASONAL, now))
                }
            }
        }

        val resolvedNoticeIds = reminderRuleDao.applyChanges(changes, now)
        resolvedNoticeIds.forEach { id -> runCatching { notificationGateway.cancel(id) } }
    }

    private fun intervalChange(
        existing: ReminderRuleEntity?,
        plantId: String,
        careType: CareType,
        enabled: Boolean,
        intervalDays: Int,
        due: Long,
        now: Long,
    ): ReminderRuleChange {
        val entity = ReminderRuleEntity(
            id = existing?.id ?: uuidProvider.newUuid(),
            plantId = plantId,
            careType = careType.name,
            scheduleType = INTERVAL_SCHEDULE_TYPE,
            enabled = enabled,
            intervalDays = intervalDays,
            nextDueEpochDay = due,
            seasonStartMonth = null,
            seasonStartDay = null,
            seasonEndMonth = null,
            seasonEndDay = null,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        return ReminderRuleChange(
            entity = entity,
            resolveCurrentOccurrence = existing != null && existing.enabled && (
                !enabled || existing.intervalDays != intervalDays || existing.nextDueEpochDay != due
                ),
        )
    }

    private fun seasonalChange(
        existing: ReminderRuleEntity?,
        plantId: String,
        careType: CareType,
        enabled: Boolean,
        start: AnnualDate,
        end: AnnualDate?,
        now: Long,
    ): ReminderRuleChange {
        val entity = ReminderRuleEntity(
            id = existing?.id ?: uuidProvider.newUuid(),
            plantId = plantId,
            careType = careType.name,
            scheduleType = SEASONAL_SCHEDULE_TYPE,
            enabled = enabled,
            intervalDays = null,
            nextDueEpochDay = null,
            seasonStartMonth = start.month,
            seasonStartDay = start.day,
            seasonEndMonth = end?.month,
            seasonEndDay = end?.day,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        return ReminderRuleChange(
            entity = entity,
            resolveCurrentOccurrence = existing != null && existing.enabled && (
                !enabled || existing.seasonStartMonth != start.month ||
                    existing.seasonStartDay != start.day || existing.seasonEndMonth != end?.month ||
                    existing.seasonEndDay != end?.day
                ),
        )
    }
}

private fun ReminderRuleEntity.enabledChange(enabled: Boolean, now: Long): ReminderRuleChange =
    ReminderRuleChange(
        entity = copy(enabled = enabled, updatedAt = now),
        resolveCurrentOccurrence = this.enabled && !enabled,
    )

private fun ReminderRuleEntity.toIntervalModel(): IntervalReminderRule = IntervalReminderRule(
    id = id,
    plantId = plantId,
    careType = CareType.valueOf(careType),
    enabled = enabled,
    intervalDays = requireNotNull(intervalDays),
    nextDueEpochDay = requireNotNull(nextDueEpochDay),
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun ReminderRuleEntity.toSeasonalModel(): SeasonalReminderRule = SeasonalReminderRule(
    id = id,
    plantId = plantId,
    careType = CareType.valueOf(careType),
    enabled = enabled,
    start = requireSeasonStart(),
    end = optionalSeasonEnd(),
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun ReminderRuleEntity.requireSeasonStart(): AnnualDate = AnnualDate(
    requireNotNull(seasonStartMonth),
    requireNotNull(seasonStartDay),
)

internal fun ReminderRuleEntity.optionalSeasonEnd(): AnnualDate? = seasonEndMonth?.let { month ->
    AnnualDate(month, requireNotNull(seasonEndDay))
}
