package com.miguelrodriguez19.stayleafy.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.miguelrodriguez19.stayleafy.R
import com.miguelrodriguez19.stayleafy.ui.plant.PlantDetailRoute
import com.miguelrodriguez19.stayleafy.ui.plant.PlantListRoute
import com.miguelrodriguez19.stayleafy.ui.plant.PlantNoteEditorRoute
import com.miguelrodriguez19.stayleafy.ui.reminder.CareConfigurationRoute
import com.miguelrodriguez19.stayleafy.ui.reminder.NoticeDetailRoute
import com.miguelrodriguez19.stayleafy.ui.reminder.NoticeListRoute
import com.miguelrodriguez19.stayleafy.ui.reminder.RootNavigationViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

enum class RootTab { Plants, Notices }

/** Entry point for notification PendingIntents once the Android notification layer is connected. */
object AndroidNoticeNavigation {
    private val requestChannel = Channel<String>(capacity = Channel.BUFFERED)
    val requests: Flow<String> = requestChannel.receiveAsFlow()

    fun openNotice(noticeId: String): Boolean =
        noticeId.isNotBlank() && requestChannel.trySend(noticeId).isSuccess

    fun handleIntent(intent: Intent?): Boolean {
        val noticeId = intent?.getStringExtra(NOTICE_ID_EXTRA)
            ?: intent?.data?.takeIf { it.scheme == "stayleafy" && it.host == "notice" }?.lastPathSegment
        return noticeId?.let(::openNotice) ?: false
    }

    private const val NOTICE_ID_EXTRA = "com.miguelrodriguez19.stayleafy.extra.NOTICE_ID"
}

@Composable
fun MainNavigation(
    noticeNavigationRequests: Flow<String> = AndroidNoticeNavigation.requests,
) {
    val plantsBackStack = rememberNavBackStack(PlantList)
    val noticesBackStack = rememberNavBackStack(NoticeList)
    val plantsSaveableStateDecorator = rememberSaveableStateHolderNavEntryDecorator<NavKey>()
    val plantsViewModelStoreDecorator = rememberViewModelStoreNavEntryDecorator<NavKey>()
    val noticesSaveableStateDecorator = rememberSaveableStateHolderNavEntryDecorator<NavKey>()
    val noticesViewModelStoreDecorator = rememberViewModelStoreNavEntryDecorator<NavKey>()
    var selectedTab by rememberSaveable { mutableStateOf(RootTab.Plants) }
    val rootViewModel: RootNavigationViewModel = hiltViewModel()
    val unreadCount by rootViewModel.unreadCount.collectAsStateWithLifecycle()
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {}
    val activeStack = if (selectedTab == RootTab.Plants) plantsBackStack else noticesBackStack
    val showBottomBar = activeStack.size == 1

    fun openPlant(plantId: String) {
        while (plantsBackStack.size > 1) plantsBackStack.removeLastOrNull()
        plantsBackStack.add(PlantDetail(plantId))
        selectedTab = RootTab.Plants
    }

    fun openNotice(noticeId: String) {
        while (noticesBackStack.size > 1) noticesBackStack.removeLastOrNull()
        noticesBackStack.add(NoticeDetail(noticeId))
        selectedTab = RootTab.Notices
    }

    LaunchedEffect(noticeNavigationRequests) {
        noticeNavigationRequests.collect(::openNotice)
    }

    Scaffold(
        modifier = Modifier.safeDrawingPadding(),
        bottomBar = {
            if (showBottomBar) {
                RootNavigationBar(
                    selectedTab = selectedTab,
                    unreadCount = unreadCount,
                    onSelect = { selectedTab = it },
                )
            }
        },
    ) { innerPadding ->
        when (selectedTab) {
            RootTab.Plants -> {
                NavDisplay(
                    backStack = plantsBackStack,
                    modifier = Modifier.padding(innerPadding),
                    onBack = { plantsBackStack.removeLastOrNull() },
                    entryDecorators = listOf(plantsSaveableStateDecorator, plantsViewModelStoreDecorator),
                    entryProvider = entryProvider {
                        entry<PlantList> {
                            PlantListRoute(
                                onAddPlant = { plantsBackStack.add(PlantDetail()) },
                                onPlantClick = { plantId -> plantsBackStack.add(PlantDetail(plantId)) },
                            )
                        }
                        entry<PlantDetail> { key ->
                            PlantDetailRoute(
                                plantId = key.plantId,
                                onBack = { plantsBackStack.removeLastOrNull() },
                                onDeleted = { plantsBackStack.removeLastOrNull() },
                                onCreated = { plantId ->
                                    plantsBackStack[plantsBackStack.lastIndex] = PlantDetail(plantId)
                                },
                                onAddNote = { key.plantId?.let { plantsBackStack.add(PlantNoteEditor(it)) } },
                                onEditNote = { noteId ->
                                    key.plantId?.let { plantsBackStack.add(PlantNoteEditor(it, noteId)) }
                                },
                                onConfigureCare = {
                                    key.plantId?.let { plantsBackStack.add(CareConfiguration(it)) }
                                },
                            )
                        }
                        entry<PlantNoteEditor> { key ->
                            PlantNoteEditorRoute(
                                plantId = key.plantId,
                                noteId = key.noteId,
                                onBack = { plantsBackStack.removeLastOrNull() },
                                onSaved = { plantsBackStack.removeLastOrNull() },
                            )
                        }
                        entry<CareConfiguration> { key ->
                            CareConfigurationRoute(
                                plantId = key.plantId,
                                onBack = { plantsBackStack.removeLastOrNull() },
                                onSaved = { plantsBackStack.removeLastOrNull() },
                                onRequestNotificationPermission = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                },
                            )
                        }
                    },
                )
            }
            RootTab.Notices -> {
                NavDisplay(
                    backStack = noticesBackStack,
                    modifier = Modifier.padding(innerPadding),
                    onBack = { noticesBackStack.removeLastOrNull() },
                    entryDecorators = listOf(noticesSaveableStateDecorator, noticesViewModelStoreDecorator),
                    entryProvider = entryProvider {
                        entry<NoticeList> {
                            NoticeListRoute(
                                onNoticeClick = { noticeId -> noticesBackStack.add(NoticeDetail(noticeId)) },
                            )
                        }
                        entry<NoticeDetail> { key ->
                            NoticeDetailRoute(
                                noticeId = key.noticeId,
                                onBack = { noticesBackStack.removeLastOrNull() },
                                onViewPlant = ::openPlant,
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun RootNavigationBar(
    selectedTab: RootTab,
    unreadCount: Int,
    onSelect: (RootTab) -> Unit,
) {
    NavigationBar {
        NavigationBarItem(
            selected = selectedTab == RootTab.Plants,
            onClick = { onSelect(RootTab.Plants) },
            icon = { Text(stringResource(R.string.navigation_plants_symbol)) },
            label = { Text(stringResource(R.string.navigation_plants)) },
        )
        NavigationBarItem(
            selected = selectedTab == RootTab.Notices,
            onClick = { onSelect(RootTab.Notices) },
            icon = {
                BadgedBox(
                    badge = {
                        if (unreadCount > 0) {
                            Badge { Text(if (unreadCount > 99) "99+" else unreadCount.toString()) }
                        }
                    },
                ) { Text(stringResource(R.string.navigation_notices_symbol)) }
            },
            label = { Text(stringResource(R.string.navigation_notices)) },
        )
    }
}
