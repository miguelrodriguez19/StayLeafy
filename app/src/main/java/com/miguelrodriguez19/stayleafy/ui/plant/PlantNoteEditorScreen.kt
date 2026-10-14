package com.miguelrodriguez19.stayleafy.ui.plant

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miguelrodriguez19.stayleafy.R
import com.miguelrodriguez19.stayleafy.ui.components.LeafyDiscardChangesDialog
import com.miguelrodriguez19.stayleafy.ui.components.leafyTopAppBarColors
import com.miguelrodriguez19.stayleafy.ui.components.LeafyPrimaryButton
import com.miguelrodriguez19.stayleafy.ui.theme.StayLeafyTheme

@Composable
fun PlantNoteEditorRoute(
    plantId: String,
    noteId: String?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: PlantNoteEditorViewModel = hiltViewModel(
        creationCallback = { factory: PlantNoteEditorViewModel.Factory -> factory.create(plantId, noteId) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onSaved() }
    PlantNoteEditorScreen(
        state = state,
        onBack = onBack,
        onBodyChange = viewModel::updateBody,
        onRetry = viewModel::retryLoad,
        onSave = viewModel::save,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlantNoteEditorScreen(
    state: PlantNoteEditorState,
    onBack: () -> Unit,
    onBodyChange: (String) -> Unit,
    onRetry: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(if (state.noteId == null) R.string.add_note else R.string.edit_note)
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }
    val requestBack = {
        if (!state.isLoading && state.body != state.initialBody && !state.isSaving) {
            showDiscardDialog = true
        } else {
            onBack()
        }
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                colors = leafyTopAppBarColors(),
                title = { Text(title, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = requestBack, enabled = !state.isSaving) {
                        Icon(
                            painterResource(R.drawable.ic_back),
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (!state.isLoading && !state.notFound && state.loadError == null) {
                LeafyPrimaryButton(
                    text = stringResource(if (state.isSaving) R.string.saving else R.string.save),
                    onClick = onSave,
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        },
    ) { innerPadding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(innerPadding)) { LoadingContent() }
            state.notFound -> NoteNotFoundContent(onBack)
            state.loadError != null -> NoteEditorError(state.loadError, onRetry, Modifier.padding(innerPadding))
            else -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                state.plantName?.let { plantName ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            PlantPhotoBox(
                                relativePath = state.plantPhotoPath,
                                modifier = Modifier.size(56.dp),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(plantName, style = MaterialTheme.typography.titleMedium)
                                state.plantScientificName?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        val body = state.body.take(MAX_NOTE_BODY_LENGTH)
                        OutlinedTextField(
                            value = body,
                            onValueChange = { onBodyChange(it.take(MAX_NOTE_BODY_LENGTH)) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.note_body)) },
                            minLines = 9,
                            shape = MaterialTheme.shapes.medium,
                            isError = state.bodyError,
                            supportingText = if (state.bodyError) ({ Text(stringResource(R.string.note_required)) }) else null,
                        )
                        Text(
                            text = stringResource(
                                R.string.note_character_count,
                                body.length,
                                MAX_NOTE_BODY_LENGTH,
                            ),
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            textAlign = TextAlign.End,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        state.saveError?.let { Text(stringResource(R.string.save_note_error), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }
                    }
                }
            }
        }
    }

    if (showDiscardDialog) {
        LeafyDiscardChangesDialog(
            title = stringResource(R.string.discard_changes_title),
            message = stringResource(R.string.discard_changes_message),
            onDismiss = { showDiscardDialog = false },
            onConfirm = {
                showDiscardDialog = false
                onBack()
            },
        )
    }
}

@Composable
private fun NoteEditorError(category: UiErrorCategory, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(32.dp)) {
        Text(stringResource(if (category == UiErrorCategory.NoteLoad) R.string.load_note_error else R.string.save_note_error))
        Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) { Text(stringResource(R.string.retry)) }
    }
}

@Composable
private fun NoteNotFoundContent(onBack: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(32.dp)) {
        Text(stringResource(R.string.note_target_not_found), style = MaterialTheme.typography.titleMedium)
        Button(onClick = onBack, modifier = Modifier.padding(top = 16.dp)) { Text(stringResource(R.string.back)) }
    }
}

@Preview(name = "Note editor - Light", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Note editor - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable private fun PlantNoteEditorPreview() {
    StayLeafyTheme(dynamicColor = false) {
        PlantNoteEditorScreen(
            PlantNoteEditorState(
                noteId = null,
                isLoading = false,
                plantName = "Monstera",
                plantScientificName = "Monstera deliciosa",
                body = "Watered and moved closer to the window.",
            ),
            {}, {}, {}, {},
        )
    }
}
