package com.miguelrodriguez19.stayleafy.ui.reminder.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelrodriguez19.stayleafy.model.CareStatusSummary
import com.miguelrodriguez19.stayleafy.ui.reminder.UiGateway
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class PlantCareSummaryViewModel @Inject constructor(
    gateway: UiGateway,
) : ViewModel() {
    val summaries: StateFlow<List<CareStatusSummary>> = gateway.observeCareSummaries()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
