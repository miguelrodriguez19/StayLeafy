package com.miguelrodriguez19.stayleafy.ui.plant

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miguelrodriguez19.stayleafy.R
import com.miguelrodriguez19.stayleafy.model.Plant
import com.miguelrodriguez19.stayleafy.ui.theme.StayLeafyTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlantDetailRoute(
    plantId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDeleted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: PlantDetailViewModel = hiltViewModel(
        creationCallback = { factory: PlantDetailViewModel.Factory -> factory.create(plantId) },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val mutationError by viewModel.mutationError.collectAsStateWithLifecycle()
    val isDeleting by viewModel.isDeleting.collectAsStateWithLifecycle()
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()

    LaunchedEffect(deleted) {
        if (deleted) onDeleted()
    }

    PlantDetailScreen(
        state = state,
        mutationError = mutationError,
        isDeleting = isDeleting,
        onBack = onBack,
        onRetry = viewModel::retry,
        onEdit = onEdit,
        onFavoriteClick = viewModel::setFavorite,
        onDelete = viewModel::delete,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlantDetailScreen(
    state: PlantDetailUiState,
    mutationError: UiErrorCategory?,
    isDeleting: Boolean,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onEdit: () -> Unit,
    onFavoriteClick: (Boolean) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    val title = when (state) {
        is PlantDetailUiState.Content -> state.plant.displayName
        else -> stringResource(R.string.plant_details)
    }
    val backDescription = stringResource(R.string.back)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    TextButton(
                        onClick = onBack,
                        modifier = Modifier.semantics {
                            contentDescription = backDescription
                        },
                    ) {
                        Text(stringResource(R.string.back))
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (state) {
                PlantDetailUiState.Loading -> LoadingContent(modifier = Modifier.align(Alignment.Center))
                is PlantDetailUiState.Error -> DetailError(onRetry = onRetry)
                PlantDetailUiState.NotFound -> NotFoundContent(onBack = onBack)
                is PlantDetailUiState.Content -> PlantDetailContent(
                    plant = state.plant,
                    mutationError = mutationError,
                    isDeleting = isDeleting,
                    onEdit = onEdit,
                    onFavoriteClick = onFavoriteClick,
                    onDeleteRequest = { showDeleteDialog = true },
                )
            }
        }
    }

    if (showDeleteDialog) {
        DeletePlantDialog(
            isDeleting = isDeleting,
            onDismiss = { if (!isDeleting) showDeleteDialog = false },
            onConfirm = onDelete,
        )
    }
}

@Composable
private fun PlantDetailContent(
    plant: Plant,
    mutationError: UiErrorCategory?,
    isDeleting: Boolean,
    onEdit: () -> Unit,
    onFavoriteClick: (Boolean) -> Unit,
    onDeleteRequest: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = { onFavoriteClick(!plant.isFavorite) },
                modifier = Modifier.weight(1f),
                enabled = !isDeleting,
            ) {
                Text(
                    stringResource(
                        if (plant.isFavorite) R.string.remove_favorite else R.string.add_favorite,
                    ),
                )
            }
            OutlinedButton(
                onClick = onEdit,
                modifier = Modifier.weight(1f),
                enabled = !isDeleting,
            ) {
                Text(stringResource(R.string.edit_plant))
            }
        }
        mutationError?.let {
            Text(
                text = stringResource(
                    if (it == UiErrorCategory.Delete) R.string.delete_plant_error
                    else R.string.favorite_update_error,
                ),
                color = MaterialTheme.colorScheme.error,
            )
        }
        DetailField(R.string.display_name, plant.displayName)
        plant.commonName?.let { DetailField(R.string.common_name, it) }
        plant.scientificName?.let { DetailField(R.string.scientific_name, it) }
        plant.location?.let { DetailField(R.string.location, it) }
        plant.acquiredEpochDay?.let {
            DetailField(R.string.acquisition_date, formatEpochDay(it))
        }
        plant.description?.let { DetailField(R.string.description, it) }
        OutlinedButton(
            onClick = onDeleteRequest,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isDeleting,
        ) {
            Text(stringResource(R.string.delete_plant))
        }
    }
}

@Composable
private fun DetailField(label: Int, value: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(label), style = MaterialTheme.typography.labelLarge)
            Text(value, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun DetailError(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.load_plant_error))
        Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) {
            Text(stringResource(R.string.retry))
        }
    }
}

@Composable
private fun NotFoundContent(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.plant_not_found), style = MaterialTheme.typography.titleMedium)
        Button(onClick = onBack, modifier = Modifier.padding(top = 16.dp)) {
            Text(stringResource(R.string.back_to_plants))
        }
    }
}

@Composable
private fun DeletePlantDialog(
    isDeleting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_plant_title)) },
        text = { Text(stringResource(R.string.delete_plant_message)) },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isDeleting) {
                Text(stringResource(R.string.cancel))
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = !isDeleting) {
                Text(stringResource(R.string.delete_plant))
            }
        },
    )
}

@Preview(name = "Detail - Light", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Detail - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PlantDetailPreview() {
    StayLeafyTheme(dynamicColor = false) {
        PlantDetailScreen(
            state = PlantDetailUiState.Content(previewPlants.first()),
            mutationError = null,
            isDeleting = false,
            onBack = {},
            onRetry = {},
            onEdit = {},
            onFavoriteClick = {},
            onDelete = {},
        )
    }
}

@Preview(name = "Delete confirmation - Light", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Delete confirmation - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PlantDeleteDialogPreview() {
    StayLeafyTheme(dynamicColor = false) {
        DeletePlantDialog(isDeleting = false, onDismiss = {}, onConfirm = {})
    }
}

