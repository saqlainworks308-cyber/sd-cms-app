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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.LitigationCase
import com.example.data.model.LitigationTask
import com.example.data.model.SyncedCalendarEvent
import com.example.ui.ChambersDashboardState
import com.example.ui.LitigationViewModel
import com.example.ui.components.CauseListEmailDialog
import com.example.ui.components.EmptyChambersCard
import com.example.ui.components.PriorityBadge
import com.example.ui.components.StatusBadge
import com.example.ui.components.formatEpochDateOnly
import com.example.ui.components.formatEpochTimeOnly
import com.example.ui.components.openExternalUrl
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class DiaryPeriod(val label: String) {
    DAILY("Daily"),
    WEEK("This Week"),
    MONTH("This Month"),
}

private sealed interface DiaryEntry {
    val sortMs: Long
    val dayKeyMs: Long

    data class Hearing(val item: LitigationCase) : DiaryEntry {
        override val sortMs: Long get() = item.nextHearingEpochMs
        override val dayKeyMs: Long get() = LitigationViewModel.startOfDayMs(item.nextHearingEpochMs)
    }

    data class Deadline(val item: LitigationCase) : DiaryEntry {
        override val sortMs: Long get() = item.filingDeadlineEpochMs
        override val dayKeyMs: Long get() = LitigationViewModel.startOfDayMs(item.filingDeadlineEpochMs)
    }

    data class CalendarItem(val item: SyncedCalendarEvent) : DiaryEntry {
        override val sortMs: Long get() = item.startEpochMs
        override val dayKeyMs: Long get() = LitigationViewModel.startOfDayMs(item.startEpochMs)
    }

    data class TaskItem(val item: LitigationTask) : DiaryEntry {
        override val sortMs: Long get() = item.dueEpochMs
        override val dayKeyMs: Long get() = LitigationViewModel.startOfDayMs(item.dueEpochMs)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryScreen(
    state: ChambersDashboardState,
    onEditCase: (LitigationCase) -> Unit,
    onAdjournCase: (LitigationCase, Int) -> Unit,
    onAddCase: () -> Unit,
    onEditTask: (LitigationTask) -> Unit,
) {
    val context = LocalContext.current

    var selectedPeriod by remember { mutableStateOf(DiaryPeriod.DAILY) }
    var periodOffset by remember { mutableIntStateOf(0) }
    var showEmailDialog by remember { mutableStateOf(false) }

    val (rangeStartMs, rangeEndMs, rangeTitle) = remember(selectedPeriod, periodOffset) {
        computeDiaryBounds(selectedPeriod, periodOffset)
    }

    val groupedEntries = remember(state, rangeStartMs, rangeEndMs) {
        val entries = mutableListOf<DiaryEntry>()
        val allCaseIds = state.cases.map { it.id }.toSet()

        state.cases.forEach { c ->
            if (c.nextHearingEpochMs in rangeStartMs..rangeEndMs) {
                entries.add(DiaryEntry.Hearing(c))
            }
            if (c.filingDeadlineEpochMs in rangeStartMs..rangeEndMs) {
                entries.add(DiaryEntry.Deadline(c))
            }
        }
        state.calendarEvents.forEach { ev ->
            val alreadyCase = ev.linkedCaseId.isNotBlank() && allCaseIds.contains(ev.linkedCaseId)
            if (!alreadyCase && ev.startEpochMs in rangeStartMs..rangeEndMs) {
                entries.add(DiaryEntry.CalendarItem(ev))
            }
        }
        state.tasks.filter { !it.completed && it.dueEpochMs in rangeStartMs..rangeEndMs }.forEach { t ->
            entries.add(DiaryEntry.TaskItem(t))
        }
        entries.sortedBy { it.sortMs }.groupBy { it.dayKeyMs }.toSortedMap()
    }

    val totalHearings = remember(groupedEntries) {
        groupedEntries.values.flatten().count { it is DiaryEntry.Hearing || it is DiaryEntry.CalendarItem }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Court Diary", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        text = "$totalHearings scheduled items",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(
                    onClick = { showEmailDialog = true },
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("send_cause_list_button"),
                ) {
                    Icon(Icons.Filled.MailOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Email List")
                }
            }
        }

        // Daily / This Week / This Month Segmented Selector
        item {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth().testTag("diary_period_selector"),
            ) {
                DiaryPeriod.entries.forEachIndexed { index, period ->
                    SegmentedButton(
                        selected = selectedPeriod == period,
                        onClick = {
                            selectedPeriod = period
                            periodOffset = 0
                        },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = DiaryPeriod.entries.size,
                        ),
                        modifier = Modifier.testTag("diary_tab_${period.name.lowercase(Locale.US)}"),
                    ) {
                        Text(period.label, maxLines = 1)
                    }
                }
            }
        }

        // Period Navigation Bar
        item {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    IconButton(onClick = { periodOffset-- }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous period")
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = rangeTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (periodOffset != 0) {
                            TextButton(onClick = { periodOffset = 0 }) {
                                Text("Back to Current", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    IconButton(onClick = { periodOffset++ }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next period")
                    }
                }
            }
        }

        if (groupedEntries.isEmpty()) {
            item {
                EmptyChambersCard(
                    icon = Icons.Filled.Gavel,
                    title = "No Entries in ${selectedPeriod.label} Diary",
                    subtitle = "Hearings and cases imported from linked calendars appear here automatically.",
                    actionLabel = "Add Case",
                    onAction = onAddCase,
                )
            }
        } else {
            groupedEntries.forEach { (dayMs, dayEntries) ->
                item(key = "header_$dayMs") {
                    Text(
                        text = SimpleDateFormat("EEEE, dd MMM yyyy", Locale.getDefault()).format(Date(dayMs)),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                    )
                }

                itemsIndexed(
                    items = dayEntries,
                    key = { _, entry ->
                        when (entry) {
                            is DiaryEntry.Hearing -> "h_${entry.item.id}_$dayMs"
                            is DiaryEntry.Deadline -> "d_${entry.item.id}_$dayMs"
                            is DiaryEntry.CalendarItem -> "c_${entry.item.id}_$dayMs"
                            is DiaryEntry.TaskItem -> "t_${entry.item.id}_$dayMs"
                        }
                    }
                ) { index, entry ->
                    val alternatingColor = if (index % 2 == 0) {
                        MaterialTheme.colorScheme.surface
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                    when (entry) {
                        is DiaryEntry.Hearing -> {
                            val c = entry.item
                            Card(
                                colors = CardDefaults.cardColors(containerColor = alternatingColor),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onEditCase(c) },
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = "${formatEpochTimeOnly(c.nextHearingEpochMs)} • ${c.caseNumber}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            StatusBadge(c.status)
                                            PriorityBadge(c.priority)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = c.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    val venue = listOf(c.courtName, c.courtRoom, c.stage)
                                        .filter { it.isNotBlank() }
                                        .joinToString(" • ")
                                    if (venue.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = venue,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = { onAdjournCase(c, 7) },
                                            modifier = Modifier.minimumInteractiveComponentSize(),
                                        ) {
                                            Text("Adjourn +7d")
                                        }
                                        if (c.googleKeepUrl.isNotBlank()) {
                                            OutlinedButton(
                                                onClick = { openExternalUrl(context, c.googleKeepUrl) },
                                                modifier = Modifier.minimumInteractiveComponentSize(),
                                            ) {
                                                Icon(Icons.Filled.StickyNote2, contentDescription = null, modifier = Modifier.size(15.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Keep Note")
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        is DiaryEntry.Deadline -> {
                            val c = entry.item
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onEditCase(c) },
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "FILING • ${c.caseNumber}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                        Text(
                                            text = c.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                    PriorityBadge(c.priority)
                                }
                            }
                        }

                        is DiaryEntry.CalendarItem -> {
                            val ev = entry.item
                            val linkedCase = state.cases.firstOrNull {
                                it.id == ev.linkedCaseId || it.calendarEventId == ev.externalUid
                            }
                            Card(
                                colors = CardDefaults.cardColors(containerColor = alternatingColor),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = linkedCase != null) {
                                        linkedCase?.let(onEditCase)
                                    },
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Filled.Schedule,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "${if (ev.allDay) "All Day" else formatEpochTimeOnly(ev.startEpochMs)} • ${ev.sourceLabel.ifBlank { "Linked Calendar" }}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = ev.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    if (ev.location.isNotBlank()) {
                                        Text(
                                            text = ev.location,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    if (ev.keepNoteUrl.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        OutlinedButton(
                                            onClick = { openExternalUrl(context, ev.keepNoteUrl) },
                                            modifier = Modifier.minimumInteractiveComponentSize(),
                                        ) {
                                            Icon(Icons.Filled.StickyNote2, contentDescription = null, modifier = Modifier.size(15.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Keep Note")
                                        }
                                    }
                                }
                            }
                        }

                        is DiaryEntry.TaskItem -> {
                            val t = entry.item
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onEditTask(t) },
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.TaskAlt,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(t.title, style = MaterialTheme.typography.titleSmall)
                                        if (t.caseNumber.isNotBlank()) {
                                            Text(
                                                text = "Task • ${t.caseNumber}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                    PriorityBadge(t.priority)
                                }
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(72.dp)) }
    }

    if (showEmailDialog) {
        val presetIdx = when (selectedPeriod) {
            DiaryPeriod.DAILY -> 0
            DiaryPeriod.WEEK -> 1
            DiaryPeriod.MONTH -> 2
        }
        CauseListEmailDialog(
            state = state,
            initialStartDateMs = rangeStartMs,
            initialEndDateMs = rangeEndMs,
            initialPresetIndex = presetIdx,
            onDismiss = { showEmailDialog = false },
        )
    }
}

private fun computeDiaryBounds(period: DiaryPeriod, offset: Int): Triple<Long, Long, String> {
    val startCal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return when (period) {
        DiaryPeriod.DAILY -> {
            startCal.add(Calendar.DAY_OF_YEAR, offset)
            val start = startCal.timeInMillis
            val end = start + 86_400_000L - 1L
            val label = formatEpochDateOnly(start)
            Triple(start, end, label)
        }
        DiaryPeriod.WEEK -> {
            startCal.firstDayOfWeek = Calendar.MONDAY
            startCal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            startCal.add(Calendar.WEEK_OF_YEAR, offset)
            val start = startCal.timeInMillis
            val endCal = (startCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 6) }
            val end = endCal.timeInMillis + 86_400_000L - 1L
            val fmt = SimpleDateFormat("dd MMM", Locale.getDefault())
            val yrFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            Triple(start, end, "${fmt.format(Date(start))} – ${yrFmt.format(Date(end))}")
        }
        DiaryPeriod.MONTH -> {
            startCal.set(Calendar.DAY_OF_MONTH, 1)
            startCal.add(Calendar.MONTH, offset)
            val start = startCal.timeInMillis
            val days = startCal.getActualMaximum(Calendar.DAY_OF_MONTH)
            val endCal = (startCal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, days) }
            val end = endCal.timeInMillis + 86_400_000L - 1L
            val label = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(start))
            Triple(start, end, label)
        }
    }
}
