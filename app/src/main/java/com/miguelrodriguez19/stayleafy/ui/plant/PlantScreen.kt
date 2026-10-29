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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
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
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val mutationError by viewModel.mutationError.collectAsStateWithLifecycle()
    val careSummaries by careSummaryViewModel.summaries.collectAsStateWithLifecycle()

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
) {
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

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                colors = leafyTopAppBarColors(),
                title = {
                    Text(
                        stringResource(R.string.plants_title),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            painter = painterResource(R.drawable.ic_settings),
                            contentDescription = stringResource(R.string.settings),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            LeafyAddFab(
                contentDescription = stringResource(R.string.add_plant),
                onClick = onAddPlant,
            )
        },
        floatingActionButtonPosition = FabPosition.End,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = leafyLayoutTokens.spacingXl),
        ) {
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

                is PlantListUiState.Empty -> EmptyPlantList(
                    isFiltered = state.isFiltered,
                    onAddPlant = onAddPlant,
                    onClearFilters = onClearFilters,
                )

                is PlantListUiState.Content -> PlantListContent(
                    plants = state.plants,
                    onPlantClick = onPlantClick,
                    onFavoriteClick = onFavoriteClick,
                    careSummaries = careSummaries,
                )
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
    onClearFilters: () -> Unit,
) {
    LeafyEmptyState(
        title = stringResource(
            if (isFiltered) R.string.no_matching_plants else R.string.no_plants_yet,
        ),
        supporting = stringResource(
            if (isFiltered) R.string.try_different_filter else R.string.add_first_plant_hint,
        ),
        modifier = Modifier.fillMaxWidth(),
        action = stringResource(if (isFiltered) R.string.clear_filters else R.string.add_plant),
        onAction = if (isFiltered) onClearFilters else onAddPlant,
    )
}

@Composable
private fun PlantListContent(
    plants: List<Plant>,
    onPlantClick: (String) -> Unit,
    onFavoriteClick: (String, Boolean) -> Unit,
    careSummaries: Map<String, List<CareStatusSummary>>,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(plants, key = { it.id }) { plant ->
            PlantListItem(
                plant = plant,
                onClick = { onPlantClick(plant.id) },
                onFavoriteClick = { onFavoriteClick(plant.id, !plant.isFavorite) },
                careSummaries = careSummaries[plant.id].orEmpty(),
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
