package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.CasePriority
import com.example.data.model.CaseStatus
import com.example.data.model.IntegrationType
import com.example.data.model.LitigationCase
import com.example.data.model.LitigationTask
import com.example.data.model.SyncedCalendarEvent
import com.example.data.model.TaskCategory
import com.example.data.sync.IcsCalendarExporter
import java.util.Calendar
import java.util.TimeZone

private fun localDateToUtcPickerMs(localMs: Long): Long {
    val src = Calendar.getInstance().apply {
        timeInMillis = if (localMs > 0L) localMs else System.currentTimeMillis()
    }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(src.get(Calendar.YEAR), src.get(Calendar.MONTH), src.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
    }.timeInMillis
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CaseEditorDialog(
    initialCase: LitigationCase?,
    onDismiss: () -> Unit,
    onSave: (LitigationCase) -> Unit,
    onDelete: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val isEditing = initialCase != null && initialCase.id.isNotBlank()
    var caseNumber by remember { mutableStateOf(initialCase?.caseNumber.orEmpty()) }
    var title by remember { mutableStateOf(initialCase?.title.orEmpty()) }
    var courtName by remember { mutableStateOf(initialCase?.courtName.orEmpty()) }
    var courtRoom by remember { mutableStateOf(initialCase?.courtRoom.orEmpty()) }
    var judgeName by remember { mutableStateOf(initialCase?.judgeName.orEmpty()) }
    var clientName by remember { mutableStateOf(initialCase?.clientName.orEmpty()) }
    var clientEmail by remember { mutableStateOf(initialCase?.clientEmail.orEmpty()) }
    var opposingParty by remember { mutableStateOf(initialCase?.opposingParty.orEmpty()) }
    var stage by remember { mutableStateOf(initialCase?.stage.orEmpty()) }
    var status by remember { mutableStateOf(initialCase?.status ?: CaseStatus.ACTIVE.wireValue) }
    var priority by remember { mutableStateOf(initialCase?.priority ?: CasePriority.STANDARD.wireValue) }
    var nextHearingMs by remember { mutableLongStateOf(initialCase?.nextHearingEpochMs ?: 0L) }
    var filingDeadlineMs by remember { mutableLongStateOf(initialCase?.filingDeadlineEpochMs ?: 0L) }
    var reminderMinutes by remember { mutableLongStateOf(initialCase?.reminderMinutesBefore ?: 120L) }
    var keepUrl by remember { mutableStateOf(initialCase?.googleKeepUrl.orEmpty()) }
    var keepLabel by remember { mutableStateOf(initialCase?.googleKeepLabel.orEmpty()) }
    var sheetUrl by remember { mutableStateOf(initialCase?.googleSheetUrl.orEmpty()) }
    var notes by remember { mutableStateOf(initialCase?.notes.orEmpty()) }

    var activePickerTarget by remember { mutableStateOf<String?>(null) }
    var showTimePickerForHearing by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (isEditing) "Edit Case" else "New Case",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Row {
                        if (isEditing && onDelete != null) {
                            IconButton(
                                onClick = {
                                    onDelete(initialCase!!.id)
                                    onDismiss()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "Delete case",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = "Close editor")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = caseNumber,
                    onValueChange = { caseNumber = it },
                    label = { Text("Case / Docket Number *") },
                    placeholder = { Text("e.g., WP-1402/2026") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("case_number_input"),
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Case Title / Parties *") },
                    placeholder = { Text("e.g., Dilawar Textile Mills v. Commissioner") },
                    modifier = Modifier.fillMaxWidth().testTag("case_title_input"),
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = courtName,
                        onValueChange = { courtName = it },
                        label = { Text("Court") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = courtRoom,
                        onValueChange = { courtRoom = it },
                        label = { Text("Bench / Room") },
                        singleLine = true,
                        modifier = Modifier.weight(0.75f),
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = judgeName,
                        onValueChange = { judgeName = it },
                        label = { Text("Judge") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = stage,
                        onValueChange = { stage = it },
                        label = { Text("Stage") },
                        placeholder = { Text("Arguments") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = clientName,
                        onValueChange = { clientName = it },
                        label = { Text("Client") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = opposingParty,
                        onValueChange = { opposingParty = it },
                        label = { Text("Opposing Party") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = clientEmail,
                    onValueChange = { clientEmail = it },
                    label = { Text("Client / Clerk Email (for Cause List)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(14.dp))
                Text("Status", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CaseStatus.entries.forEach { s ->
                        FilterChip(
                            selected = status == s.wireValue,
                            onClick = { status = s.wireValue },
                            label = { Text(s.displayLabel) },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text("Priority", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CasePriority.entries.forEach { p ->
                        FilterChip(
                            selected = priority == p.wireValue,
                            onClick = { priority = p.wireValue },
                            label = { Text(p.displayLabel) },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text("Court Dates & Automated Reminder", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { activePickerTarget = "hearing" },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.CalendarMonth, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (nextHearingMs > 0L) formatEpochDateTime(nextHearingMs) else "Set Next Hearing",
                            maxLines = 1,
                        )
                    }
                    if (nextHearingMs > 0L) {
                        OutlinedButton(onClick = { showTimePickerForHearing = true }) {
                            Icon(Icons.Filled.Schedule, contentDescription = "Pick hearing time")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { activePickerTarget = "deadline" },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (filingDeadlineMs > 0L) {
                            "Filing Deadline: ${formatEpochDateOnly(filingDeadlineMs)}"
                        } else {
                            "Set Filing Deadline"
                        }
                    )
                }

                if (nextHearingMs > 0L || filingDeadlineMs > 0L) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            val draftCase = (initialCase ?: LitigationCase()).copy(
                                caseNumber = caseNumber.trim(),
                                title = title.trim().ifBlank { "Court Hearing" },
                                courtName = courtName.trim(),
                                courtRoom = courtRoom.trim(),
                                stage = stage.trim(),
                                nextHearingEpochMs = nextHearingMs,
                                filingDeadlineEpochMs = filingDeadlineMs,
                                googleKeepUrl = keepUrl.trim(),
                                notes = notes.trim(),
                            )
                            IcsCalendarExporter.openCaseInExternalCalendar(context, draftCase)
                        },
                        modifier = Modifier.fillMaxWidth().testTag("push_case_to_calendar_button"),
                    ) {
                        Icon(Icons.Filled.CalendarMonth, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Push Date to External Calendar")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(30L to "30m before", 120L to "2h before", 1440L to "1d before", 2880L to "2d before").forEach { (mins, label) ->
                        FilterChip(
                            selected = reminderMinutes == mins,
                            onClick = { reminderMinutes = mins },
                            label = { Text(label) },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = keepUrl,
                    onValueChange = { keepUrl = it },
                    label = { Text("Google Keep Note URL (optional)") },
                    placeholder = { Text("https://keep.google.com/#NOTE/...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (keepUrl.isNotBlank()) {
                    TextButton(onClick = { openExternalUrl(context, keepUrl) }) {
                        Text("Open Keep Note")
                    }
                }

                OutlinedTextField(
                    value = keepLabel,
                    onValueChange = { keepLabel = it },
                    label = { Text("Keep Note Label (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = sheetUrl,
                    onValueChange = { sheetUrl = it },
                    label = { Text("Google Sheet URL (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (sheetUrl.isNotBlank()) {
                    TextButton(onClick = { openExternalUrl(context, sheetUrl) }) {
                        Text("Open Sheet")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Case Strategy & Notes") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        enabled = caseNumber.isNotBlank() && title.isNotBlank(),
                        onClick = {
                            val updated = (initialCase ?: LitigationCase()).copy(
                                caseNumber = caseNumber.trim(),
                                title = title.trim(),
                                courtName = courtName.trim(),
                                courtRoom = courtRoom.trim(),
                                judgeName = judgeName.trim(),
                                clientName = clientName.trim(),
                                clientEmail = clientEmail.trim(),
                                opposingParty = opposingParty.trim(),
                                stage = stage.trim(),
                                status = status,
                                priority = priority,
                                nextHearingEpochMs = nextHearingMs,
                                filingDeadlineEpochMs = filingDeadlineMs,
                                reminderMinutesBefore = reminderMinutes,
                                googleKeepUrl = keepUrl.trim(),
                                googleKeepLabel = keepLabel.trim(),
                                googleSheetUrl = sheetUrl.trim(),
                                notes = notes.trim(),
                            )
                            onSave(updated)
                            onDismiss()
                        },
                        modifier = Modifier.testTag("save_case_button"),
                    ) {
                        Text(if (isEditing) "Save Changes" else "Save Case")
                    }
                }
            }
        }
    }

    if (activePickerTarget != null) {
        val initialSel = when (activePickerTarget) {
            "hearing" -> localDateToUtcPickerMs(nextHearingMs)
            else -> localDateToUtcPickerMs(filingDeadlineMs)
        }
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialSel)
        DatePickerDialog(
            onDismissRequest = { activePickerTarget = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        val utcMs = datePickerState.selectedDateMillis
                        if (utcMs != null) {
                            val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                                timeInMillis = utcMs
                            }
                            val localCal = Calendar.getInstance().apply {
                                val existingMs = if (activePickerTarget == "hearing") nextHearingMs else filingDeadlineMs
                                if (existingMs > 0L) {
                                    timeInMillis = existingMs
                                } else {
                                    set(Calendar.HOUR_OF_DAY, 10)
                                    set(Calendar.MINUTE, 0)
                                }
                                set(Calendar.YEAR, utcCal.get(Calendar.YEAR))
                                set(Calendar.MONTH, utcCal.get(Calendar.MONTH))
                                set(Calendar.DAY_OF_MONTH, utcCal.get(Calendar.DAY_OF_MONTH))
                                set(Calendar.SECOND, 0)
                            }
                            if (activePickerTarget == "hearing") {
                                nextHearingMs = localCal.timeInMillis
                            } else {
                                filingDeadlineMs = localCal.timeInMillis
                            }
                        }
                        activePickerTarget = null
                    }
                ) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { activePickerTarget = null }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePickerForHearing) {
        val cal = Calendar.getInstance().apply {
            timeInMillis = if (nextHearingMs > 0L) nextHearingMs else System.currentTimeMillis()
        }
        val timeState = rememberTimePickerState(
            initialHour = cal.get(Calendar.HOUR_OF_DAY),
            initialMinute = cal.get(Calendar.MINUTE),
        )
        AlertDialog(
            onDismissRequest = { showTimePickerForHearing = false },
            title = { Text("Hearing Time") },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        cal.set(Calendar.HOUR_OF_DAY, timeState.hour)
                        cal.set(Calendar.MINUTE, timeState.minute)
                        cal.set(Calendar.SECOND, 0)
                        nextHearingMs = cal.timeInMillis
                        showTimePickerForHearing = false
                    }
                ) { Text("Set Time") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePickerForHearing = false }) { Text("Cancel") }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TaskEditorDialog(
    cases: List<LitigationCase>,
    initialTask: LitigationTask?,
    onDismiss: () -> Unit,
    onSave: (LitigationTask) -> Unit,
) {
    var title by remember { mutableStateOf(initialTask?.title.orEmpty()) }
    var description by remember { mutableStateOf(initialTask?.description.orEmpty()) }
    var category by remember { mutableStateOf(initialTask?.category ?: TaskCategory.HEARING_PREP.wireValue) }
    var priority by remember { mutableStateOf(initialTask?.priority ?: CasePriority.HIGH.wireValue) }
    var selectedCaseId by remember { mutableStateOf(initialTask?.caseId.orEmpty()) }
    var dueMs by remember { mutableLongStateOf(initialTask?.dueEpochMs ?: (System.currentTimeMillis() + 3_600_000L)) }
    var keepUrl by remember { mutableStateOf(initialTask?.keepNoteUrl.orEmpty()) }
    var reminderEnabled by remember { mutableStateOf(initialTask?.reminderEnabled ?: true) }
    var showDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialTask == null) "New Priority Task" else "Edit Task") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Task Title *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("task_title_input"),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Details") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text("Category", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TaskCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = category == cat.wireValue,
                            onClick = { category = cat.wireValue },
                            label = { Text(cat.displayLabel) },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Priority", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CasePriority.entries.forEach { p ->
                        FilterChip(
                            selected = priority == p.wireValue,
                            onClick = { priority = p.wireValue },
                            label = { Text(p.displayLabel) },
                        )
                    }
                }
                if (cases.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Link Case", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        cases.take(6).forEach { c ->
                            FilterChip(
                                selected = selectedCaseId == c.id,
                                onClick = {
                                    selectedCaseId = if (selectedCaseId == c.id) "" else c.id
                                },
                                label = { Text(c.caseNumber) },
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Due: ${formatEpochDateOnly(dueMs)}")
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = keepUrl,
                    onValueChange = { keepUrl = it },
                    label = { Text("Google Keep Note URL (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Push Reminder Alert", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = reminderEnabled, onCheckedChange = { reminderEnabled = it })
                }
            }
        },
        confirmButton = {
            Button(
                enabled = title.isNotBlank(),
                onClick = {
                    val linkedCase = cases.firstOrNull { it.id == selectedCaseId }
                    onSave(
                        (initialTask ?: LitigationTask()).copy(
                            title = title.trim(),
                            description = description.trim(),
                            category = category,
                            priority = priority,
                            caseId = selectedCaseId,
                            caseNumber = linkedCase?.caseNumber.orEmpty(),
                            dueEpochMs = dueMs,
                            reminderEnabled = reminderEnabled,
                            keepNoteUrl = keepUrl.trim(),
                        )
                    )
                    onDismiss()
                },
                modifier = Modifier.testTag("save_task_button"),
            ) { Text("Save Task") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = localDateToUtcPickerMs(dueMs))
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { utcMs ->
                            val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                                timeInMillis = utcMs
                            }
                            val localCal = Calendar.getInstance().apply {
                                set(Calendar.YEAR, utcCal.get(Calendar.YEAR))
                                set(Calendar.MONTH, utcCal.get(Calendar.MONTH))
                                set(Calendar.DAY_OF_MONTH, utcCal.get(Calendar.DAY_OF_MONTH))
                                set(Calendar.HOUR_OF_DAY, 10)
                                set(Calendar.MINUTE, 0)
                            }
                            dueMs = localCal.timeInMillis
                        }
                        showDatePicker = false
                    }
                ) { Text("Set Date") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = state)
        }
    }
}

@Composable
fun CalendarLinkOnlyDialog(
    onDismiss: () -> Unit,
    onAddCalendarLink: (String, String) -> Unit,
) {
    var label by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Link Calendar via URL") },
        text = {
            Column {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Calendar Name *") },
                    placeholder = { Text("e.g., High Court Docket") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("cal_link_label_input"),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Calendar Link (.ics / webcal://) *") },
                    placeholder = { Text("https://calendar.google.com/calendar/ical/.../basic.ics") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth().testTag("cal_link_url_input"),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "No login or permissions required. Paste any .ics, webcal://, iCloud, Outlook, or Google Calendar iCal/share link.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                enabled = url.isNotBlank(),
                onClick = {
                    onAddCalendarLink(label.ifBlank { "Linked Calendar" }, url)
                    onDismiss()
                },
                modifier = Modifier.testTag("save_cal_link_button"),
            ) { Text("Link & Sync") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlatformLinkDialog(
    initialType: IntegrationType,
    onDismiss: () -> Unit,
    onAdd: (IntegrationType, String, String) -> Unit,
) {
    var selectedType by remember { mutableStateOf(initialType) }
    var label by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (selectedType == IntegrationType.ICS_CALENDAR) "Link Calendar (.ics / webcal)"
                else "Link External Source"
            )
        },
        text = {
            Column {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IntegrationType.entries.forEach { t ->
                        FilterChip(
                            selected = selectedType == t,
                            onClick = { selectedType = t },
                            label = { Text(t.displayLabel) },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label *") },
                    placeholder = {
                        Text(
                            when (selectedType) {
                                IntegrationType.ICS_CALENDAR -> "e.g., Court Calendar Feed"
                                IntegrationType.GOOGLE_SHEET -> "e.g., Chambers Case Sheet"
                                IntegrationType.GOOGLE_KEEP -> "e.g., Case Briefs Notebook"
                            }
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("link_label_input"),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Link URL *") },
                    placeholder = {
                        Text(
                            when (selectedType) {
                                IntegrationType.ICS_CALENDAR -> "https://calendar.google.com/calendar/ical/.../basic.ics"
                                IntegrationType.GOOGLE_SHEET -> "https://docs.google.com/spreadsheets/d/..."
                                IntegrationType.GOOGLE_KEEP -> "https://keep.google.com/#NOTE/..."
                            }
                        )
                    },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth().testTag("link_url_input"),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = when (selectedType) {
                        IntegrationType.ICS_CALENDAR ->
                            "Calendar syncs by link only (.ics / webcal:// / Google iCal link). No sign-in needed."
                        IntegrationType.GOOGLE_SHEET ->
                            "Paste a Google Sheet shared as 'Anyone with the link can view' or CSV publish link."
                        IntegrationType.GOOGLE_KEEP ->
                            "Paste a Google Keep note or label URL for 1-tap reference."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                enabled = label.isNotBlank() && url.isNotBlank(),
                onClick = {
                    onAdd(selectedType, label, url)
                    onDismiss()
                },
                modifier = Modifier.testTag("save_link_button"),
            ) { Text("Link & Sync") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
fun AttachKeepToEventDialog(
    event: SyncedCalendarEvent,
    onDismiss: () -> Unit,
    onAttach: (String) -> Unit,
) {
    var keepUrl by remember { mutableStateOf(event.keepNoteUrl) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Link Google Keep Note") },
        text = {
            Column {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = keepUrl,
                    onValueChange = { keepUrl = it },
                    label = { Text("Google Keep Note URL") },
                    placeholder = { Text("https://keep.google.com/#NOTE/...") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                enabled = keepUrl.isNotBlank(),
                onClick = {
                    onAttach(keepUrl)
                    onDismiss()
                },
            ) { Text("Attach Link") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
