package com.example.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.LocalDatabaseFileManager
import com.example.data.model.CaseStatus
import com.example.data.model.IntegrationType
import com.example.data.model.LitigationCase
import com.example.data.model.LitigationTask
import com.example.data.model.PlatformLink
import com.example.data.model.SyncedCalendarEvent
import com.example.data.repository.LocalLitigationRepository
import com.example.data.sync.GoogleSheetSyncEngine
import com.example.data.sync.IcsCalendarParser
import com.example.notifications.LitigationReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class ChambersDashboardState(
    val cases: List<LitigationCase> = emptyList(),
    val tasks: List<LitigationTask> = emptyList(),
    val integrations: List<PlatformLink> = emptyList(),
    val calendarEvents: List<SyncedCalendarEvent> = emptyList(),
)

class LitigationViewModel(
    private val repository: LocalLitigationRepository,
    private val fileManager: LocalDatabaseFileManager,
    private val appContext: Context,
) : ViewModel() {

    val dashboardState: StateFlow<UiState<ChambersDashboardState>> = combine(
        repository.observeCases(),
        repository.observeTasks(),
        repository.observeIntegrations(),
        repository.observeCalendarEvents(),
    ) { cases, tasks, integrations, events ->
        ChambersDashboardState(
            cases = cases.sortedWith(
                compareBy<LitigationCase> { if (it.nextHearingEpochMs > 0L) 0 else 1 }
                    .thenBy { it.nextHearingEpochMs }
                    .thenBy { it.caseNumber }
            ),
            tasks = tasks.sortedWith(
                compareBy<LitigationTask> { it.completed }
                    .thenBy { priorityWeight(it.priority) }
                    .thenBy { if (it.dueEpochMs > 0L) it.dueEpochMs else Long.MAX_VALUE }
            ),
            integrations = integrations.sortedBy { it.label },
            calendarEvents = events.sortedBy { it.startEpochMs },
        )
    }
        .map<ChambersDashboardState, UiState<ChambersDashboardState>> { UiState.Success(it) }
        .catch { error ->
            Log.w(TAG, "Error observing local litigation database", error)
            emit(UiState.Error(error.message ?: "Unable to read local chambers database"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading,
        )

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _selectedDateMs = MutableStateFlow(startOfDayMs(System.currentTimeMillis()))
    val selectedDateMs: StateFlow<Long> = _selectedDateMs.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { repository.ensureCasesForUnlinkedCalendarEvents() }
        }

        dashboardState
            .onEach { state ->
                if (state is UiState.Success) {
                    runCatching {
                        LitigationReminderScheduler.scheduleAllReminders(
                            context = appContext,
                            cases = state.data.cases,
                            tasks = state.data.tasks,
                        )
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun selectDate(epochMs: Long) {
        _selectedDateMs.value = startOfDayMs(epochMs)
    }

    fun saveCase(caseItem: LitigationCase) {
        viewModelScope.launch {
            try {
                repository.saveCaseAndSyncCalendar(caseItem)
                if (caseItem.nextHearingEpochMs > 0L) {
                    _selectedDateMs.value = startOfDayMs(caseItem.nextHearingEpochMs)
                }
                _statusMessage.value = "Case saved & synced to calendar"
            } catch (e: Exception) {
                _statusMessage.value = "Failed to save case: ${e.localizedMessage}"
            }
        }
    }

    fun adjournCaseByDays(caseItem: LitigationCase, days: Int) {
        val base = if (caseItem.nextHearingEpochMs > 0L) caseItem.nextHearingEpochMs else System.currentTimeMillis()
        val nextCal = Calendar.getInstance().apply {
            timeInMillis = base
            add(Calendar.DAY_OF_YEAR, days)
        }
        saveCase(
            caseItem = caseItem.copy(
                nextHearingEpochMs = nextCal.timeInMillis,
                status = CaseStatus.ADJOURNED.wireValue,
            )
        )
    }

    fun deleteCase(caseId: String) {
        viewModelScope.launch {
            try {
                repository.deleteCase(caseId)
                _statusMessage.value = "Case removed"
            } catch (e: Exception) {
                _statusMessage.value = "Delete failed: ${e.localizedMessage}"
            }
        }
    }

    fun saveTask(task: LitigationTask) {
        viewModelScope.launch {
            try {
                repository.saveTask(task)
                _statusMessage.value = "Task saved"
            } catch (e: Exception) {
                _statusMessage.value = "Failed to save task: ${e.localizedMessage}"
            }
        }
    }

    fun toggleTaskCompleted(task: LitigationTask) {
        viewModelScope.launch {
            try {
                repository.toggleTaskCompleted(task.id, !task.completed)
            } catch (e: Exception) {
                _statusMessage.value = "Update failed: ${e.localizedMessage}"
            }
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            try {
                repository.deleteTask(taskId)
            } catch (e: Exception) {
                _statusMessage.value = "Delete failed: ${e.localizedMessage}"
            }
        }
    }

    fun addPlatformLink(type: IntegrationType, label: String, url: String, autoSync: Boolean = true) {
        viewModelScope.launch {
            try {
                val normalizedUrl = if (type == IntegrationType.ICS_CALENDAR) {
                    IcsCalendarParser.normalizeCalendarUrl(url)
                } else {
                    url.trim()
                }
                val link = PlatformLink(
                    type = type.wireValue,
                    label = label.trim().ifBlank {
                        when (type) {
                            IntegrationType.ICS_CALENDAR -> "Linked Calendar"
                            IntegrationType.GOOGLE_SHEET -> "Linked Sheet"
                            IntegrationType.GOOGLE_KEEP -> "Keep Notebook"
                        }
                    },
                    url = normalizedUrl,
                    autoSync = autoSync,
                )
                val id = repository.savePlatformLink(link)
                val savedLink = link.copy(id = id)
                when (type) {
                    IntegrationType.ICS_CALENDAR -> syncSingleIcsLink(savedLink)
                    IntegrationType.GOOGLE_SHEET -> syncSingleGoogleSheetLink(savedLink)
                    IntegrationType.GOOGLE_KEEP -> {
                        _statusMessage.value = "Google Keep note link saved"
                    }
                }
            } catch (e: Exception) {
                _statusMessage.value = "Failed to add link: ${e.localizedMessage}"
            }
        }
    }

    fun deletePlatformLink(linkId: String) {
        viewModelScope.launch {
            try {
                repository.deletePlatformLink(linkId)
                _statusMessage.value = "Link removed"
            } catch (e: Exception) {
                _statusMessage.value = "Failed to remove link: ${e.localizedMessage}"
            }
        }
    }

    fun linkKeepNoteToEvent(event: SyncedCalendarEvent, keepUrl: String) {
        viewModelScope.launch {
            try {
                repository.upsertCalendarEvent(event.copy(keepNoteUrl = keepUrl.trim()))
                if (event.linkedCaseId.isNotBlank()) {
                    val linkedCase = repository.getCasesOnce().firstOrNull { it.id == event.linkedCaseId }
                    if (linkedCase != null) {
                        repository.saveCase(linkedCase.copy(googleKeepUrl = keepUrl.trim()))
                    }
                }
                _statusMessage.value = "Keep note linked"
            } catch (e: Exception) {
                _statusMessage.value = "Failed to link Keep note: ${e.localizedMessage}"
            }
        }
    }

    fun syncAllConnectedPlatforms() {
        val currentState = dashboardState.value as? UiState.Success ?: return
        val links = currentState.data.integrations
        if (links.none { it.type == IntegrationType.ICS_CALENDAR.wireValue || it.type == IntegrationType.GOOGLE_SHEET.wireValue }) {
            _statusMessage.value = "Add a calendar link (.ics / webcal) to sync events"
            return
        }
        viewModelScope.launch {
            _isSyncing.value = true
            var totalEvents = 0
            var totalCases = 0
            val errors = mutableListOf<String>()

            links.filter { it.type == IntegrationType.ICS_CALENDAR.wireValue }
                .forEach { link ->
                    IcsCalendarParser.fetchAndParse(link.url, link.id, link.label)
                        .onSuccess { events ->
                            val capped = events.take(250)
                            val syncStamp = System.currentTimeMillis()
                            val imported = repository.syncFeedEventsAsEditableCases(
                                sourceId = link.id,
                                incomingEvents = capped,
                                lastFeedSyncEpochMs = link.lastSyncedEpochMs,
                                syncStampEpochMs = syncStamp,
                            )
                            totalEvents += imported
                            repository.savePlatformLink(
                                link.copy(
                                    lastSyncedEpochMs = syncStamp,
                                    syncedItemCount = imported.toLong(),
                                    lastError = "",
                                )
                            )
                        }
                        .onFailure { err ->
                            errors.add("${link.label}: ${err.message}")
                            repository.savePlatformLink(
                                link.copy(lastError = err.message.orEmpty().take(280))
                            )
                        }
                }

            links.filter { it.type == IntegrationType.GOOGLE_SHEET.wireValue }
                .forEach { link ->
                    GoogleSheetSyncEngine.fetchSheetCases(link.url)
                        .onSuccess { sheetCases ->
                            val merged = mergeImportedCases(sheetCases, repository.getCasesOnce())
                            totalCases += merged
                            repository.savePlatformLink(
                                link.copy(
                                    lastSyncedEpochMs = System.currentTimeMillis(),
                                    syncedItemCount = sheetCases.size.toLong(),
                                    lastError = "",
                                )
                            )
                        }
                        .onFailure { err ->
                            errors.add("${link.label}: ${err.message}")
                            repository.savePlatformLink(
                                link.copy(lastError = err.message.orEmpty().take(280))
                            )
                        }
                }

            _isSyncing.value = false
            _statusMessage.value = if (errors.isEmpty()) {
                if (totalCases > 0) {
                    "Synced $totalEvents calendar cases & $totalCases sheet updates"
                } else {
                    "Synced $totalEvents cases from calendar link"
                }
            } else {
                errors.first()
            }
        }
    }

    fun syncSingleIcsLink(link: PlatformLink) {
        viewModelScope.launch {
            _isSyncing.value = true
            IcsCalendarParser.fetchAndParse(link.url, link.id, link.label)
                .onSuccess { events ->
                    val capped = events.take(250)
                    val syncStamp = System.currentTimeMillis()
                    val imported = repository.syncFeedEventsAsEditableCases(
                        sourceId = link.id,
                        incomingEvents = capped,
                        lastFeedSyncEpochMs = link.lastSyncedEpochMs,
                        syncStampEpochMs = syncStamp,
                    )
                    repository.savePlatformLink(
                        link.copy(
                            lastSyncedEpochMs = syncStamp,
                            syncedItemCount = imported.toLong(),
                            lastError = "",
                        )
                    )
                    _statusMessage.value = "Synced $imported cases from ${link.label}"
                }
                .onFailure { err ->
                    repository.savePlatformLink(link.copy(lastError = err.message.orEmpty().take(280)))
                    _statusMessage.value = err.message ?: "Calendar link sync failed"
                }
            _isSyncing.value = false
        }
    }

    fun syncSingleGoogleSheetLink(link: PlatformLink) {
        viewModelScope.launch {
            _isSyncing.value = true
            GoogleSheetSyncEngine.fetchSheetCases(link.url)
                .onSuccess { sheetCases ->
                    val count = mergeImportedCases(sheetCases, repository.getCasesOnce())
                    repository.savePlatformLink(
                        link.copy(
                            lastSyncedEpochMs = System.currentTimeMillis(),
                            syncedItemCount = sheetCases.size.toLong(),
                            lastError = "",
                        )
                    )
                    _statusMessage.value = "Imported/updated $count cases from ${link.label}"
                }
                .onFailure { err ->
                    repository.savePlatformLink(link.copy(lastError = err.message.orEmpty().take(280)))
                    _statusMessage.value = "Sheet sync error: ${err.message}"
                }
            _isSyncing.value = false
        }
    }

    private suspend fun mergeImportedCases(
        incoming: List<LitigationCase>,
        existingCases: List<LitigationCase>,
    ): Int {
        var count = 0
        for (row in incoming) {
            val existing = existingCases.firstOrNull {
                it.caseNumber.equals(row.caseNumber, ignoreCase = true)
            }
            val merged = if (existing != null) {
                existing.copy(
                    title = row.title.ifBlank { existing.title },
                    courtName = row.courtName.ifBlank { existing.courtName },
                    courtRoom = row.courtRoom.ifBlank { existing.courtRoom },
                    judgeName = row.judgeName.ifBlank { existing.judgeName },
                    clientName = row.clientName.ifBlank { existing.clientName },
                    clientEmail = row.clientEmail.ifBlank { existing.clientEmail },
                    opposingParty = row.opposingParty.ifBlank { existing.opposingParty },
                    stage = row.stage.ifBlank { existing.stage },
                    status = row.status,
                    priority = row.priority,
                    nextHearingEpochMs = if (row.nextHearingEpochMs > 0L) row.nextHearingEpochMs else existing.nextHearingEpochMs,
                    filingDeadlineEpochMs = if (row.filingDeadlineEpochMs > 0L) row.filingDeadlineEpochMs else existing.filingDeadlineEpochMs,
                    googleKeepUrl = row.googleKeepUrl.ifBlank { existing.googleKeepUrl },
                    googleSheetUrl = row.googleSheetUrl.ifBlank { existing.googleSheetUrl },
                    notes = row.notes.ifBlank { existing.notes },
                )
            } else {
                row
            }
            repository.saveCaseAndSyncCalendar(merged)
            count++
        }
        return count
    }

    fun exportDatabaseJson(uri: Uri) {
        val state = dashboardState.value as? UiState.Success ?: return
        viewModelScope.launch {
            fileManager.exportToJsonUri(uri, state.data.cases, state.data.tasks)
                .onSuccess { count ->
                    _statusMessage.value = "Saved $count database records"
                }
                .onFailure { err ->
                    _statusMessage.value = "Export failed: ${err.message}"
                }
        }
    }

    fun exportDatabaseCsv(uri: Uri) {
        val state = dashboardState.value as? UiState.Success ?: return
        viewModelScope.launch {
            runCatching {
                val csv = GoogleSheetSyncEngine.exportCasesToCsv(state.data.cases)
                appContext.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(csv.toByteArray(Charsets.UTF_8))
                } ?: error("Cannot write CSV file")
            }.onSuccess {
                _statusMessage.value = "Exported ${state.data.cases.size} cases to CSV"
            }.onFailure { err ->
                _statusMessage.value = "CSV export failed: ${err.message}"
            }
        }
    }

    fun importDatabaseFile(uri: Uri) {
        viewModelScope.launch {
            fileManager.importFromUri(uri)
                .onSuccess { bundle ->
                    val existingCases = repository.getCasesOnce()
                    val mergedCount = mergeImportedCases(bundle.cases, existingCases)
                    bundle.tasks.forEach { repository.saveTask(it) }
                    _statusMessage.value = "Imported $mergedCount cases & ${bundle.tasks.size} tasks"
                }
                .onFailure { err ->
                    _statusMessage.value = "Import failed: ${err.message}"
                }
        }
    }

    fun triggerInstantTestNotification() {
        val state = dashboardState.value as? UiState.Success
        val nextCase = state?.data?.cases?.firstOrNull { it.nextHearingEpochMs >= startOfDayMs(System.currentTimeMillis()) }
        val title = if (nextCase != null) {
            "Upcoming Hearing: ${nextCase.caseNumber}"
        } else {
            "DILAWAR & SAQLAIN CMS — Docket Alert"
        }
        val body = if (nextCase != null) {
            val fmt = SimpleDateFormat("EEE, dd MMM hh:mm a", Locale.getDefault())
            "${nextCase.title} • ${fmt.format(Date(nextCase.nextHearingEpochMs))} (${nextCase.courtName.ifBlank { "Court" }})"
        } else {
            "Automated hearing & filing deadline notifications are active."
        }
        val shown = LitigationReminderScheduler.showInstantNotification(appContext, 9001, title, body)
        _statusMessage.value = if (shown) {
            "Notification sent"
        } else {
            "Enable notification permission to receive alerts"
        }
    }

    fun rescheduleAllNotifications() {
        val state = dashboardState.value as? UiState.Success ?: return
        val count = LitigationReminderScheduler.scheduleAllReminders(
            context = appContext,
            cases = state.data.cases,
            tasks = state.data.tasks,
        )
        _statusMessage.value = "Scheduled $count court alerts"
    }

    private fun priorityWeight(priority: String): Int = when (priority.lowercase(Locale.US)) {
        "critical" -> 0
        "high" -> 1
        else -> 2
    }

    companion object {
        private const val TAG = "LitigationVM"

        fun startOfDayMs(epochMs: Long): Long {
            val cal = Calendar.getInstance().apply {
                timeInMillis = epochMs
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            return cal.timeInMillis
        }

        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val appCtx = context.applicationContext
                return LitigationViewModel(
                    repository = LocalLitigationRepository(appCtx),
                    fileManager = LocalDatabaseFileManager(appCtx),
                    appContext = appCtx,
                ) as T
            }
        }
    }
}
