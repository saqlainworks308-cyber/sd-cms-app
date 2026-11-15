package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FolderCopy
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.model.IntegrationType
import com.example.data.model.LitigationCase
import com.example.data.model.LitigationTask
import com.example.notifications.LitigationReminderScheduler
import com.example.ui.ChambersDashboardState
import com.example.ui.LitigationViewModel
import com.example.ui.UiState
import com.example.ui.components.CaseEditorDialog
import com.example.ui.components.CauseListEmailDialog
import com.example.ui.components.NotesImportedDialog
import com.example.ui.components.ShareAppDialog
import com.example.ui.components.TaskEditorDialog
import com.example.ui.screens.CalendarScreen
import com.example.ui.screens.CasesListScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DiaryScreen
import com.example.ui.screens.SyncHubScreen
import com.example.ui.theme.DilawarSaqlainTheme
import com.example.ui.theme.ThemePreferenceStore

enum class ChambersRoute(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    HOME("home", "Home", Icons.Filled.Home, Icons.Outlined.Home),
    DIARY("diary", "Diary", Icons.Filled.Book, Icons.Outlined.Book),
    CALENDAR("calendar", "Calendar", Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth),
    CASES("cases", "Cases", Icons.Filled.FolderCopy, Icons.Outlined.FolderCopy),
    SYNC("sync", "Sync", Icons.Filled.Sync, Icons.Outlined.Sync),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        LitigationReminderScheduler.ensureNotificationChannel(this)
        setContent {
            val systemDark = isSystemInDarkTheme()
            var isDarkTheme by remember {
                mutableStateOf(ThemePreferenceStore.isDarkMode(this, systemDark))
            }

            DilawarSaqlainTheme(darkTheme = isDarkTheme) {
                ChambersAppShell(
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = {
                        val next = !isDarkTheme
                        isDarkTheme = next
                        ThemePreferenceStore.setDarkMode(this, next)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChambersAppShell(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
) {
    val context = LocalContext.current
    val vm: LitigationViewModel = viewModel(
        factory = LitigationViewModel.factory(context = context),
    )

    val uiState by vm.dashboardState.collectAsStateWithLifecycle()
    val isSyncing by vm.isSyncing.collectAsStateWithLifecycle()
    val statusMessage by vm.statusMessage.collectAsStateWithLifecycle()
    val selectedDateMs by vm.selectedDateMs.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(statusMessage) {
        val msg = statusMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        vm.clearStatusMessage()
    }

    val notifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) vm.rescheduleAllNotifications()
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !LitigationReminderScheduler.hasNotificationPermission(context)
        ) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: ChambersRoute.HOME.route

    var editingCase by remember { mutableStateOf<LitigationCase?>(null) }
    var showCaseDialog by remember { mutableStateOf(false) }

    var editingTask by remember { mutableStateOf<LitigationTask?>(null) }
    var showTaskDialog by remember { mutableStateOf(false) }

    var showCauseListDialog by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }
    var showNotesImportedDialog by remember { mutableStateOf(false) }

    fun navigateTo(route: ChambersRoute) {
        navController.navigate(route.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = R.drawable.sd_cms_logo),
                                contentDescription = "SD-CMS Logo",
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "SD-CMS",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = "Dilawar & Saqlain • Case Management",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = onToggleTheme,
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("theme_toggle_button"),
                        ) {
                            Icon(
                                imageVector = if (isDarkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                                contentDescription = if (isDarkTheme) "Switch to light mode" else "Switch to dark mode",
                            )
                        }
                        IconButton(
                            onClick = { showShareDialog = true },
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("share_app_top_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Share,
                                contentDescription = "Share app for Android & iOS",
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                )
            },
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        ChambersRoute.entries.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTo(dest) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label,
                                    )
                                },
                                label = { Text(dest.label, maxLines = 1) },
                                modifier = Modifier.testTag("nav_${dest.route}"),
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                if (currentRoute != ChambersRoute.SYNC.route) {
                    ExtendedFloatingActionButton(
                        onClick = {
                            editingCase = null
                            showCaseDialog = true
                        },
                        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                        text = { Text("New Case") },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("fab_new_case"),
                    )
                }
            },
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (useNavRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight(),
                    ) {
                        ChambersRoute.entries.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTo(dest) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label,
                                    )
                                },
                                label = { Text(dest.label) },
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    val contentModifier = Modifier
                        .widthIn(max = 920.dp)
                        .fillMaxSize()

                    when (val state = uiState) {
                        is UiState.Loading -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                        is UiState.Error -> {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = state.message,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                    Button(
                                        onClick = { vm.syncAllConnectedPlatforms() },
                                        modifier = Modifier.padding(top = 12.dp),
                                    ) {
                                        Text("Retry")
                                    }
                                }
                            }
                        }
                        is UiState.Success<ChambersDashboardState> -> {
                            val data = state.data
                            NavHost(
                                navController = navController,
                                startDestination = ChambersRoute.HOME.route,
                                modifier = contentModifier,
                            ) {
                                composable(ChambersRoute.HOME.route) {
                                    DashboardScreen(
                                        state = data,
                                        isSyncing = isSyncing,
                                        onSyncAll = { vm.syncAllConnectedPlatforms() },
                                        onOpenDiary = { navigateTo(ChambersRoute.DIARY) },
                                        onOpenCases = { navigateTo(ChambersRoute.CASES) },
                                        onAddCase = {
                                            editingCase = null
                                            showCaseDialog = true
                                        },
                                        onEditCase = { c ->
                                            editingCase = c
                                            showCaseDialog = true
                                        },
                                        onAddTask = {
                                            editingTask = null
                                            showTaskDialog = true
                                        },
                                        onEditTask = { t ->
                                            editingTask = t
                                            showTaskDialog = true
                                        },
                                        onToggleTask = { t -> vm.toggleTaskCompleted(t) },
                                        onDeleteTask = { id -> vm.deleteTask(id) },
                                        onOpenCauseListDialog = { showCauseListDialog = true },
                                    )
                                }
                                composable(ChambersRoute.DIARY.route) {
                                    DiaryScreen(
                                        state = data,
                                        onEditCase = { c ->
                                            editingCase = c
                                            showCaseDialog = true
                                        },
                                        onAdjournCase = { c, days -> vm.adjournCaseByDays(c, days) },
                                        onAddCase = {
                                            editingCase = null
                                            showCaseDialog = true
                                        },
                                        onEditTask = { t ->
                                            editingTask = t
                                            showTaskDialog = true
                                        },
                                    )
                                }
                                composable(ChambersRoute.CALENDAR.route) {
                                    CalendarScreen(
                                        state = data,
                                        selectedDateMs = selectedDateMs,
                                        isSyncing = isSyncing,
                                        onSelectDate = { vm.selectDate(it) },
                                        onSyncAll = { vm.syncAllConnectedPlatforms() },
                                        onAddCalendarLink = { label, url ->
                                            vm.addPlatformLink(IntegrationType.ICS_CALENDAR, label, url)
                                        },
                                        onEditCase = { c ->
                                            editingCase = c
                                            showCaseDialog = true
                                        },
                                        onAttachKeepToEvent = { ev, url -> vm.linkKeepNoteToEvent(ev, url) },
                                    )
                                }
                                composable(ChambersRoute.CASES.route) {
                                    CasesListScreen(
                                        state = data,
                                        onAddCase = {
                                            editingCase = null
                                            showCaseDialog = true
                                        },
                                        onEditCase = { c ->
                                            editingCase = c
                                            showCaseDialog = true
                                        },
                                    )
                                }
                                composable(ChambersRoute.SYNC.route) {
                                    SyncHubScreen(
                                        state = data,
                                        isSyncing = isSyncing,
                                        isDarkTheme = isDarkTheme,
                                        onToggleTheme = onToggleTheme,
                                        onOpenShareDialog = { showShareDialog = true },
                                        onOpenNotesImported = { showNotesImportedDialog = true },
                                        onSyncAll = { vm.syncAllConnectedPlatforms() },
                                        onAddLink = { type, label, url -> vm.addPlatformLink(type, label, url) },
                                        onSyncLink = { link ->
                                            when (link.type) {
                                                IntegrationType.GOOGLE_SHEET.wireValue -> vm.syncSingleGoogleSheetLink(link)
                                                else -> vm.syncSingleIcsLink(link)
                                            }
                                        },
                                        onDeleteLink = { id -> vm.deletePlatformLink(id) },
                                        onExportJson = { uri -> vm.exportDatabaseJson(uri) },
                                        onExportCsv = { uri -> vm.exportDatabaseCsv(uri) },
                                        onImportFile = { uri -> vm.importDatabaseFile(uri) },
                                        onRescheduleAlerts = { vm.rescheduleAllNotifications() },
                                        onTestNotification = { vm.triggerInstantTestNotification() },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCaseDialog) {
        CaseEditorDialog(
            initialCase = editingCase,
            onDismiss = { showCaseDialog = false },
            onSave = { updated -> vm.saveCase(updated) },
            onDelete = { id -> vm.deleteCase(id) },
        )
    }

    if (showTaskDialog) {
        val currentCases = (uiState as? UiState.Success)?.data?.cases.orEmpty()
        TaskEditorDialog(
            cases = currentCases,
            initialTask = editingTask,
            onDismiss = { showTaskDialog = false },
            onSave = { task -> vm.saveTask(task) },
        )
    }

    if (showCauseListDialog) {
        val currentData = (uiState as? UiState.Success)?.data
        if (currentData != null) {
            CauseListEmailDialog(
                state = currentData,
                onDismiss = { showCauseListDialog = false },
            )
        }
    }

    if (showNotesImportedDialog) {
        val currentData = (uiState as? UiState.Success)?.data
        if (currentData != null) {
            NotesImportedDialog(
                state = currentData,
                onEditCase = { c ->
                    editingCase = c
                    showCaseDialog = true
                },
                onDismiss = { showNotesImportedDialog = false },
            )
        }
    }

    if (showShareDialog) {
        val currentData = (uiState as? UiState.Success)?.data ?: ChambersDashboardState()
        ShareAppDialog(
            state = currentData,
            onDismiss = { showShareDialog = false },
        )
    }
}
