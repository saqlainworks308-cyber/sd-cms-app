package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.LitigationCase
import com.example.data.model.SyncedCalendarEvent
import com.example.data.sync.IcsCalendarExporter
import com.example.ui.ChambersDashboardState
import com.example.ui.LitigationViewModel
import com.example.ui.components.AttachKeepToEventDialog
import com.example.ui.components.CalendarLinkOnlyDialog
import com.example.ui.components.EmptyChambersCard
import com.example.ui.components.PlatformActionPill
import com.example.ui.components.PriorityBadge
import com.example.ui.components.StatusBadge
import com.example.ui.components.formatEpochDateOnly
import com.example.ui.components.formatEpochTimeOnly
import com.example.ui.components.openExternalUrl
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun CalendarScreen(
    state: ChambersDashboardState,
    selectedDateMs: Long,
    isSyncing: Boolean,
    onSelectDate: (Long) -> Unit,
    onSyncAll: () -> Unit,
    onAddCalendarLink: (String, String) -> Unit,
    onEditCase: (LitigationCase) -> Unit,
    onAttachKeepToEvent: (SyncedCalendarEvent, String) -> Unit,
) {
    val context = LocalContext.current

    var displayedMonthOffset by remember { mutableIntStateOf(0) }
    LaunchedEffect(selectedDateMs) {
        val nowCal = Calendar.getInstance()
        val selCal = Calendar.getInstance().apply { timeInMillis = selectedDateMs }
        val offset = ((selCal.get(Calendar.YEAR) - nowCal.get(Calendar.YEAR)) * 12) +
            (selCal.get(Calendar.MONTH) - nowCal.get(Calendar.MONTH))
        if (offset != displayedMonthOffset) {
            displayedMonthOffset = offset
        }
    }
    val monthCal = remember(displayedMonthOffset) {
        Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MONTH, displayedMonthOffset)
        }
    }
    val monthTitle = remember(monthCal.timeInMillis) {
        SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(monthCal.timeInMillis))
    }

    val daysInMonth = monthCal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val firstDayOfWeek = monthCal.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY

    val dayStart = LitigationViewModel.startOfDayMs(selectedDateMs)
    val dayEnd = dayStart + 86_400_000L - 1L

    val casesOnSelectedDay = remember(state.cases, dayStart) {
        state.cases.filter {
            it.nextHearingEpochMs in dayStart..dayEnd || it.filingDeadlineEpochMs in dayStart..dayEnd
        }
    }
    val unlinkedEventsOnSelectedDay = remember(state.calendarEvents, state.cases, dayStart) {
        val allCaseIds = state.cases.map { it.id }.toSet()
        state.calendarEvents.filter { ev ->
            ev.startEpochMs in dayStart..dayEnd &&
                (ev.linkedCaseId.isBlank() || !allCaseIds.contains(ev.linkedCaseId))
        }
    }

    val hearingDays = remember(state.cases, monthCal.timeInMillis) {
        val set = mutableSetOf<Int>()
        state.cases.forEach { c ->
            listOf(c.nextHearingEpochMs, c.filingDeadlineEpochMs).filter { it > 0L }.forEach { ms ->
                val cCal = Calendar.getInstance().apply { timeInMillis = ms }
                if (cCal.get(Calendar.YEAR) == monthCal.get(Calendar.YEAR) &&
                    cCal.get(Calendar.MONTH) == monthCal.get(Calendar.MONTH)
                ) {
                    set.add(cCal.get(Calendar.DAY_OF_MONTH))
                }
            }
        }
        set
    }
    val externalEventDays = remember(state.calendarEvents, monthCal.timeInMillis) {
        val set = mutableSetOf<Int>()
        state.calendarEvents.forEach { ev ->
            val eCal = Calendar.getInstance().apply { timeInMillis = ev.startEpochMs }
            if (eCal.get(Calendar.YEAR) == monthCal.get(Calendar.YEAR) &&
                eCal.get(Calendar.MONTH) == monthCal.get(Calendar.MONTH)
            ) {
                set.add(eCal.get(Calendar.DAY_OF_MONTH))
            }
        }
        set
    }

    var eventForKeepDialog by remember { mutableStateOf<SyncedCalendarEvent?>(null) }
    var showAddCalLinkDialog by remember { mutableStateOf(false) }

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
                Text(
                    text = "Calendar",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(
                        onClick = { IcsCalendarExporter.shareCasesAsIcsFeed(context, state.cases) },
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("calendar_export_ics_button"),
                    ) {
                        Icon(Icons.Filled.IosShare, contentDescription = "Sync dates to external calendar (.ics)")
                    }
                    OutlinedButton(
                        onClick = { showAddCalLinkDialog = true },
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("calendar_link_button"),
                    ) {
                        Icon(Icons.Filled.AddLink, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Link .ics")
                    }
                    FilledTonalButton(
                        onClick = onSyncAll,
                        enabled = !isSyncing,
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("calendar_sync_button"),
                    ) {
                        Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isSyncing) "..." else "Sync")
                    }
                }
            }
        }

        // Monochrome Month Grid
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { displayedMonthOffset-- }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(monthTitle, style = MaterialTheme.typography.titleMedium)
                            TextButton(
                                onClick = {
                                    displayedMonthOffset = 0
                                    onSelectDate(System.currentTimeMillis())
                                }
                            ) { Text("Today") }
                        }
                        IconButton(onClick = { displayedMonthOffset++ }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth()) {
                        listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").forEach { dayName ->
                            Text(
                                text = dayName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    val totalCells = firstDayOfWeek + daysInMonth
                    val rows = (totalCells + 6) / 7
                    for (r in 0 until rows) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            for (c in 0 until 7) {
                                val cellIndex = r * 7 + c
                                val dayNum = cellIndex - firstDayOfWeek + 1
                                if (dayNum in 1..daysInMonth) {
                                    val cellDateMs = (monthCal.clone() as Calendar).apply {
                                        set(Calendar.DAY_OF_MONTH, dayNum)
                                    }.timeInMillis
                                    val isSelected = LitigationViewModel.startOfDayMs(cellDateMs) == dayStart
                                    val isToday = LitigationViewModel.startOfDayMs(cellDateMs) ==
                                        LitigationViewModel.startOfDayMs(System.currentTimeMillis())
                                    val hasCase = hearingDays.contains(dayNum)
                                    val hasCal = externalEventDays.contains(dayNum)

                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .padding(2.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                when {
                                                    isSelected -> MaterialTheme.colorScheme.primary
                                                    isToday -> MaterialTheme.colorScheme.surfaceVariant
                                                    else -> Color.Transparent
                                                }
                                            )
                                            .clickable { onSelectDate(cellDateMs) },
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = dayNum.toString(),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                                color = when {
                                                    isSelected -> MaterialTheme.colorScheme.onPrimary
                                                    isToday -> MaterialTheme.colorScheme.onSurface
                                                    else -> MaterialTheme.colorScheme.onSurface
                                                },
                                            )
                                            if (hasCase || hasCal) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                                    if (hasCase) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(5.dp)
                                                                .clip(CircleShape)
                                                                .background(
                                                                    if (isSelected) MaterialTheme.colorScheme.onPrimary
                                                                    else MaterialTheme.colorScheme.primary
                                                                )
                                                        )
                                                    }
                                                    if (hasCal && !hasCase) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(5.dp)
                                                                .clip(CircleShape)
                                                                .background(
                                                                    if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                                                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                                                )
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LegendDot(color = MaterialTheme.colorScheme.primary, label = "Scheduled Case")
                        LegendDot(color = MaterialTheme.colorScheme.onSurfaceVariant, label = "Linked Calendar")
                    }
                }
            }
        }

        item {
            Text(
                text = formatEpochDateOnly(selectedDateMs),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }

        if (casesOnSelectedDay.isEmpty() && unlinkedEventsOnSelectedDay.isEmpty()) {
            item {
                EmptyChambersCard(
                    icon = Icons.Filled.CalendarMonth,
                    title = "No Events on ${formatEpochDateOnly(selectedDateMs)}",
                    subtitle = "Tap 'Link .ics' above to import calendar events automatically as editable cases.",
                )
            }
        } else {
            itemsIndexed(casesOnSelectedDay, key = { _, c -> "cal_case_${c.id}" }) { index, c ->
                val isDeadline = c.filingDeadlineEpochMs in dayStart..dayEnd && c.nextHearingEpochMs !in dayStart..dayEnd
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
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = if (isDeadline) "DEADLINE • ${c.caseNumber}" else "${formatEpochTimeOnly(c.nextHearingEpochMs)} • ${c.caseNumber}",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isDeadline) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                StatusBadge(c.status)
                                PriorityBadge(c.priority)
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = c.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (c.courtName.isNotBlank()) {
                            Text(
                                text = listOf(c.courtName, c.courtRoom).filter { it.isNotBlank() }.joinToString(" • "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (c.notes.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = c.notes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Tap card to edit case",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (c.googleKeepUrl.isNotBlank()) {
                                PlatformActionPill(
                                    icon = Icons.Filled.StickyNote2,
                                    label = "Open Keep",
                                    onClick = { openExternalUrl(context, c.googleKeepUrl) },
                                )
                            }
                        }
                    }
                }
            }

            itemsIndexed(unlinkedEventsOnSelectedDay, key = { _, ev -> "cal_evt_${ev.id}" }) { index, ev ->
                val matchedCase = state.cases.firstOrNull {
                    it.id == ev.linkedCaseId || it.calendarEventId == ev.externalUid
                }
                val cardColor = if ((casesOnSelectedDay.size + index) % 2 == 0) {
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
                        .clickable(enabled = matchedCase != null) {
                            matchedCase?.let(onEditCase)
                        },
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = if (ev.allDay) "ALL DAY • ${ev.sourceLabel.ifBlank { "Linked Calendar" }}" else "${formatEpochTimeOnly(ev.startEpochMs)} • ${ev.sourceLabel.ifBlank { "Linked Calendar" }}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = ev.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (ev.location.isNotBlank()) {
                            Text(
                                text = ev.location,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (ev.description.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = ev.description,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        if (ev.keepNoteUrl.isNotBlank()) {
                            PlatformActionPill(
                                icon = Icons.Filled.StickyNote2,
                                label = "Open Keep",
                                onClick = { openExternalUrl(context, ev.keepNoteUrl) },
                            )
                        } else {
                            OutlinedButton(
                                onClick = { eventForKeepDialog = ev },
                                modifier = Modifier.minimumInteractiveComponentSize(),
                            ) {
                                Icon(Icons.Filled.StickyNote2, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Link Keep")
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(72.dp)) }
    }

    eventForKeepDialog?.let { targetEvent ->
        AttachKeepToEventDialog(
            event = targetEvent,
            onDismiss = { eventForKeepDialog = null },
            onAttach = { keepUrl -> onAttachKeepToEvent(targetEvent, keepUrl) },
        )
    }

    if (showAddCalLinkDialog) {
        CalendarLinkOnlyDialog(
            onDismiss = { showAddCalLinkDialog = false },
            onAddCalendarLink = onAddCalendarLink,
        )
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
