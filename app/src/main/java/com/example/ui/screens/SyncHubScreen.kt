package com.example.ui.screens

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.IntegrationType
import com.example.data.model.PlatformLink
import com.example.notifications.LitigationReminderScheduler
import com.example.ui.ChambersDashboardState
import com.example.ui.components.CalendarLinkOnlyDialog
import com.example.ui.components.PlatformLinkDialog
import com.example.ui.components.formatEpochDateTime
import com.example.ui.components.openExternalUrl

@Composable
fun SyncHubScreen(
    state: ChambersDashboardState,
    isSyncing: Boolean,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onOpenShareDialog: () -> Unit,
    onOpenNotesImported: () -> Unit,
    onSyncAll: () -> Unit,
    onAddLink: (IntegrationType, String, String) -> Unit,
    onSyncLink: (PlatformLink) -> Unit,
    onDeleteLink: (String) -> Unit,
    onExportJson: (Uri) -> Unit,
    onExportCsv: (Uri) -> Unit,
    onImportFile: (Uri) -> Unit,
    onRescheduleAlerts: () -> Unit,
    onTestNotification: () -> Unit,
) {
    val context = LocalContext.current
    var hasNotifPerm by remember {
        mutableStateOf(LitigationReminderScheduler.hasNotificationPermission(context))
    }

    val notifPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifPerm = granted
        if (granted) onRescheduleAlerts()
    }

    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(onExportJson) }

    val exportCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> uri?.let(onExportCsv) }

    val importFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(onImportFile) }

    var showCalendarLinkOnlyDialog by remember { mutableStateOf(false) }
    var otherLinkDialogType by remember { mutableStateOf<IntegrationType?>(null) }

    val calendarLinks = remember(state.integrations) {
        state.integrations.filter { it.type == IntegrationType.ICS_CALENDAR.wireValue }
    }
    val otherLinks = remember(state.integrations) {
        state.integrations.filter { it.type != IntegrationType.ICS_CALENDAR.wireValue }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Sync & Settings", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        text = "Link calendars, backup files & share app",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalButton(
                    onClick = onSyncAll,
                    enabled = !isSyncing,
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("sync_hub_sync_all"),
                ) {
                    Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isSyncing) "Syncing" else "Sync")
                }
            }
        }

        // 1. Calendar Sync by Link Only (.ics / webcal://)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.CalendarMonth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Calendar Sync (Link Only)", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Connect any Google, iCloud, or Outlook calendar using only its .ics / webcal:// feed link. No account login or permissions needed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { showCalendarLinkOnlyDialog = true },
                        modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("add_ics_link_button"),
                    ) {
                        Icon(Icons.Filled.AddLink, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Link Calendar (.ics / webcal)")
                    }
                }
            }
        }

        if (calendarLinks.isNotEmpty()) {
            items(calendarLinks, key = { it.id }) { link ->
                LinkedFeedCard(
                    link = link,
                    onSync = { onSyncLink(link) },
                    onOpen = { openExternalUrl(context, link.url) },
                    onDelete = { onDeleteLink(link.id) },
                )
            }
        }

        // 2. Appearance (Light / Dark Mode) & Share App (Android / iOS)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = if (isDarkTheme) Icons.Filled.DarkMode else Icons.Filled.LightMode,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Dark / Light Theme", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = if (isDarkTheme) "Charcoal Dark Mode" else "Crisp Light Mode",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Switch(
                            checked = isDarkTheme,
                            onCheckedChange = { onToggleTheme() },
                            modifier = Modifier.testTag("sync_theme_switch"),
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedButton(
                        onClick = onOpenShareDialog,
                        modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("sync_share_app_button"),
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share App (Android & iOS)")
                    }
                }
            }
        }

        // 3. Google Sheets & Keep Links (Optional Reference)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Sheets & Keep Links", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Import cases from a shared Google Sheet link or pin Google Keep notebooks.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { otherLinkDialogType = IntegrationType.GOOGLE_SHEET },
                            modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("add_sheet_link_button"),
                        ) {
                            Icon(Icons.Filled.TableChart, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sheet Link")
                        }
                        OutlinedButton(
                            onClick = { otherLinkDialogType = IntegrationType.GOOGLE_KEEP },
                            modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("add_keep_link_button"),
                        ) {
                            Icon(Icons.Filled.StickyNote2, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Keep Link")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onOpenNotesImported,
                        modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("sync_notes_imported_button"),
                    ) {
                        Icon(Icons.Filled.StickyNote2, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Notes imported")
                    }
                }
            }
        }

        if (otherLinks.isNotEmpty()) {
            items(otherLinks, key = { it.id }) { link ->
                LinkedFeedCard(
                    link = link,
                    onSync = { onSyncLink(link) },
                    onOpen = { openExternalUrl(context, link.url) },
                    onDelete = { onDeleteLink(link.id) },
                )
            }
        }

        // 4. Local Database Files (.json / .csv)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Local Database Backup", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "All cases and calendars are stored locally on-device. Export or restore portable JSON & CSV files.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { exportJsonLauncher.launch("dilawar_saqlain_cases.json") },
                            modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("export_json_button"),
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("JSON")
                        }
                        OutlinedButton(
                            onClick = { exportCsvLauncher.launch("dilawar_saqlain_cases.csv") },
                            modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("export_csv_button"),
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("CSV")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    FilledTonalButton(
                        onClick = {
                            importFileLauncher.launch(
                                arrayOf("application/json", "text/csv", "text/comma-separated-values", "text/plain", "*/*")
                            )
                        },
                        modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("import_db_button"),
                    ) {
                        Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Import Database (.json / .csv)")
                    }
                }
            }
        }

        // 5. Automated Court & Filing Alerts
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.NotificationsActive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Automated Reminders", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (hasNotifPerm) {
                            "Hearing, filing deadline, and task notifications are active."
                        } else {
                            "Enable notifications to receive automated court date alerts."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!hasNotifPerm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            Button(
                                onClick = { notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                                modifier = Modifier.minimumInteractiveComponentSize(),
                            ) {
                                Text("Enable")
                            }
                        }
                        OutlinedButton(
                            onClick = onRescheduleAlerts,
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        ) {
                            Text("Reschedule")
                        }
                        FilledTonalButton(
                            onClick = onTestNotification,
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("test_notification_button"),
                        ) {
                            Text("Test Alert")
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(72.dp)) }
    }

    if (showCalendarLinkOnlyDialog) {
        CalendarLinkOnlyDialog(
            onDismiss = { showCalendarLinkOnlyDialog = false },
            onAddCalendarLink = { label, url ->
                onAddLink(IntegrationType.ICS_CALENDAR, label, url)
            },
        )
    }

    otherLinkDialogType?.let { initialType ->
        PlatformLinkDialog(
            initialType = initialType,
            onDismiss = { otherLinkDialogType = null },
            onAdd = onAddLink,
        )
    }
}

@Composable
private fun LinkedFeedCard(
    link: PlatformLink,
    onSync: () -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val type = IntegrationType.fromWire(link.type)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = when (type) {
                            IntegrationType.ICS_CALENDAR -> Icons.Filled.CalendarMonth
                            IntegrationType.GOOGLE_SHEET -> Icons.Filled.TableChart
                            IntegrationType.GOOGLE_KEEP -> Icons.Filled.StickyNote2
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(link.label, style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = link.url,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Row {
                    if (type != IntegrationType.GOOGLE_KEEP) {
                        IconButton(onClick = onSync) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Sync link")
                        }
                    } else {
                        OutlinedButton(onClick = onOpen) {
                            Text("Open")
                        }
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Remove link")
                    }
                }
            }
            if (link.lastSyncedEpochMs > 0L) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Synced ${link.syncedItemCount} items • ${formatEpochDateTime(link.lastSyncedEpochMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (link.lastError.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = link.lastError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
