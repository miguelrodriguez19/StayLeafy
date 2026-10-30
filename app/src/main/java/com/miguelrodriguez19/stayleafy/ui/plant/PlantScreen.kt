package com.miguelrodriguez19.stayleafy.ui.plant

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miguelrodriguez19.stayleafy.R
import com.miguelrodriguez19.stayleafy.model.CareDueStatus
import com.miguelrodriguez19.stayleafy.model.CareStatusSummary
import com.miguelrodriguez19.stayleafy.model.CareType
import com.miguelrodriguez19.stayleafy.model.Plant
import com.miguelrodriguez19.stayleafy.ui.components.LeafyAddFab
import com.miguelrodriguez19.stayleafy.ui.components.LeafyEmptyState
import com.miguelrodriguez19.stayleafy.ui.components.LeafyPrimaryButton
import com.miguelrodriguez19.stayleafy.ui.components.SettingsSectionCard
import com.miguelrodriguez19.stayleafy.ui.components.SettingsSegment
import com.miguelrodriguez19.stayleafy.ui.components.SettingsSegmentedControl
import com.miguelrodriguez19.stayleafy.ui.components.leafyTopAppBarColors
import com.miguelrodriguez19.stayleafy.ui.reminder.viewmodels.PlantCareSummaryViewModel
import com.miguelrodriguez19.stayleafy.ui.reminder.localizedName
import com.miguelrodriguez19.stayleafy.ui.theme.StayLeafyTheme
import com.miguelrodriguez19.stayleafy.ui.theme.leafyColors
import com.miguelrodriguez19.stayleafy.ui.theme.leafyLayoutTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlantListRoute(
    onAddPlant: () -> Unit,
    onSettingsClick: () -> Unit = {},
    onPlantClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlantViewModel = hiltViewModel(),
    careSummaryViewModel: PlantCareSummaryViewModel = hiltViewModel(),
    bulkCareViewModel: BulkCareViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val mutationError by viewModel.mutationError.collectAsStateWithLifecycle()
    val careSummaries by careSummaryViewModel.summaries.collectAsStateWithLifecycle()
    val bulkCare by bulkCareViewModel.uiState.collectAsStateWithLifecycle()

    BackHandler(enabled = bulkCare.isSelectionMode) {
        if (bulkCare.isConfirming) bulkCareViewModel.cancelConfirmation()
        else if (bulkCare.isFormOpen) bulkCareViewModel.returnToSelection()
        else bulkCareViewModel.exitSelection()
    }

    PlantListScreen(
        state = state,
        filterQuery = filter.query,
        favoritesOnly = filter.favoritesOnly,
        mutationError = mutationError,
        onQueryChange = viewModel::updateQuery,
        onFavoritesOnlyChange = viewModel::updateFavoritesOnly,
        onClearFilters = viewModel::clearFilters,
        onRetry = viewModel::retry,
        onAddPlant = onAddPlant,
        onSettingsClick = onSettingsClick,
        onPlantClick = onPlantClick,
        onFavoriteClick = viewModel::setFavorite,
        onDismissMutationError = viewModel::clearMutationError,
        upcomingOnly = filter.upcomingOnly,
        overdueOnly = filter.overdueOnly,
        onUpcomingOnlyChange = viewModel::updateUpcomingOnly,
        onOverdueOnlyChange = viewModel::updateOverdueOnly,
        careSummaries = careSummaries.groupBy(CareStatusSummary::plantId),
        bulkCare = bulkCare,
        onStartBulkCare = bulkCareViewModel::startSelection,
        onToggleBulkCarePlant = bulkCareViewModel::togglePlant,
        onSelectVisiblePlants = bulkCareViewModel::selectVisiblePlants,
        onClearBulkCareSelection = bulkCareViewModel::clearSelection,
        onExitBulkCare = bulkCareViewModel::exitSelection,
        onContinueBulkCare = bulkCareViewModel::continueToForm,
        onOpenBulkCareConfirmation = bulkCareViewModel::openConfirmation,
        onCancelBulkCareConfirmation = bulkCareViewModel::cancelConfirmation,
        onBackFromBulkCareForm = bulkCareViewModel::returnToSelection,
        onBulkCareTypeChange = bulkCareViewModel::setCareType,
        onBulkCareDateChange = bulkCareViewModel::setPerformedEpochDay,
        onSubmitBulkCare = bulkCareViewModel::confirmSubmission,
        onDismissBulkCareCompletion = bulkCareViewModel::dismissCompletion,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlantListScreen(
    state: PlantListUiState,
    filterQuery: String,
    favoritesOnly: Boolean,
    mutationError: UiErrorCategory?,
    onQueryChange: (String) -> Unit,
    onFavoritesOnlyChange: (Boolean) -> Unit,
    onClearFilters: () -> Unit,
    onRetry: () -> Unit,
    onAddPlant: () -> Unit,
    onSettingsClick: () -> Unit = {},
    onPlantClick: (String) -> Unit,
    onFavoriteClick: (String, Boolean) -> Unit,
    onDismissMutationError: () -> Unit,
    modifier: Modifier = Modifier,
    upcomingOnly: Boolean = false,
    overdueOnly: Boolean = false,
    onUpcomingOnlyChange: (Boolean) -> Unit = {},
    onOverdueOnlyChange: (Boolean) -> Unit = {},
    careSummaries: Map<String, List<CareStatusSummary>> = emptyMap(),
    listState: LazyListState = rememberLazyListState(),
    bulkCare: BulkCareUiState = BulkCareUiState(),
    onStartBulkCare: () -> Unit = {},
    onToggleBulkCarePlant: (Plant) -> Unit = {},
    onSelectVisiblePlants: (List<Plant>) -> Unit = {},
    onClearBulkCareSelection: () -> Unit = {},
    onExitBulkCare: () -> Unit = {},
    onContinueBulkCare: () -> Unit = {},
    onOpenBulkCareConfirmation: () -> Unit = {},
    onCancelBulkCareConfirmation: () -> Unit = {},
    onBackFromBulkCareForm: () -> Unit = {},
    onBulkCareTypeChange: (CareType) -> Unit = {},
    onBulkCareDateChange: (Long) -> Unit = {},
    onSubmitBulkCare: () -> Unit = {},
    onDismissBulkCareCompletion: () -> Unit = {},
) {
    // Keep the state above the content branch so Loading and Nav3 entry restoration retain its numeric position.
    val filterKey = PlantListFilterKey(
        query = filterQuery,
        favoritesOnly = favoritesOnly,
        upcomingOnly = upcomingOnly,
        overdueOnly = overdueOnly,
    )
    var previousFilterKey by remember { mutableStateOf(filterKey) }
    var previousState by remember { mutableStateOf<PlantListUiState?>(null) }
    var previousPlantIds by remember { mutableStateOf<List<String>?>(null) }
    var pendingPosition by remember { mutableStateOf<PlantListPosition?>(null) }

    // SideEffect runs before the next lazy measure, so this request replaces key-based anchoring after a reorder.
    SideEffect {
        val filterChanged = previousFilterKey != filterKey
        val stateChanged = previousState != state
        if (filterChanged) {
            previousFilterKey = filterKey
            pendingPosition = null
            listState.requestScrollToItem(0)
        }

        when (val currentState = state) {
            PlantListUiState.Loading -> {
                if (!filterChanged && stateChanged && previousState is PlantListUiState.Content && pendingPosition == null) {
                    pendingPosition = listState.currentPosition()
                }
            }

            is PlantListUiState.Content -> {
                val currentPlantIds = if (stateChanged) currentState.plants.map(Plant::id) else previousPlantIds
                val plantOrderChanged = previousPlantIds != null && currentPlantIds != null &&
                    previousPlantIds != currentPlantIds
                if (!filterChanged && (pendingPosition != null || plantOrderChanged)) {
                    val position = pendingPosition ?: listState.currentPosition()
                    listState.requestScrollToItem(
                        index = position.index.coerceAtMost(currentState.plants.lastIndex),
                        scrollOffset = position.offset,
                    )
                    pendingPosition = null
                } else if (!filterChanged && listState.firstVisibleItemIndex > currentState.plants.lastIndex) {
                    listState.requestScrollToItem(currentState.plants.lastIndex)
                }
                if (stateChanged) {
                    previousPlantIds = currentPlantIds
                }
            }

            is PlantListUiState.Empty,
            is PlantListUiState.Error -> {
                if (!filterChanged && stateChanged && previousState is PlantListUiState.Content && pendingPosition == null) {
                    pendingPosition = listState.currentPosition()
                }
            }
        }

        previousState = state
    }

    val filtersHaveNoResults = (state as? PlantListUiState.Empty)?.isFiltered == true
    val selectedFilterContainerColor = if (filtersHaveNoResults) {
        MaterialTheme.leafyColors.accent
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val selectedFilterLabelColor = if (filtersHaveNoResults) {
        MaterialTheme.leafyColors.onAccent
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }
    val navigationDescription = stringResource(if (bulkCare.isFormOpen) R.string.back else R.string.cancel)
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                colors = leafyTopAppBarColors(),
                navigationIcon = {
                    if (bulkCare.isSelectionMode) {
                        IconButton(
                            onClick = when {
                                bulkCare.isFormOpen -> onBackFromBulkCareForm
                                else -> onExitBulkCare
                            },
                            enabled = !bulkCare.isSubmitting,
                            modifier = Modifier.semantics {
                                contentDescription = navigationDescription
                            },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_back),
                                contentDescription = null,
                            )
                        }
                    }
                },
                title = {
                    Text(
                        stringResource(
                            when {
                                bulkCare.isFormOpen -> R.string.bulk_care_form_title
                                bulkCare.isSelectionMode -> R.string.bulk_care_select_plants
                                else -> R.string.plants_title
                            },
                        ),
                        style = if (bulkCare.isSelectionMode) {
                            MaterialTheme.typography.titleLarge
                        } else {
                            MaterialTheme.typography.headlineMedium
                        },
                    )
                },
                actions = {
                    if (!bulkCare.isSelectionMode) {
                        IconButton(onClick = onStartBulkCare) {
                            Icon(
                                painter = painterResource(R.drawable.ic_care_checklist),
                                contentDescription = stringResource(R.string.bulk_care_action_description),
                            )
                        }
                        IconButton(onClick = onSettingsClick) {
                            Icon(
                                painter = painterResource(R.drawable.ic_settings),
                                contentDescription = stringResource(R.string.settings),
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (!bulkCare.isSelectionMode) {
                LeafyAddFab(
                    contentDescription = stringResource(R.string.add_plant),
                    onClick = onAddPlant,
                )
            }
        },
        floatingActionButtonPosition = FabPosition.End,
        bottomBar = {
            if (bulkCare.isSelectionMode && !bulkCare.isFormOpen) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = onClearBulkCareSelection,
                        enabled = bulkCare.selectedCount > 0 && !bulkCare.isSubmitting,
                    ) { Text(stringResource(R.string.bulk_care_clear_selection)) }
                    androidx.compose.material3.Button(
                        onClick = onContinueBulkCare,
                        enabled = bulkCare.selectedCount > 0 && !bulkCare.isSubmitting,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.bulk_care_continue_count, bulkCare.selectedCount))
                    }
                }
            } else if (bulkCare.isFormOpen) {
                LeafyPrimaryButton(
                    text = stringResource(
                        when {
                            bulkCare.isSubmitting -> R.string.bulk_care_loading
                            bulkCare.hasError -> R.string.bulk_care_retry
                            else -> R.string.bulk_care_record
                        },
                    ),
                    onClick = onOpenBulkCareConfirmation,
                    enabled = bulkCare.careType != null && !bulkCare.isSubmitting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = if (bulkCare.isFormOpen) leafyLayoutTokens.spacingLg else leafyLayoutTokens.spacingXl),
        ) {
            if (bulkCare.isFormOpen) {
                BulkCareForm(
                    state = bulkCare,
                    onCareTypeChange = onBulkCareTypeChange,
                    onDateChange = onBulkCareDateChange,
                )
            } else {
            TextField(
                value = filterQuery,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max)
                    .padding(top = 4.dp),
                placeholder = { Text(stringResource(R.string.search_plants)) },
                leadingIcon = {
                    Icon(
                        painterResource(R.drawable.ic_search),
                        contentDescription = null,
                    )
                },
                shape = MaterialTheme.shapes.medium,
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
            )
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                FilterChip(
                    selected = favoritesOnly,
                    onClick = { onFavoritesOnlyChange(!favoritesOnly) },
                    modifier = Modifier.height(32.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        labelColor = MaterialTheme.colorScheme.onSurface,
                        selectedContainerColor = selectedFilterContainerColor,
                        selectedLabelColor = selectedFilterLabelColor,
                    ),
                    border = null,
                    label = {
                        Text(
                            stringResource(R.string.favorites_only),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                )
                FilterChip(
                    selected = upcomingOnly,
                    onClick = { onUpcomingOnlyChange(!upcomingOnly) },
                    modifier = Modifier.height(32.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        labelColor = MaterialTheme.colorScheme.onSurface,
                        selectedContainerColor = selectedFilterContainerColor,
                        selectedLabelColor = selectedFilterLabelColor,
                    ),
                    border = null,
                    label = {
                        Text(
                            stringResource(R.string.upcoming_filter),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                )
                FilterChip(
                    selected = overdueOnly,
                    onClick = { onOverdueOnlyChange(!overdueOnly) },
                    modifier = Modifier.height(32.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        labelColor = MaterialTheme.colorScheme.onSurface,
                        selectedContainerColor = selectedFilterContainerColor,
                        selectedLabelColor = selectedFilterLabelColor,
                    ),
                    border = null,
                    label = {
                        Text(
                            stringResource(R.string.overdue_filter),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                )
            }
            if (filterQuery.isNotBlank() || favoritesOnly || upcomingOnly || overdueOnly) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onClearFilters) {
                        Text(stringResource(R.string.clear_filters))
                    }
                }
            }

            bulkCare.completion?.let { completion ->
                BulkCareCompletionCard(
                    completion = completion,
                    onDismiss = onDismissBulkCareCompletion,
                    modifier = Modifier.padding(bottom = leafyLayoutTokens.spacingSm),
                )
            }

            if (bulkCare.isSelectionMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(
                            R.string.bulk_care_selection_count,
                            bulkCare.selectedCount,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (state is PlantListUiState.Content) {
                        TextButton(
                            onClick = { onSelectVisiblePlants(state.plants) },
                            enabled = !bulkCare.isSubmitting,
                        ) { Text(stringResource(R.string.bulk_care_select_visible)) }
                    }
                }
            }

            mutationError?.let {
                InlineError(
                    message = stringResource(R.string.favorite_update_error),
                    actionLabel = stringResource(R.string.dismiss),
                    onAction = onDismissMutationError,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            when (state) {
                PlantListUiState.Loading -> LoadingContent()
                is PlantListUiState.Error -> InlineError(
                    message = stringResource(R.string.load_plants_error),
                    actionLabel = stringResource(R.string.retry),
                    onAction = onRetry,
                )

                is PlantListUiState.Empty -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("plants_empty_state_content"),
                ) {
                    EmptyPlantList(
                        isFiltered = state.isFiltered,
                        onAddPlant = onAddPlant,
                        allowAdd = !bulkCare.isSelectionMode,
                    )
                }

                is PlantListUiState.Content -> PlantListContent(
                    plants = state.plants,
                    onPlantClick = { id ->
                        if (bulkCare.isSelectionMode) {
                            state.plants.firstOrNull { it.id == id }?.let(onToggleBulkCarePlant)
                        } else {
                            onPlantClick(id)
                        }
                    },
                    onFavoriteClick = onFavoriteClick,
                    careSummaries = careSummaries,
                    listState = listState,
                    selectionMode = bulkCare.isSelectionMode,
                    selectedPlantIds = bulkCare.selectedPlantIds.toSet(),
                    onToggleSelection = { plant -> onToggleBulkCarePlant(plant) },
                )
            }
            }
        }
    }
    if (bulkCare.isConfirming && bulkCare.careType != null) {
        AlertDialog(
            onDismissRequest = onCancelBulkCareConfirmation,
            title = { Text(stringResource(R.string.bulk_care_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.bulk_care_confirm_summary,
                        bulkCare.careType.localizedName(),
                        formatEpochDay(bulkCare.performedEpochDay),
                        bulkCare.selectedCount,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = onSubmitBulkCare,
                    enabled = !bulkCare.isSubmitting,
                ) { Text(stringResource(R.string.bulk_care_record)) }
            },
            dismissButton = {
                TextButton(
                    onClick = onCancelBulkCareConfirmation,
                    enabled = !bulkCare.isSubmitting,
                ) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BulkCareForm(
    state: BulkCareUiState,
    onCareTypeChange: (CareType) -> Unit,
    onDateChange: (Long) -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = leafyLayoutTokens.spacingMd)
            .verticalScroll(androidx.compose.foundation.rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(leafyLayoutTokens.spacingMd),
    ) {
        Text(
            text = stringResource(R.string.bulk_care_selection_count, state.selectedCount),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SettingsSectionCard(
            title = stringResource(R.string.bulk_care_section_care),
            icon = R.drawable.ic_leaf,
        ) {
            SettingsSegmentedControl(
                options = listOf(
                    SettingsSegment("watering", stringResource(R.string.bulk_care_type_water), "bulk_care_type_water"),
                    SettingsSegment("pruning", stringResource(R.string.bulk_care_type_prune), "bulk_care_type_prune"),
                    SettingsSegment("fertilizing", stringResource(R.string.bulk_care_type_fertilize), "bulk_care_type_fertilize"),
                ),
                selectedKey = when (state.careType) {
                    CareType.WATERING -> "watering"
                    CareType.PRUNING -> "pruning"
                    CareType.FERTILIZING -> "fertilizing"
                    null -> null
                },
                enabled = !state.isSubmitting,
                onSelected = { key ->
                    onCareTypeChange(
                        when (key) {
                            "watering" -> CareType.WATERING
                            "pruning" -> CareType.PRUNING
                            else -> CareType.FERTILIZING
                        },
                    )
                },
            )
        }
        androidx.compose.material3.Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !state.isSubmitting, role = Role.Button) { showDatePicker = true },
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(leafyLayoutTokens.spacingLg),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(leafyLayoutTokens.spacingSm),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(leafyLayoutTokens.spacingSm),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_calendar),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(stringResource(R.string.bulk_care_performed_date))
                }
                Text(
                    text = formatEpochDay(state.performedEpochDay),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        SettingsSectionCard(
            title = stringResource(R.string.bulk_care_selected_plants),
            icon = R.drawable.ic_leaf,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(leafyLayoutTokens.spacingSm)) {
                state.selectedPlantIds.forEach { id ->
                    val plant = state.selectedPlants[id] ?: BulkCarePlantPreview(id, id, null)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(leafyLayoutTokens.spacingMd),
                    ) {
                        PlantPhotoBox(
                            relativePath = plant.photoPath,
                            modifier = Modifier.size(40.dp).testTag("bulk_care_preview_photo_${plant.id}"),
                        )
                        Text(
                            text = plant.name,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
        if (state.hasError) {
            Text(
                text = stringResource(R.string.bulk_care_error),
                color = MaterialTheme.leafyColors.accent,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = leafyLayoutTokens.spacingMd),
            )
        }
    }
    if (showDatePicker) {
        val todayMillis = initialAcquisitionDatePickerMillis(null, state.todayEpochDay)
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialAcquisitionDatePickerMillis(
                state.performedEpochDay,
                state.todayEpochDay,
            ),
            selectableDates = remember(todayMillis) {
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= todayMillis
                }
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { onDateChange(pickerMillisToEpochDay(it)) }
                        showDatePicker = false
                    },
                    enabled = !state.isSubmitting,
                ) { Text(stringResource(R.string.select_date)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }, enabled = !state.isSubmitting) {
                    Text(stringResource(R.string.cancel))
                }
            },
        ) { DatePicker(state = pickerState, showModeToggle = false) }
    }
}

@Composable
private fun BulkCareCompletionCard(
    completion: BulkCareCompletion,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val recordedCount = completion.result.recordedPlantIds.size
    val skippedCount = completion.result.skippedPlantIds.size
    val dismissDescription = stringResource(R.string.bulk_care_dismiss_result)
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = leafyLayoutTokens.spacingMd, end = leafyLayoutTokens.spacingSm),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = leafyLayoutTokens.spacingMd),
                verticalArrangement = Arrangement.spacedBy(leafyLayoutTokens.spacingXs),
            ) {
                Text(
                    text = stringResource(
                        R.string.bulk_care_completion_context,
                        completion.careType.localizedName(),
                        formatEpochDay(completion.performedEpochDay),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (recordedCount > 0) {
                    Text(
                        text = pluralStringResource(R.plurals.bulk_care_recorded_count, recordedCount, recordedCount),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (skippedCount > 0) {
                    Text(
                        text = pluralStringResource(R.plurals.bulk_care_skipped_count, skippedCount, skippedCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (recordedCount == 0 && skippedCount > 0) {
                    Text(
                        text = stringResource(R.string.bulk_care_duplicates_only),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.semantics {
                    contentDescription = dismissDescription
                },
            ) {
                Icon(painter = painterResource(R.drawable.ic_close), contentDescription = null)
            }
        }
    }
}

@Composable
internal fun LoadingContent(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Text(
                text = stringResource(R.string.loading),
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun EmptyPlantList(
    isFiltered: Boolean,
    onAddPlant: () -> Unit,
    allowAdd: Boolean = true,
    modifier: Modifier = Modifier,
) {
    LeafyEmptyState(
        title = stringResource(
            if (isFiltered) R.string.no_matching_plants else R.string.no_plants_yet,
        ),
        supporting = stringResource(
            if (isFiltered) R.string.try_different_filter else R.string.add_first_plant_hint,
        ),
        modifier = modifier.fillMaxWidth(),
        action = if (!isFiltered && allowAdd) stringResource(R.string.add_plant) else null,
        onAction = onAddPlant,
        adaptiveIllustration = true,
    )
}

private data class PlantListFilterKey(
    val query: String,
    val favoritesOnly: Boolean,
    val upcomingOnly: Boolean,
    val overdueOnly: Boolean,
)

private data class PlantListPosition(
    val index: Int,
    val offset: Int,
)

private fun LazyListState.currentPosition(): PlantListPosition = PlantListPosition(
    index = firstVisibleItemIndex,
    offset = firstVisibleItemScrollOffset,
)

@Composable
private fun PlantListContent(
    plants: List<Plant>,
    onPlantClick: (String) -> Unit,
    onFavoriteClick: (String, Boolean) -> Unit,
    careSummaries: Map<String, List<CareStatusSummary>>,
    listState: LazyListState,
    selectionMode: Boolean = false,
    selectedPlantIds: Set<String> = emptySet(),
    onToggleSelection: (Plant) -> Unit = {},
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(plants, key = { it.id }) { plant ->
            PlantListItem(
                plant = plant,
                onClick = { onPlantClick(plant.id) },
                onFavoriteClick = { onFavoriteClick(plant.id, !plant.isFavorite) },
                careSummaries = careSummaries[plant.id].orEmpty(),
                selectionMode = selectionMode,
                selected = plant.id in selectedPlantIds,
                onToggleSelection = { onToggleSelection(plant) },
            )
        }
    }
}

@Composable
private fun PlantListItem(
    plant: Plant,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    careSummaries: List<CareStatusSummary>,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onToggleSelection: () -> Unit = {},
) {
    val favoriteDescription = stringResource(
        if (plant.isFavorite) R.string.remove_favorite else R.string.add_favorite,
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(leafyLayoutTokens.cardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(leafyLayoutTokens.spacingMd),
        ) {
            PlantPhotoBox(
                relativePath = plant.mainPhotoPath,
                modifier = Modifier.size(leafyLayoutTokens.thumbnailSize),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = leafyLayoutTokens.thumbnailSize),
                verticalArrangement = Arrangement.Top,
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = plant.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        plant.scientificName?.let { scientificName ->
                            Text(
                                text = scientificName,
                                style = MaterialTheme.typography.bodySmall,
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (selectionMode) {
                        Checkbox(
                            checked = selected,
                            onCheckedChange = { onToggleSelection() },
                            modifier = Modifier.semantics {
                                contentDescription = plant.name
                            },
                        )
                    } else {
                        IconButton(
                            onClick = onFavoriteClick,
                            modifier = Modifier
                                .size(32.dp)
                                .semantics { contentDescription = favoriteDescription },
                        ) {
                            Icon(
                                painterResource(
                                    if (plant.isFavorite) R.drawable.ic_favorite
                                    else R.drawable.ic_favorite_border,
                                ),
                                contentDescription = null,
                                tint = MaterialTheme.leafyColors.accent,
                            )
                        }
                    }
                }
                plant.location?.let { location ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painterResource(R.drawable.ic_location),
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            location,
                            modifier = Modifier.padding(start = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                CareStatusCompact(careSummaries)
            }
        }
    }
}

@Composable
private fun CareStatusCompact(summaries: List<CareStatusSummary>) {
    val active = summaries.sortedBy(CareStatusSummary::nextDueEpochDay)
    val urgent = active.firstOrNull() ?: return
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val statusColors = MaterialTheme.leafyColors
        val container = when (urgent.status) {
            CareDueStatus.OVERDUE -> statusColors.overdueContainer
            CareDueStatus.DUE_TODAY -> statusColors.dueContainer
            CareDueStatus.UPCOMING, CareDueStatus.LATER -> statusColors.upcomingContainer
        }
        val foreground = when (urgent.status) {
            CareDueStatus.OVERDUE -> statusColors.onOverdue
            CareDueStatus.DUE_TODAY -> statusColors.onDue
            CareDueStatus.UPCOMING, CareDueStatus.LATER -> statusColors.onUpcoming
        }
        Text(
            text = stringResource(
                R.string.compact_care_status,
                urgent.careType.localizedName(),
                formatEpochDay(urgent.nextDueEpochDay),
            ),
            color = foreground,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f, fill = false)
                .background(container, MaterialTheme.shapes.extraLarge)
                .padding(horizontal = 12.dp, vertical = 5.dp),
        )
        if (active.size > 1) {
            Text(
                stringResource(R.string.more_care_rules, active.size - 1),
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}


@Composable
private fun InlineError(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(message, modifier = Modifier.weight(1f))
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Preview(name = "List - Light", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "List - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PlantListPreview() {
    StayLeafyTheme(dynamicColor = false) {
        PlantListScreen(
            state = PlantListUiState.Content(previewPlants),
            filterQuery = "",
            favoritesOnly = false,
            mutationError = null,
            onQueryChange = {},
            onFavoritesOnlyChange = {},
            onClearFilters = {},
            onRetry = {},
            onAddPlant = {},
            onPlantClick = {},
            onFavoriteClick = { _, _ -> },
            onDismissMutationError = {},
            careSummaries = mapOf(
                "preview-1" to listOf(
                    CareStatusSummary(
                        plantId = "preview-1",
                        careType = CareType.WATERING,
                        nextDueEpochDay = 21_000,
                        status = CareDueStatus.UPCOMING,
                    ),
                ),
            ),
        )
    }
}

@Preview(name = "Empty - Light", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Empty - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PlantListEmptyPreview() {
    StayLeafyTheme(dynamicColor = false) {
        PlantListScreen(
            state = PlantListUiState.Empty(isFiltered = false),
            filterQuery = "",
            favoritesOnly = false,
            mutationError = null,
            onQueryChange = {},
            onFavoritesOnlyChange = {},
            onClearFilters = {},
            onRetry = {},
            onAddPlant = {},
            onPlantClick = {},
            onFavoriteClick = { _, _ -> },
            onDismissMutationError = {},
        )
    }
}

@Preview(name = "Loading - Light", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Loading - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PlantListLoadingPreview() {
    StayLeafyTheme(dynamicColor = false) {
        PlantListScreen(
            state = PlantListUiState.Loading,
            filterQuery = "",
            favoritesOnly = false,
            mutationError = null,
            onQueryChange = {},
            onFavoritesOnlyChange = {},
            onClearFilters = {},
            onRetry = {},
            onAddPlant = {},
            onPlantClick = {},
            onFavoriteClick = { _, _ -> },
            onDismissMutationError = {},
        )
    }
}

@Preview(name = "Error - Light", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Error - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PlantListErrorPreview() {
    StayLeafyTheme(dynamicColor = false) {
        PlantListScreen(
            state = PlantListUiState.Error(),
            filterQuery = "",
            favoritesOnly = false,
            mutationError = null,
            onQueryChange = {},
            onFavoritesOnlyChange = {},
            onClearFilters = {},
            onRetry = {},
            onAddPlant = {},
            onPlantClick = {},
            onFavoriteClick = { _, _ -> },
            onDismissMutationError = {},
        )
    }
}

@Preview(
    name = "Filtered empty - Light",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_NO
)
@Preview(
    name = "Filtered empty - Dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun PlantListFilteredEmptyPreview() {
    StayLeafyTheme(dynamicColor = false) {
        PlantListScreen(
            state = PlantListUiState.Empty(isFiltered = true),
            filterQuery = "fern",
            favoritesOnly = false,
            mutationError = null,
            onQueryChange = {},
            onFavoritesOnlyChange = {},
            onClearFilters = {},
            onRetry = {},
            onAddPlant = {},
            onPlantClick = {},
            onFavoriteClick = { _, _ -> },
            onDismissMutationError = {},
        )
    }
}

internal val previewPlants = listOf(
    Plant(
        id = "preview-1",
        name = "Monstera",
        scientificName = "Monstera deliciosa",
        location = "Living room",
        acquiredEpochDay = null,
        description = null,
        mainPhotoPath = null,
        isFavorite = true,
        createdAt = 0L,
        updatedAt = 0L,
    ),
    Plant(
        id = "preview-2",
        name = "Aloe",
        scientificName = null,
        location = null,
        acquiredEpochDay = null,
        description = null,
        mainPhotoPath = null,
        isFavorite = false,
        createdAt = 0L,
        updatedAt = 0L,
    ),
)
