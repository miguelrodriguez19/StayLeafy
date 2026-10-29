package com.miguelrodriguez19.stayleafy.ui.reminder.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelrodriguez19.stayleafy.model.AppNotice
import com.miguelrodriguez19.stayleafy.ui.reminder.NoticeInboxUiState
import com.miguelrodriguez19.stayleafy.ui.reminder.NoticeListUiState
import com.miguelrodriguez19.stayleafy.ui.reminder.UiGateway
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject


private sealed interface NoticeSourceState {
    data object Loading : NoticeSourceState
    data object Error : NoticeSourceState
    data class Content(val notices: List<AppNotice>) : NoticeSourceState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NoticeListViewModel @Inject constructor(
    private val gateway: UiGateway,
) : ViewModel() {
    private val reload = MutableStateFlow(0)
    private val warningReload = MutableStateFlow(0)
    private val unreadOnly = MutableStateFlow(true)

    private val noticesState = reload.flatMapLatest {
        gateway.observeNotices()
            .map<List<AppNotice>, NoticeSourceState> { notices ->
                NoticeSourceState.Content(notices)
            }
            .onStart { emit(NoticeSourceState.Loading) }
            .catch { emit(NoticeSourceState.Error) }
    }

    val uiState: StateFlow<NoticeInboxUiState> =
        combine(noticesState, unreadOnly) { source, showUnreadOnly ->
            when (source) {
                NoticeSourceState.Loading -> NoticeInboxUiState(
                    unreadOnly = showUnreadOnly,
                    listState = NoticeListUiState.Loading,
                )

                NoticeSourceState.Error -> NoticeInboxUiState(
                    unreadOnly = showUnreadOnly,
                    listState = NoticeListUiState.Error,
                )

                is NoticeSourceState.Content -> {
                    val sorted = source.notices
                        .sortedWith(compareByDescending<AppNotice> { it.createdAt }.thenByDescending { it.id })
                    val visible =
                        if (showUnreadOnly) sorted.filter { it.readAt == null } else sorted
                    NoticeInboxUiState(
                        unreadOnly = showUnreadOnly,
                        listState = if (visible.isEmpty()) {
                            NoticeListUiState.Empty
                        } else {
                            NoticeListUiState.Content(visible)
                        },
                        unreadCount = source.notices.count { it.readAt == null },
                    )
                }
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            NoticeInboxUiState(),
        )

    val notificationWarning: StateFlow<Boolean> = warningReload.flatMapLatest {
        gateway.observeNotificationWarning().catch { emit(false) }
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun retry() {
        reload.value += 1
    }

    fun setUnreadOnly(unreadOnly: Boolean) {
        this.unreadOnly.value = unreadOnly
    }

    fun refreshNotificationWarning() {
        warningReload.value += 1
    }

    fun markAllRead() {
        viewModelScope.launch { runCatching { gateway.markAllNoticesRead() } }
    }
}
