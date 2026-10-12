package com.miguelrodriguez19.stayleafy.ui.plant

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miguelrodriguez19.stayleafy.R
import com.miguelrodriguez19.stayleafy.model.Plant
import com.miguelrodriguez19.stayleafy.model.CareDueStatus
import com.miguelrodriguez19.stayleafy.model.CareStatusSummary
import com.miguelrodriguez19.stayleafy.ui.reminder.PlantCareSummaryViewModel
import com.miguelrodriguez19.stayleafy.ui.reminder.localizedName
import com.miguelrodriguez19.stayleafy.ui.theme.StayLeafyTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlantListRoute(
    onAddPlant: () -> Unit,
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
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.plants_title)) },
                actions = {
                    TextButton(onClick = onAddPlant) {
                        Text(stringResource(R.string.add_plant))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            OutlinedTextField(
                value = filterQuery,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                label = { Text(stringResource(R.string.search_plants)) },
                singleLine = true,
            )
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                FilterChip(
                    selected = favoritesOnly,
                    onClick = { onFavoritesOnlyChange(!favoritesOnly) },
                    label = { Text(stringResource(R.string.favorites_only)) },
                )
                FilterChip(
                    selected = upcomingOnly,
                    onClick = { onUpcomingOnlyChange(!upcomingOnly) },
                    label = { Text(stringResource(R.string.upcoming_filter)) },
                )
                FilterChip(
                    selected = overdueOnly,
                    onClick = { onOverdueOnlyChange(!overdueOnly) },
                    label = { Text(stringResource(R.string.overdue_filter)) },
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(
                    if (isFiltered) R.string.no_matching_plants else R.string.no_plants_yet,
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(
                    if (isFiltered) R.string.try_different_filter else R.string.add_first_plant_hint,
                ),
                modifier = Modifier.padding(top = 8.dp),
            )
            Button(
                onClick = if (isFiltered) onClearFilters else onAddPlant,
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Text(
                    stringResource(
                        if (isFiltered) R.string.clear_filters else R.string.add_plant,
                    ),
                )
            }
        }
    }
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
        verticalArrangement = Arrangement.spacedBy(8.dp),
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
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlantPhotoBox(
                    relativePath = plant.mainPhotoPath,
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .size(64.dp),
                )
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
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                TextButton(
                    onClick = onFavoriteClick,
                    modifier = Modifier.semantics {
                        contentDescription = favoriteDescription
                    },
                ) {
                    Text(
                        stringResource(
                            if (plant.isFavorite) R.string.favorite_marker_on
                            else R.string.favorite_marker_off,
                        ),
                    )
                }
            }
            plant.location?.let { location ->
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(location, style = MaterialTheme.typography.bodySmall)
            }
            CareStatusCompact(careSummaries)
        }
    }
}

@Composable
private fun CareStatusCompact(summaries: List<CareStatusSummary>) {
    val active = summaries.sortedBy(CareStatusSummary::nextDueEpochDay)
    val urgent = active.firstOrNull() ?: return
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(
                R.string.compact_care_status,
                urgent.careType.localizedName(),
                formatEpochDay(urgent.nextDueEpochDay),
            ),
            color = when (urgent.status) {
                CareDueStatus.OVERDUE -> MaterialTheme.colorScheme.error
                CareDueStatus.DUE_TODAY -> MaterialTheme.colorScheme.primary
                CareDueStatus.UPCOMING, CareDueStatus.LATER -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        if (active.size > 1) Text(stringResource(R.string.more_care_rules, active.size - 1))
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

@Preview(name = "Filtered empty - Light", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Filtered empty - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
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
        name = "Ficus del salón",
        scientificName = "Ficus lyrata",
        location = "Living room",
        acquiredEpochDay = null,
        description = null,
        mainPhotoPath = "plant_photos/preview.jpg",
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
