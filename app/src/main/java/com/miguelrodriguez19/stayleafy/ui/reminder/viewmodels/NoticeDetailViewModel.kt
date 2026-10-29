package com.miguelrodriguez19.stayleafy.ui.reminder.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelrodriguez19.stayleafy.model.AppNotice
import com.miguelrodriguez19.stayleafy.ui.reminder.NoticeDetailUiState
import com.miguelrodriguez19.stayleafy.ui.reminder.UiGateway
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = NoticeDetailViewModel.Factory::class)
class NoticeDetailViewModel @AssistedInject constructor(
    @Assisted private val noticeId: String,
    private val gateway: UiGateway,
) : ViewModel() {
    private val reload = MutableStateFlow(0)

    val uiState: StateFlow<NoticeDetailUiState> = reload.flatMapLatest {
        gateway.observeNotice(noticeId)
            .map<AppNotice?, NoticeDetailUiState> { notice ->
                if (notice == null) NoticeDetailUiState.NotFound else NoticeDetailUiState.Content(notice)
            }
            .onStart { emit(NoticeDetailUiState.Loading) }
            .catch { emit(NoticeDetailUiState.Error) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NoticeDetailUiState.Loading)

    init { viewModelScope.launch { runCatching { gateway.markNoticeRead(noticeId) } } }

    fun retry() { reload.value += 1 }

    @AssistedFactory
    interface Factory {
        fun create(noticeId: String): NoticeDetailViewModel
    }
}
