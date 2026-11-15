package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material3.Button
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.data.sync.CauseListEmailComposer
import com.example.ui.ChambersDashboardState
import java.util.Calendar
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CauseListEmailDialog(
    state: ChambersDashboardState,
    initialStartDateMs: Long = CauseListEmailComposer.startOfDayMs(System.currentTimeMillis()),
    initialEndDateMs: Long = CauseListEmailComposer.endOfDayMs(System.currentTimeMillis()),
    initialPresetIndex: Int = 0, // 0=Single Day, 1=This Week, 2=This Month, 3=Custom
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    var selectedPreset by remember { mutableIntStateOf(initialPresetIndex) }
    var startDateMs by remember { mutableLongStateOf(CauseListEmailComposer.startOfDayMs(initialStartDateMs)) }
    var endDateMs by remember { mutableLongStateOf(CauseListEmailComposer.endOfDayMs(initialEndDateMs)) }
    var showDateRangePicker by remember { mutableStateOf(false) }

    val draft = remember(startDateMs, endDateMs, state.cases, state.calendarEvents, state.tasks) {
        CauseListEmailComposer.buildCauseListDraft(
            startDateMs = startDateMs,
            endDateMs = endDateMs,
            allCases = state.cases,
            allCalendarEvents = state.calendarEvents,
            allTasks = state.tasks,
            counselName = "Dilawar & Saqlain Chambers",
        )
    }

    var recipient by remember { mutableStateOf(draft.suggestedRecipients.joinToString(", ")) }
    var subject by remember { mutableStateOf(draft.subject) }
    var body by remember { mutableStateOf(draft.body) }

    LaunchedEffect(draft) {
        subject = draft.subject
        body = draft.body
    }

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
                        text = "Cause List Email",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close cause list dialog")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text("Date Range", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(6.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    FilterChip(
                        selected = selectedPreset == 0,
                        onClick = {
                            selectedPreset = 0
                            val now = System.currentTimeMillis()
                            startDateMs = CauseListEmailComposer.startOfDayMs(now)
                            endDateMs = CauseListEmailComposer.endOfDayMs(now)
                        },
                        label = { Text("Today") },
                    )
                    FilterChip(
                        selected = selectedPreset == 1,
                        onClick = {
                            selectedPreset = 1
                            val now = System.currentTimeMillis()
                            startDateMs = CauseListEmailComposer.startOfDayMs(now)
                            endDateMs = CauseListEmailComposer.endOfDayMs(now + 6L * 86_400_000L)
                        },
                        label = { Text("This Week") },
                    )
                    FilterChip(
                        selected = selectedPreset == 2,
                        onClick = {
                            selectedPreset = 2
                            val now = System.currentTimeMillis()
                            startDateMs = CauseListEmailComposer.startOfDayMs(now)
                            endDateMs = CauseListEmailComposer.endOfDayMs(now + 29L * 86_400_000L)
                        },
                        label = { Text("30 Days") },
                    )
                    FilterChip(
                        selected = selectedPreset == 3,
                        onClick = {
                            selectedPreset = 3
                            showDateRangePicker = true
                        },
                        leadingIcon = {
                            Icon(Icons.Filled.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text("Custom") },
                        modifier = Modifier.testTag("cause_list_custom_range_chip"),
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = draft.rangeLabel,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = "${draft.hearingCount} hearings • ${draft.deadlineCount} deadlines • ${draft.eventCount} calendar events",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { showDateRangePicker = true }) {
                            Text("Edit")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = recipient,
                    onValueChange = { recipient = it },
                    label = { Text("Recipient Email(s)") },
                    placeholder = { Text("clerk@chambers.com, client@example.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("cause_list_recipient_input"),
                )

                if (draft.suggestedRecipients.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        draft.suggestedRecipients.take(4).forEach { email ->
                            FilterChip(
                                selected = recipient.contains(email, ignoreCase = true),
                                onClick = {
                                    if (!recipient.contains(email, ignoreCase = true)) {
                                        recipient = if (recipient.isBlank()) email else "$recipient, $email"
                                    }
                                },
                                label = { Text("+ $email") },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("Subject") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("cause_list_subject_input"),
                )

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("Cause List Draft") },
                    minLines = 6,
                    maxLines = 11,
                    modifier = Modifier.fillMaxWidth().testTag("cause_list_body_input"),
                )

                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText(subject, body))
                            Toast.makeText(context, "Cause list copied", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f).minimumInteractiveComponentSize(),
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy")
                    }
                    Button(
                        onClick = {
                            val intent = CauseListEmailComposer.buildSendCauseListEmailIntent(
                                recipientEmails = recipient,
                                subject = subject,
                                body = body,
                            )
                            runCatching { context.startActivity(intent) }.onFailure {
                                Toast.makeText(context, "No email app found on device", Toast.LENGTH_SHORT).show()
                            }
                            onDismiss()
                        },
                        modifier = Modifier
                            .weight(1.2f)
                            .minimumInteractiveComponentSize()
                            .testTag("confirm_email_cause_list"),
                    ) {
                        Icon(Icons.Filled.MailOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Send Email")
                    }
                }
            }
        }
    }

    if (showDateRangePicker) {
        val rangeState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = startDateMs,
            initialSelectedEndDateMillis = endDateMs,
        )
        DatePickerDialog(
            onDismissRequest = { showDateRangePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val sUtc = rangeState.selectedStartDateMillis
                        val eUtc = rangeState.selectedEndDateMillis ?: sUtc
                        if (sUtc != null && eUtc != null) {
                            startDateMs = CauseListEmailComposer.startOfDayMs(utcPickerMillisToLocal(sUtc))
                            endDateMs = CauseListEmailComposer.endOfDayMs(utcPickerMillisToLocal(eUtc))
                            selectedPreset = 3
                        }
                        showDateRangePicker = false
                    }
                ) { Text("Apply") }
            },
            dismissButton = {
                TextButton(onClick = { showDateRangePicker = false }) { Text("Cancel") }
            },
        ) {
            DateRangePicker(
                state = rangeState,
                modifier = Modifier.height(460.dp),
            )
        }
    }
}

private fun utcPickerMillisToLocal(utcMs: Long): Long {
    val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = utcMs
    }
    return Calendar.getInstance().apply {
        set(Calendar.YEAR, utcCal.get(Calendar.YEAR))
        set(Calendar.MONTH, utcCal.get(Calendar.MONTH))
        set(Calendar.DAY_OF_MONTH, utcCal.get(Calendar.DAY_OF_MONTH))
        set(Calendar.HOUR_OF_DAY, 12)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
