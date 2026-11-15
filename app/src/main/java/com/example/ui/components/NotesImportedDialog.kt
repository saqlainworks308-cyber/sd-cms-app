package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.IntegrationType
import com.example.data.model.LitigationCase
import com.example.ui.ChambersDashboardState

data class ImportedNoteItem(
    val id: String,
    val sourceLabel: String,
    val header: String,
    val body: String,
    val keepUrl: String,
    val keepLabel: String,
    val linkedCase: LitigationCase? = null,
)

fun collectImportedNotes(state: ChambersDashboardState): List<ImportedNoteItem> {
    val items = mutableListOf<ImportedNoteItem>()
    val linkedCaseIds = mutableSetOf<String>()

    // 1. Notes & Keep links attached to Cases (including calendar-imported & sheet-imported cases)
    state.cases.forEach { c ->
        if (c.notes.isNotBlank() || c.googleKeepUrl.isNotBlank()) {
            linkedCaseIds.add(c.id)
            val source = if (c.calendarSourceId.isNotBlank()) {
                "Calendar Case"
            } else if (c.googleSheetUrl.isNotBlank()) {
                "Sheet Case"
            } else {
                "Case Note"
            }
            items.add(
                ImportedNoteItem(
                    id = "case_${c.id}",
                    sourceLabel = source,
                    header = "${c.caseNumber} • ${c.title}",
                    body = c.notes.trim(),
                    keepUrl = c.googleKeepUrl.trim(),
                    keepLabel = c.googleKeepLabel.trim(),
                    linkedCase = c,
                )
            )
        }
    }

    // 2. Standalone Calendar Event descriptions/Keep links not already covered by a case
    state.calendarEvents.forEach { ev ->
        val alreadyCovered = ev.linkedCaseId.isNotBlank() && linkedCaseIds.contains(ev.linkedCaseId)
        if (!alreadyCovered && (ev.description.isNotBlank() || ev.keepNoteUrl.isNotBlank())) {
            val matchedCase = state.cases.firstOrNull { it.id == ev.linkedCaseId }
            items.add(
                ImportedNoteItem(
                    id = "evt_${ev.id}",
                    sourceLabel = ev.sourceLabel.ifBlank { "Calendar Note" },
                    header = ev.title,
                    body = ev.description.trim(),
                    keepUrl = ev.keepNoteUrl.trim(),
                    keepLabel = "Open Keep",
                    linkedCase = matchedCase,
                )
            )
        }
    }

    // 3. Linked Google Keep Notebooks from Sync
    state.integrations
        .filter { it.type == IntegrationType.GOOGLE_KEEP.wireValue }
        .forEach { link ->
            items.add(
                ImportedNoteItem(
                    id = "keep_${link.id}",
                    sourceLabel = "Google Keep",
                    header = link.label.ifBlank { "Google Keep Notebook" },
                    body = link.url,
                    keepUrl = link.url,
                    keepLabel = "Open Keep",
                )
            )
        }

    // 4. Priority Task notes/Keep links
    state.tasks.forEach { t ->
        if (t.description.isNotBlank() || t.keepNoteUrl.isNotBlank()) {
            items.add(
                ImportedNoteItem(
                    id = "task_${t.id}",
                    sourceLabel = "Task Note",
                    header = if (t.caseNumber.isNotBlank()) "${t.caseNumber} • ${t.title}" else t.title,
                    body = t.description.trim(),
                    keepUrl = t.keepNoteUrl.trim(),
                    keepLabel = "Open Keep",
                )
            )
        }
    }

    return items
}

@Composable
fun NotesImportedDialog(
    state: ChambersDashboardState,
    onEditCase: (LitigationCase) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    val allNotes = remember(state) { collectImportedNotes(state) }
    val filteredNotes = remember(allNotes, searchQuery) {
        val q = searchQuery.trim()
        if (q.isBlank()) allNotes
        else allNotes.filter {
            it.header.contains(q, ignoreCase = true) ||
                it.body.contains(q, ignoreCase = true) ||
                it.sourceLabel.contains(q, ignoreCase = true)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "Notes Imported (${filteredNotes.size})",
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            text = "All calendar descriptions, case briefs & Keep links",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close notes imported")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search imported notes...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("notes_imported_search"),
                )

                Spacer(modifier = Modifier.height(12.dp))
                if (filteredNotes.isEmpty()) {
                    EmptyChambersCard(
                        icon = Icons.Filled.StickyNote2,
                        title = "No Imported Notes Yet",
                        subtitle = "Notes imported from linked calendars, sheets, Keep links, or case briefs will appear together here.",
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 460.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(filteredNotes, key = { it.id }) { note ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = note.linkedCase != null) {
                                        note.linkedCase?.let {
                                            onDismiss()
                                            onEditCase(it)
                                        }
                                    },
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            shape = RoundedCornerShape(6.dp),
                                        ) {
                                            Text(
                                                text = note.sourceLabel,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            )
                                        }
                                        if (note.keepUrl.isNotBlank()) {
                                            PlatformActionPill(
                                                icon = Icons.Filled.StickyNote2,
                                                label = note.keepLabel.ifBlank { "Open Keep" },
                                                onClick = { openExternalUrl(context, note.keepUrl) },
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = note.header,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (note.body.isNotBlank() && note.body != note.keepUrl) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = note.body,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 5,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    if (note.linkedCase != null) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Tap to edit case",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
