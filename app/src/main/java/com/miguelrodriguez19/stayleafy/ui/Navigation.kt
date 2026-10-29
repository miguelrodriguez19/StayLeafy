package com.miguelrodriguez19.stayleafy.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
import com.miguelrodriguez19.stayleafy.ui.backup.BackupRoute
import com.miguelrodriguez19.stayleafy.ui.reminder.CareConfigurationRoute
import com.miguelrodriguez19.stayleafy.ui.reminder.NoticeDetailRoute
import com.miguelrodriguez19.stayleafy.ui.reminder.NoticeListRoute
import com.miguelrodriguez19.stayleafy.ui.reminder.NoticeListViewModel
import com.miguelrodriguez19.stayleafy.ui.theme.leafyColors
import com.miguelrodriguez19.stayleafy.ui.reminder.RootNavigationViewModel
import com.miguelrodriguez19.stayleafy.ui.settings.NotificationPermissionAction
import com.miguelrodriguez19.stayleafy.ui.settings.NotificationPermissionStatus
import com.miguelrodriguez19.stayleafy.ui.settings.NotificationPermissionSummary
import com.miguelrodriguez19.stayleafy.ui.settings.SettingsRoute
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
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val plantsBackStack = rememberNavBackStack(PlantList)
    val noticesBackStack = rememberNavBackStack(NoticeList)
    val plantsSaveableStateDecorator = rememberSaveableStateHolderNavEntryDecorator<NavKey>()
    val plantsViewModelStoreDecorator = rememberViewModelStoreNavEntryDecorator<NavKey>()
    val noticesSaveableStateDecorator = rememberSaveableStateHolderNavEntryDecorator<NavKey>()
    val noticesViewModelStoreDecorator = rememberViewModelStoreNavEntryDecorator<NavKey>()
    var selectedTab by rememberSaveable { mutableStateOf(RootTab.Plants) }
    val rootViewModel: RootNavigationViewModel = hiltViewModel()
    val noticeListViewModel: NoticeListViewModel = hiltViewModel()
    val unreadCount by rootViewModel.unreadCount.collectAsStateWithLifecycle()
    var permissionRefresh by rememberSaveable { mutableIntStateOf(0) }
    var notificationPermissionRequested by rememberSaveable { mutableStateOf(false) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        notificationPermissionRequested = true
        permissionRefresh++
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permissionRefresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val notificationPermission = notificationPermissionSummary(
        context = context,
        permissionRequested = notificationPermissionRequested,
        refresh = permissionRefresh,
    )
    fun openPlant(plantId: String) {
        while (plantsBackStack.size > 1) plantsBackStack.removeLastOrNull()
        plantsBackStack.add(PlantDetail(plantId))
        selectedTab = RootTab.Plants
    }

    fun openPlantFromNotice(plantId: String) {
        while (noticesBackStack.size > 1) noticesBackStack.removeLastOrNull()
        openPlant(plantId)
    }

    fun openNotice(noticeId: String) {
        while (noticesBackStack.size > 1) noticesBackStack.removeLastOrNull()
        noticesBackStack.add(NoticeDetail(noticeId))
        selectedTab = RootTab.Notices
    }

    fun selectRoot(tab: RootTab) {
        if (tab != selectedTab) {
            selectedTab = tab
            return
        }

        when (tab) {
            RootTab.Plants -> while (plantsBackStack.size > 1) plantsBackStack.removeLastOrNull()
            RootTab.Notices -> while (noticesBackStack.size > 1) noticesBackStack.removeLastOrNull()
        }
    }

    LaunchedEffect(noticeNavigationRequests) {
        noticeNavigationRequests.collect(::openNotice)
    }

    Scaffold(
        modifier = Modifier.safeDrawingPadding(),
        bottomBar = {
            RootNavigationBar(
                selectedTab = selectedTab,
                unreadCount = unreadCount,
                onSelect = ::selectRoot,
            )
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
                                onSettingsClick = { plantsBackStack.add(Settings) },
                                onPlantClick = { plantId -> plantsBackStack.add(PlantDetail(plantId)) },
                            )
                        }
                        entry<Settings> {
                            SettingsRoute(
                                onNavigateBack = { plantsBackStack.removeLastOrNull() },
                                notificationPermission = notificationPermission,
                                onNotificationPermissionAction = { action ->
                                    when (action) {
                                        NotificationPermissionAction.NONE -> Unit
                                        NotificationPermissionAction.REQUEST_PERMISSION -> {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                notificationPermissionLauncher.launch(
                                                    Manifest.permission.POST_NOTIFICATIONS,
                                                )
                                            }
                                        }
                                        NotificationPermissionAction.OPEN_SYSTEM_SETTINGS -> {
                                            val notificationSettings = Intent(
                                                AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS,
                                            ).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)
                                            runCatching { context.startActivity(notificationSettings) }
                                                .onFailure {
                                                    context.startActivity(
                                                        Intent(
                                                            AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                            Uri.parse("package:${context.packageName}"),
                                                        ),
                                                    )
                                                }
                                        }
                                    }
                                },
                                onBackupClick = { plantsBackStack.add(Backup) },
                            )
                        }
                        entry<Backup> {
                            BackupRoute(
                                onNavigateBack = { plantsBackStack.removeLastOrNull() },
                                onRestoreCompleted = {
                                    while (plantsBackStack.size > 1) plantsBackStack.removeLastOrNull()
                                },
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
                                viewModel = noticeListViewModel,
                            )
                        }
                        entry<NoticeDetail> { key ->
                            NoticeDetailRoute(
                                noticeId = key.noticeId,
                                onBack = { noticesBackStack.removeLastOrNull() },
                                onViewPlant = ::openPlantFromNotice,
                            )
                        }
                    },
                )
            }
        }
    }
}

@Suppress("UNUSED_PARAMETER")
private fun notificationPermissionSummary(
    context: Context,
    permissionRequested: Boolean,
    refresh: Int,
): NotificationPermissionSummary {
    val runtimeGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    val enabled = runtimeGranted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    return when {
        enabled -> NotificationPermissionSummary(
            status = NotificationPermissionStatus.ENABLED,
            action = NotificationPermissionAction.OPEN_SYSTEM_SETTINGS,
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !runtimeGranted &&
            !permissionRequested -> NotificationPermissionSummary(
                status = NotificationPermissionStatus.NEEDS_PERMISSION,
                action = NotificationPermissionAction.REQUEST_PERMISSION,
            )
        else -> NotificationPermissionSummary(
            status = NotificationPermissionStatus.BLOCKED,
            action = NotificationPermissionAction.OPEN_SYSTEM_SETTINGS,
        )
    }
}

@Composable
private fun RootNavigationBar(
    selectedTab: RootTab,
    unreadCount: Int,
    onSelect: (RootTab) -> Unit,
) {
    NavigationBar(
        modifier = Modifier.height(64.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        NavigationBarItem(
            selected = selectedTab == RootTab.Plants,
            onClick = { onSelect(RootTab.Plants) },
            icon = {
                Icon(
                    painterResource(R.drawable.ic_leaf),
                    contentDescription = stringResource(R.string.navigation_plants),
                    modifier = Modifier.height(22.dp),
                )
            },
            label = { Text(stringResource(R.string.navigation_plants)) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                indicatorColor = Color.Transparent,
            ),
        )
        NavigationBarItem(
            selected = selectedTab == RootTab.Notices,
            onClick = { onSelect(RootTab.Notices) },
            icon = {
                BadgedBox(
                    badge = {
                        if (unreadCount > 0) {
                            Badge(
                                containerColor = MaterialTheme.leafyColors.accent,
                                contentColor = MaterialTheme.leafyColors.onAccent,
                            ) {
                                Text(
                                    if (unreadCount > 99) stringResource(R.string.unread_count_overflow)
                                    else unreadCount.toString(),
                                )
                            }
                        }
                    },
                ) {
                    Icon(
                        painterResource(R.drawable.ic_bell),
                        contentDescription = stringResource(R.string.navigation_notices),
                        modifier = Modifier.height(22.dp),
                    )
                }
            },
            label = { Text(stringResource(R.string.navigation_notices)) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                indicatorColor = Color.Transparent,
            ),
        )
    }
}
