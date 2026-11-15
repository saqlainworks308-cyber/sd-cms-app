package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddTask
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.LitigationCase
import com.example.data.model.LitigationTask
import com.example.ui.ChambersDashboardState
import com.example.ui.LitigationViewModel
import com.example.ui.components.EmptyChambersCard
import com.example.ui.components.PriorityBadge
import com.example.ui.components.formatEpochDateOnly
import com.example.ui.components.formatEpochTimeOnly
import com.example.ui.components.openExternalUrl

@Composable
fun DashboardScreen(
    state: ChambersDashboardState,
    isSyncing: Boolean,
    onSyncAll: () -> Unit,
    onOpenDiary: () -> Unit,
    onOpenCases: () -> Unit,
    onAddCase: () -> Unit,
    onEditCase: (LitigationCase) -> Unit,
    onAddTask: () -> Unit,
    onEditTask: (LitigationTask) -> Unit,
    onToggleTask: (LitigationTask) -> Unit,
    onDeleteTask: (String) -> Unit,
    onOpenCauseListDialog: () -> Unit,
) {
    val context = LocalContext.current
    val nowMs = System.currentTimeMillis()
    val todayStart = LitigationViewModel.startOfDayMs(nowMs)
    val todayEnd = todayStart + 86_400_000L - 1L

    val todaysCases = remember(state.cases, todayStart) {
        state.cases.filter { it.nextHearingEpochMs in todayStart..todayEnd }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // Minimal Charcoal/Surface Daily Brief Card
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "DAILY BRIEF",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = formatEpochDateOnly(nowMs),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                        OutlinedButton(
                            onClick = onSyncAll,
                            enabled = !isSyncing,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.35f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("dashboard_sync_button"),
                        ) {
                            Icon(Icons.Filled.Sync, contentDescription = "Sync calendar links", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isSyncing) "Syncing" else "Sync")
                        }
                    }

                    if (isSyncing) {
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.onPrimary,
                            trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f),
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = onOpenDiary,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                            modifier = Modifier.weight(1f).minimumInteractiveComponentSize(),
                        ) {
                            Icon(Icons.Filled.Gavel, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Diary (${todaysCases.size})")
                        }
                        OutlinedButton(
                            onClick = onOpenCauseListDialog,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.35f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                            modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("dashboard_email_cause_list"),
                        ) {
                            Icon(Icons.Filled.MailOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Cause List")
                        }
                    }
                }
            }
        }

        // Total Cases Summary Card
        item {
            DashboardActionTile(
                icon = Icons.Filled.FolderCopy,
                label = "Total Cases",
                value = state.cases.size.toString(),
                onClick = onOpenCases,
                modifier = Modifier.fillMaxWidth().testTag("dashboard_cases_count_card"),
            )
        }

        // Priority Tasks Section
        item {
            SectionHeader(
                title = "Priority Tasks",
                actionLabel = "+ Add",
                onAction = onAddTask,
                testTag = "add_task_button",
            )
        }

        if (state.tasks.isEmpty()) {
            item {
                EmptyChambersCard(
                    icon = Icons.Filled.AddTask,
                    title = "No Priority Tasks",
                    subtitle = "Add filing prep, client follow-ups, or hearing tasks.",
                    actionLabel = "Add Task",
                    onAction = onAddTask,
                )
            }
        } else {
            items(state.tasks.take(5), key = { it.id }) { task ->
                TaskRowCard(
                    task = task,
                    onToggle = { onToggleTask(task) },
                    onEdit = { onEditTask(task) },
                    onDelete = { onDeleteTask(task.id) },
                    onOpenKeep = { openExternalUrl(context, task.keepNoteUrl) },
                )
            }
        }

        // Today's Cases Schedule Preview
        item {
            SectionHeader(
                title = "Today's Schedule",
                actionLabel = "Full Diary",
                onAction = onOpenDiary,
            )
        }

        if (todaysCases.isEmpty()) {
            item {
                EmptyChambersCard(
                    icon = Icons.Filled.EventAvailable,
                    title = "Clear Schedule Today",
                    subtitle = "No court cases scheduled for today.",
                    actionLabel = "Add Case",
                    onAction = onAddCase,
                )
            }
        } else {
            itemsIndexed(todaysCases, key = { _, c -> "dash_case_${c.id}" }) { index, c ->
                val cardColor = if (index % 2 == 0) {
                    MaterialTheme.colorScheme.surface
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = cardColor),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onEditCase(c) },
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = formatEpochTimeOnly(c.nextHearingEpochMs),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(72.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = c.caseNumber,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = c.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (c.courtName.isNotBlank()) {
                                Text(
                                    text = listOf(c.courtName, c.courtRoom).filter { it.isNotBlank() }.joinToString(" • "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        PriorityBadge(c.priority)
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(72.dp)) }
    }
}

@Composable
private fun DashboardActionTile(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    actionLabel: String?,
    onAction: () -> Unit,
    testTag: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (actionLabel != null) {
            TextButton(
                onClick = onAction,
                modifier = if (testTag != null) {
                    Modifier.minimumInteractiveComponentSize().testTag(testTag)
                } else {
                    Modifier.minimumInteractiveComponentSize()
                },
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun TaskRowCard(
    task: LitigationTask,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onOpenKeep: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onToggle,
                modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
                Icon(
                    imageVector = if (task.completed) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                    contentDescription = if (task.completed) "Mark incomplete" else "Mark complete",
                    tint = if (task.completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleSmall,
                    textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (task.completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (task.caseNumber.isNotBlank()) {
                        Text(
                            text = task.caseNumber,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (task.dueEpochMs > 0L) {
                        Text(
                            text = formatEpochDateOnly(task.dueEpochMs),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (task.keepNoteUrl.isNotBlank()) {
                IconButton(onClick = onOpenKeep) {
                    Icon(
                        imageVector = Icons.Filled.StickyNote2,
                        contentDescription = "Open Google Keep note",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = "Delete task",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
