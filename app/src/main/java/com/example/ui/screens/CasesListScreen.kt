package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.CaseStatus
import com.example.data.model.LitigationCase
import com.example.ui.ChambersDashboardState
import com.example.ui.components.EmptyChambersCard
import com.example.ui.components.PriorityBadge
import com.example.ui.components.StatusBadge
import com.example.ui.components.formatEpochDateOnly
import com.example.ui.components.formatEpochDateTime

@Composable
fun CasesListScreen(
    state: ChambersDashboardState,
    onAddCase: () -> Unit,
    onEditCase: (LitigationCase) -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("all") }

    val filteredCases = remember(state.cases, searchQuery, statusFilter) {
        state.cases.filter { c ->
            val matchesFilter = when (statusFilter) {
                "all" -> true
                "critical" -> c.priority == "critical"
                else -> c.status == statusFilter
            }
            val q = searchQuery.trim()
            val matchesQuery = q.isBlank() ||
                c.caseNumber.contains(q, ignoreCase = true) ||
                c.title.contains(q, ignoreCase = true) ||
                c.clientName.contains(q, ignoreCase = true) ||
                c.courtName.contains(q, ignoreCase = true) ||
                c.judgeName.contains(q, ignoreCase = true)
            matchesFilter && matchesQuery
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = "Cases (${filteredCases.size})",
                style = MaterialTheme.typography.headlineMedium,
            )
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search case no., title, court, client...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("cases_search_input"),
            )
        }

        item {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = statusFilter == "all",
                    onClick = { statusFilter = "all" },
                    label = { Text("All (${state.cases.size})") },
                )
                FilterChip(
                    selected = statusFilter == "critical",
                    onClick = { statusFilter = "critical" },
                    label = { Text("Critical") },
                )
                CaseStatus.entries.forEach { st ->
                    FilterChip(
                        selected = statusFilter == st.wireValue,
                        onClick = { statusFilter = st.wireValue },
                        label = { Text(st.displayLabel) },
                    )
                }
            }
        }

        if (filteredCases.isEmpty()) {
            item {
                EmptyChambersCard(
                    icon = Icons.Filled.FolderCopy,
                    title = if (state.cases.isEmpty()) "No Cases Yet" else "No Matching Cases",
                    subtitle = if (state.cases.isEmpty()) {
                        "Tap New Case to register a matter, or import a CSV/JSON file in Sync."
                    } else {
                        "Adjust your search query or status filter."
                    },
                    actionLabel = if (state.cases.isEmpty()) "Add Case" else null,
                    onAction = if (state.cases.isEmpty()) onAddCase else null,
                )
            }
        } else {
            itemsIndexed(filteredCases, key = { _, c -> c.id }) { index, c ->
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
                        .clickable { onEditCase(c) }
                        .testTag("case_card_${c.caseNumber}"),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = c.caseNumber,
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
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )

                        val meta = listOfNotNull(
                            c.courtName.takeIf { it.isNotBlank() },
                            c.stage.takeIf { it.isNotBlank() },
                        ).joinToString(" • ")
                        if (meta.isNotBlank()) {
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = meta,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "Hearing: ${formatEpochDateTime(c.nextHearingEpochMs)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            if (c.filingDeadlineEpochMs > 0L) {
                                Text(
                                    text = "Due: ${formatEpochDateOnly(c.filingDeadlineEpochMs)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(72.dp)) }
    }
}
