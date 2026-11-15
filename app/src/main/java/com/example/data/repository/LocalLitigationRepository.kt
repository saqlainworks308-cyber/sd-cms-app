package com.example.data.repository

import android.content.Context
import com.example.data.local.LitigationLocalDatabase
import com.example.data.local.LocalCalendarEventDao
import com.example.data.local.LocalCaseDao
import com.example.data.local.LocalPlatformLinkDao
import com.example.data.local.LocalTaskDao
import com.example.data.local.toDomain
import com.example.data.local.toEntity
import com.example.data.model.LitigationCase
import com.example.data.model.LitigationTask
import com.example.data.model.PlatformLink
import com.example.data.model.SyncedCalendarEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class LocalLitigationRepository(
    private val caseDao: LocalCaseDao,
    private val taskDao: LocalTaskDao,
    private val linkDao: LocalPlatformLinkDao,
    private val eventDao: LocalCalendarEventDao,
) {
    constructor(context: Context) : this(
        db = LitigationLocalDatabase.getInstance(context)
    )

    private constructor(db: LitigationLocalDatabase) : this(
        caseDao = db.localCaseDao(),
        taskDao = db.localTaskDao(),
        linkDao = db.localPlatformLinkDao(),
        eventDao = db.localCalendarEventDao(),
    )

    fun observeCases(): Flow<List<LitigationCase>> =
        caseDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    fun observeTasks(): Flow<List<LitigationTask>> =
        taskDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    fun observeIntegrations(): Flow<List<PlatformLink>> =
        linkDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    fun observeCalendarEvents(): Flow<List<SyncedCalendarEvent>> =
        eventDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    suspend fun getCasesOnce(): List<LitigationCase> =
        caseDao.getAllOnce().map { it.toDomain() }

    suspend fun getCalendarEventsOnce(): List<SyncedCalendarEvent> =
        eventDao.getAllOnce().map { it.toDomain() }

    private fun sanitizeId(raw: String): String {
        val cleaned = raw.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(120)
        return cleaned.ifBlank { UUID.randomUUID().toString() }
    }

    suspend fun saveCase(item: LitigationCase): String =
        saveCaseInternal(item, nowMs = System.currentTimeMillis())

    private suspend fun saveCaseInternal(item: LitigationCase, nowMs: Long): String {
        val docId = if (item.id.isBlank()) UUID.randomUUID().toString() else sanitizeId(item.id)
        val clean = item.copy(
            id = docId,
            caseNumber = item.caseNumber.trim().take(80),
            title = item.title.trim().take(200),
            courtName = item.courtName.trim().take(160),
            courtRoom = item.courtRoom.trim().take(80),
            judgeName = item.judgeName.trim().take(120),
            clientName = item.clientName.trim().take(140),
            clientEmail = item.clientEmail.trim().take(160),
            opposingParty = item.opposingParty.trim().take(160),
            opposingCounsel = item.opposingCounsel.trim().take(140),
            stage = item.stage.trim().take(100),
            nextHearingEpochMs = item.nextHearingEpochMs.coerceAtLeast(0L),
            filingDeadlineEpochMs = item.filingDeadlineEpochMs.coerceAtLeast(0L),
            hearingDurationMinutes = item.hearingDurationMinutes.coerceIn(5L, 1440L),
            reminderMinutesBefore = item.reminderMinutesBefore.coerceIn(0L, 10080L),
            googleKeepUrl = item.googleKeepUrl.trim().take(500),
            googleKeepLabel = item.googleKeepLabel.trim().take(120),
            googleSheetUrl = item.googleSheetUrl.trim().take(500),
            calendarEventId = item.calendarEventId.trim().take(200),
            calendarSourceId = item.calendarSourceId.trim().take(128),
            notes = item.notes.trim().take(4000),
        )
        caseDao.upsert(clean.toEntity(nowMs = nowMs))
        return docId
    }

    /**
     * Saves a case entered/edited in the app and immediately updates all matching calendar_events
     * (or creates one if dated, or removes obsolete local events if cleared).
     */
    suspend fun saveCaseAndSyncCalendar(item: LitigationCase): String {
        val nowMs = System.currentTimeMillis()
        val savedId = saveCaseInternal(item, nowMs = nowMs)
        val savedCase = item.copy(id = savedId, updatedAtEpochMs = nowMs)
        syncCaseToCalendarEvents(savedCase)
        return savedId
    }

    suspend fun syncCaseToCalendarEvents(caseItem: LitigationCase) {
        val savedId = sanitizeId(caseItem.id)
        val existingEvents = getCalendarEventsOnce().filter {
            it.linkedCaseId == savedId ||
                (caseItem.calendarEventId.isNotBlank() && it.externalUid == caseItem.calendarEventId) ||
                it.externalUid == "case_hearing_$savedId"
        }

        if (caseItem.nextHearingEpochMs <= 0L) {
            existingEvents.forEach { ev -> eventDao.deleteById(ev.id) }
            return
        }

        val startMs = caseItem.nextHearingEpochMs
        val endMs = startMs + (caseItem.hearingDurationMinutes.coerceIn(15L, 480L) * 60_000L)
        val formattedTitle = if (caseItem.caseNumber.isNotBlank()) {
            "[${caseItem.caseNumber}] ${caseItem.title}"
        } else {
            caseItem.title
        }
        val formattedLocation = listOf(caseItem.courtName, caseItem.courtRoom)
            .filter { it.isNotBlank() }
            .joinToString(" • ")

        if (existingEvents.isEmpty()) {
            upsertCalendarEvent(
                SyncedCalendarEvent(
                    externalUid = caseItem.calendarEventId.ifBlank { "case_hearing_$savedId" },
                    sourceId = caseItem.calendarSourceId.ifBlank { "chambers_case" },
                    sourceLabel = "Chambers Hearing",
                    title = formattedTitle,
                    location = formattedLocation,
                    description = caseItem.notes,
                    startEpochMs = startMs,
                    endEpochMs = endMs,
                    allDay = false,
                    linkedCaseId = savedId,
                    keepNoteUrl = caseItem.googleKeepUrl,
                )
            )
        } else {
            existingEvents.forEach { matchedEvt ->
                upsertCalendarEvent(
                    matchedEvt.copy(
                        title = formattedTitle,
                        location = formattedLocation,
                        description = caseItem.notes,
                        startEpochMs = startMs,
                        endEpochMs = endMs,
                        allDay = false,
                        linkedCaseId = savedId,
                        keepNoteUrl = caseItem.googleKeepUrl,
                    )
                )
            }
        }
    }

    suspend fun deleteCase(caseId: String) {
        val cleanId = sanitizeId(caseId)
        caseDao.deleteById(cleanId)
        eventDao.deleteByLinkedCaseId(cleanId)
    }

    suspend fun saveTask(task: LitigationTask): String {
        val docId = if (task.id.isBlank()) UUID.randomUUID().toString() else sanitizeId(task.id)
        val clean = task.copy(
            id = docId,
            caseId = task.caseId.trim().take(128),
            caseNumber = task.caseNumber.trim().take(80),
            title = task.title.trim().take(200),
            description = task.description.trim().take(1000),
            dueEpochMs = task.dueEpochMs.coerceAtLeast(0L),
            keepNoteUrl = task.keepNoteUrl.trim().take(500),
        )
        taskDao.upsert(clean.toEntity())
        return docId
    }

    suspend fun toggleTaskCompleted(taskId: String, completed: Boolean) {
        taskDao.updateCompleted(sanitizeId(taskId), completed, System.currentTimeMillis())
    }

    suspend fun deleteTask(taskId: String) {
        taskDao.deleteById(sanitizeId(taskId))
    }

    suspend fun savePlatformLink(link: PlatformLink): String {
        val docId = if (link.id.isBlank()) UUID.randomUUID().toString() else sanitizeId(link.id)
        val clean = link.copy(
            id = docId,
            label = link.label.trim().take(120),
            url = link.url.trim().take(1000),
            lastError = link.lastError.trim().take(300),
        )
        linkDao.upsert(clean.toEntity())
        return docId
    }

    suspend fun deletePlatformLink(linkId: String) {
        val cleanId = sanitizeId(linkId)
        linkDao.deleteById(cleanId)
        eventDao.deleteBySourceId(cleanId)
    }

    suspend fun upsertCalendarEvent(event: SyncedCalendarEvent): String {
        val docId = if (event.id.isNotBlank()) {
            sanitizeId(event.id)
        } else {
            sanitizeId(event.externalUid)
        }
        val clean = event.copy(
            id = docId,
            externalUid = event.externalUid.trim().take(256).ifBlank { docId },
            sourceId = event.sourceId.trim().take(128),
            sourceLabel = event.sourceLabel.trim().take(120),
            title = event.title.trim().take(240).ifBlank { "Untitled Event" },
            location = event.location.trim().take(240),
            description = event.description.trim().take(2000),
            startEpochMs = event.startEpochMs.coerceAtLeast(0L),
            endEpochMs = event.endEpochMs.coerceAtLeast(event.startEpochMs.coerceAtLeast(0L)),
            linkedCaseId = event.linkedCaseId.trim().take(128),
            keepNoteUrl = event.keepNoteUrl.trim().take(500),
            deviceEventId = event.deviceEventId.trim().take(128),
        )
        eventDao.upsert(clean.toEntity())
        return docId
    }

    /**
     * Automatically imports/updates an editable LitigationCase for every synced calendar event,
     * while preserving dates and fields edited inside the app since the last feed sync.
     */
    suspend fun syncFeedEventsAsEditableCases(
        sourceId: String,
        incomingEvents: List<SyncedCalendarEvent>,
        lastFeedSyncEpochMs: Long = 0L,
        syncStampEpochMs: Long = System.currentTimeMillis(),
    ): Int {
        val cleanSourceId = sanitizeId(sourceId)
        val existingByUid = eventDao.getBySourceId(cleanSourceId).associateBy { it.externalUid }
        val existingCases = getCasesOnce().toMutableList()

        val entities = incomingEvents.mapIndexed { idx, ev ->
            val docId = sanitizeId("${cleanSourceId}_${ev.externalUid}")
            val prevEvent = existingByUid[ev.externalUid]
            val parsedTitle = parseCaseNumberAndTitle(ev.title)

            val matchedCase = existingCases.firstOrNull { c ->
                (c.calendarEventId.isNotBlank() && c.calendarEventId == ev.externalUid) ||
                    (prevEvent?.linkedCaseId?.isNotBlank() == true && c.id == prevEvent.linkedCaseId) ||
                    (parsedTitle.first.isNotBlank() && c.caseNumber.equals(parsedTitle.first, ignoreCase = true))
            }

            val isLocallyEdited = matchedCase != null &&
                lastFeedSyncEpochMs > 0L &&
                matchedCase.updatedAtEpochMs > lastFeedSyncEpochMs

            val durationMin = if (ev.endEpochMs > ev.startEpochMs) {
                ((ev.endEpochMs - ev.startEpochMs) / 60_000L).coerceIn(15L, 480L)
            } else {
                45L
            }

            val caseToSave = when {
                isLocallyEdited && matchedCase != null -> {
                    // Preserve dates, title, court, and notes edited in the app after the previous feed sync
                    matchedCase.copy(
                        calendarEventId = matchedCase.calendarEventId.ifBlank { ev.externalUid },
                        calendarSourceId = matchedCase.calendarSourceId.ifBlank { cleanSourceId },
                        googleKeepUrl = matchedCase.googleKeepUrl.ifBlank { ev.keepNoteUrl },
                        notes = matchedCase.notes.ifBlank { ev.description },
                    )
                }
                matchedCase != null -> {
                    matchedCase.copy(
                        title = parsedTitle.second.ifBlank { matchedCase.title },
                        courtName = ev.location.ifBlank { matchedCase.courtName },
                        nextHearingEpochMs = if (ev.startEpochMs > 0L) ev.startEpochMs else matchedCase.nextHearingEpochMs,
                        hearingDurationMinutes = durationMin,
                        googleKeepUrl = matchedCase.googleKeepUrl.ifBlank { ev.keepNoteUrl },
                        notes = matchedCase.notes.ifBlank { ev.description },
                        calendarEventId = ev.externalUid,
                        calendarSourceId = cleanSourceId,
                    )
                }
                else -> {
                    val generatedNo = parsedTitle.first.ifBlank {
                        "CAL-${existingCases.size + idx + 101}"
                    }
                    LitigationCase(
                        caseNumber = generatedNo,
                        title = parsedTitle.second.ifBlank { ev.title.ifBlank { "Calendar Hearing" } },
                        courtName = ev.location,
                        nextHearingEpochMs = ev.startEpochMs,
                        hearingDurationMinutes = durationMin,
                        googleKeepUrl = ev.keepNoteUrl,
                        calendarEventId = ev.externalUid,
                        calendarSourceId = cleanSourceId,
                        notes = ev.description,
                    )
                }
            }

            // Keep updatedAtEpochMs intact if locally edited, otherwise stamp with syncStampEpochMs
            val targetStamp = if (isLocallyEdited && matchedCase != null) {
                matchedCase.updatedAtEpochMs
            } else {
                syncStampEpochMs
            }
            val savedCaseId = saveCaseInternal(caseToSave, nowMs = targetStamp)
            val savedCase = caseToSave.copy(id = savedCaseId, updatedAtEpochMs = targetStamp)

            val existingIdx = existingCases.indexOfFirst { it.id == savedCaseId }
            if (existingIdx >= 0) {
                existingCases[existingIdx] = savedCase
            } else {
                existingCases.add(savedCase)
            }

            // Keep synced calendar_event aligned with locally edited case dates/details
            if (isLocallyEdited) {
                val effectiveStart = savedCase.nextHearingEpochMs
                val effectiveEnd = if (effectiveStart > 0L) {
                    effectiveStart + (savedCase.hearingDurationMinutes.coerceIn(15L, 480L) * 60_000L)
                } else {
                    0L
                }
                ev.copy(
                    id = docId,
                    sourceId = cleanSourceId,
                    title = if (savedCase.caseNumber.isNotBlank()) {
                        "[${savedCase.caseNumber}] ${savedCase.title}"
                    } else {
                        savedCase.title
                    },
                    location = listOf(savedCase.courtName, savedCase.courtRoom)
                        .filter { it.isNotBlank() }
                        .joinToString(" • "),
                    description = savedCase.notes,
                    startEpochMs = effectiveStart,
                    endEpochMs = effectiveEnd,
                    linkedCaseId = savedCaseId,
                    keepNoteUrl = savedCase.googleKeepUrl.ifBlank { prevEvent?.keepNoteUrl.orEmpty() },
                ).toEntity(nowMs = targetStamp)
            } else {
                ev.copy(
                    id = docId,
                    sourceId = cleanSourceId,
                    linkedCaseId = savedCaseId,
                    keepNoteUrl = ev.keepNoteUrl.ifBlank { prevEvent?.keepNoteUrl.orEmpty() },
                ).toEntity(nowMs = syncStampEpochMs)
            }
        }

        eventDao.replaceForSource(cleanSourceId, entities)
        return entities.size
    }

    /**
     * Reconciles any existing calendar events in the database that don't yet have a matching
     * editable LitigationCase (for example events synced prior to auto-import).
     */
    suspend fun ensureCasesForUnlinkedCalendarEvents() {
        val events = getCalendarEventsOnce()
        if (events.isEmpty()) return
        val existingCases = getCasesOnce().toMutableList()
        var counter = existingCases.size + 101

        for (ev in events) {
            val parsed = parseCaseNumberAndTitle(ev.title)
            val matched = existingCases.firstOrNull { c ->
                (ev.linkedCaseId.isNotBlank() && c.id == ev.linkedCaseId) ||
                    (c.calendarEventId.isNotBlank() && c.calendarEventId == ev.externalUid) ||
                    (parsed.first.isNotBlank() && c.caseNumber.equals(parsed.first, ignoreCase = true))
            }
            if (matched == null) {
                val newCase = LitigationCase(
                    caseNumber = parsed.first.ifBlank { "CAL-${counter++}" },
                    title = parsed.second.ifBlank { ev.title.ifBlank { "Calendar Hearing" } },
                    courtName = ev.location,
                    nextHearingEpochMs = ev.startEpochMs,
                    googleKeepUrl = ev.keepNoteUrl,
                    calendarEventId = ev.externalUid,
                    calendarSourceId = ev.sourceId,
                    notes = ev.description,
                )
                val savedId = saveCase(newCase)
                val savedCase = newCase.copy(id = savedId)
                existingCases.add(savedCase)
                eventDao.upsert(ev.copy(linkedCaseId = savedId).toEntity())
            } else if (ev.linkedCaseId != matched.id) {
                eventDao.upsert(ev.copy(linkedCaseId = matched.id).toEntity())
            }
        }
    }

    companion object {
        private val bracketCaseRegex = Regex("""^\s*\[([^\]]{2,40})]\s*(.+)$""")

        fun parseCaseNumberAndTitle(rawTitle: String): Pair<String, String> {
            val trimmed = rawTitle.trim()
            val match = bracketCaseRegex.find(trimmed)
            return if (match != null) {
                match.groupValues[1].trim() to match.groupValues[2].trim()
            } else {
                "" to trimmed
            }
        }
    }
}
