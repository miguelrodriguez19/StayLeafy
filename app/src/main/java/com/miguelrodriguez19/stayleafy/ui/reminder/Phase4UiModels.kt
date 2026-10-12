package com.miguelrodriguez19.stayleafy.ui.reminder

import androidx.compose.runtime.Immutable
import com.miguelrodriguez19.stayleafy.model.AppNotice
import com.miguelrodriguez19.stayleafy.model.CareStatusSummary
import com.miguelrodriguez19.stayleafy.model.CareType

enum class ReminderDueSourceChoice {
    LatestCare,
    Date,
}

@Immutable
data class CareRuleForm(
    val careType: CareType,
    val enabled: Boolean = false,
    val intervalDays: String = "",
    val dueSource: ReminderDueSourceChoice? = null,
    val selectedEpochDay: Long? = null,
    val intervalError: Boolean = false,
    val sourceError: Boolean = false,
)

sealed interface CareReminderUiState {
    data object Loading : CareReminderUiState
    data object Error : CareReminderUiState
    data class Content(val summaries: List<CareStatusSummary>) : CareReminderUiState
}

sealed interface CareConfigurationUiState {
    data object Loading : CareConfigurationUiState
    data object Error : CareConfigurationUiState
    data object NotFound : CareConfigurationUiState

    data class Content(
        val plantName: String,
        val forms: List<CareRuleForm>,
        val latestCareEpochDays: Map<CareType, Long>,
        val isSaving: Boolean = false,
        val saveError: Boolean = false,
        val showPermissionEducation: Boolean = false,
        val saved: Boolean = false,
    ) : CareConfigurationUiState
}

sealed interface NoticeListUiState {
    data object Loading : NoticeListUiState
    data object Error : NoticeListUiState
    data object Empty : NoticeListUiState
    data class Content(val notices: List<AppNotice>) : NoticeListUiState
}

sealed interface NoticeDetailUiState {
    data object Loading : NoticeDetailUiState
    data object Error : NoticeDetailUiState
    data object NotFound : NoticeDetailUiState
    data class Content(val notice: AppNotice) : NoticeDetailUiState
}

internal val AppNotice.isRead: Boolean get() = readAt != null
