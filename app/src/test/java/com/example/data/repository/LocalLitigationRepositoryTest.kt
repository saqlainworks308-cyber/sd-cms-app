package com.example.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.LitigationLocalDatabase
import com.example.data.model.IntegrationType
import com.example.data.model.LitigationCase
import com.example.data.model.LitigationTask
import com.example.data.model.PlatformLink
import com.example.data.model.SyncedCalendarEvent
import com.example.ui.ChambersDashboardState
import com.example.ui.components.collectImportedNotes
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LocalLitigationRepositoryTest {

    private lateinit var db: LitigationLocalDatabase
    private lateinit var repository: LocalLitigationRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, LitigationLocalDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = LocalLitigationRepository(
            caseDao = db.localCaseDao(),
            taskDao = db.localTaskDao(),
            linkDao = db.localPlatformLinkDao(),
            eventDao = db.localCalendarEventDao(),
        )
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun syncFeedEventsAsEditableCases_autoCreatesAndLinksEditableCases() = runBlocking {
        val startMs = 1_795_000_000_000L
        val incoming = listOf(
            SyncedCalendarEvent(
                externalUid = "uid-101",
                sourceId = "feed_1",
                sourceLabel = "High Court ICS",
                title = "[WP-512/2026] Khan v. State",
                location = "High Court • Courtroom 4",
                description = "Arguments on maintainability",
                startEpochMs = startMs,
                endEpochMs = startMs + 3_600_000L,
                keepNoteUrl = "https://keep.google.com/#NOTE/abc1",
            ),
            SyncedCalendarEvent(
                externalUid = "uid-102",
                sourceId = "feed_1",
                sourceLabel = "High Court ICS",
                title = "Bail Hearing - Tariq",
                location = "Sessions Court",
                description = "",
                startEpochMs = startMs + 7_200_000L,
                endEpochMs = startMs + 10_800_000L,
            ),
        )

        val importedCount = repository.syncFeedEventsAsEditableCases("feed_1", incoming)
        assertEquals(2, importedCount)

        val cases = repository.getCasesOnce()
        val events = repository.getCalendarEventsOnce()
        assertEquals(2, cases.size)
        assertEquals(2, events.size)

        val wpCase = cases.first { it.caseNumber == "WP-512/2026" }
        assertEquals("Khan v. State", wpCase.title)
        assertEquals("High Court • Courtroom 4", wpCase.courtName)
        assertEquals("Arguments on maintainability", wpCase.notes)
        assertEquals("https://keep.google.com/#NOTE/abc1", wpCase.googleKeepUrl)
        assertEquals(60L, wpCase.hearingDurationMinutes)

        val linkedEv = events.first { it.externalUid == "uid-101" }
        assertEquals(wpCase.id, linkedEv.linkedCaseId)

        // Editing the imported case date preserves custom edits and updates the synced calendar event across re-syncs
        val newHearingMs = startMs + (5L * 86_400_000L)
        repository.saveCaseAndSyncCalendar(
            wpCase.copy(
                nextHearingEpochMs = newHearingMs,
                notes = "Updated chamber arguments note",
                courtRoom = "Bench B",
            )
        )
        val updatedEvents = repository.getCalendarEventsOnce()
        val updatedEv = updatedEvents.first { it.linkedCaseId == wpCase.id }
        assertEquals(newHearingMs, updatedEv.startEpochMs)

        repository.syncFeedEventsAsEditableCases(
            sourceId = "feed_1",
            incomingEvents = incoming,
            lastFeedSyncEpochMs = wpCase.updatedAtEpochMs,
            syncStampEpochMs = System.currentTimeMillis() + 50L,
        )
        val refreshedCase = repository.getCasesOnce().first { it.id == wpCase.id }
        val refreshedEv = repository.getCalendarEventsOnce().first { it.linkedCaseId == wpCase.id }
        assertEquals(newHearingMs, refreshedCase.nextHearingEpochMs)
        assertEquals(newHearingMs, refreshedEv.startEpochMs)
        assertEquals("Updated chamber arguments note", refreshedCase.notes)
        assertEquals("Bench B", refreshedCase.courtRoom)
    }

    @Test
    fun ensureCasesForUnlinkedCalendarEvents_backfillsMissingCases() = runBlocking {
        repository.upsertCalendarEvent(
            SyncedCalendarEvent(
                id = "legacy_evt_1",
                externalUid = "legacy-uid-1",
                sourceId = "feed_old",
                sourceLabel = "Legacy ICS",
                title = "[CR-77/2026] State v. Bilal",
                description = "Pre-existing calendar note",
                startEpochMs = 1_795_100_000_000L,
                endEpochMs = 1_795_103_600_000L,
            )
        )

        repository.ensureCasesForUnlinkedCalendarEvents()

        val cases = repository.getCasesOnce()
        val events = repository.getCalendarEventsOnce()
        assertEquals(1, cases.size)
        assertEquals("CR-77/2026", cases.first().caseNumber)
        assertEquals(cases.first().id, events.first().linkedCaseId)
    }

    @Test
    fun collectImportedNotes_aggregatesAllSourcesWithoutDuplicates() {
        val c1 = LitigationCase(
            id = "case_1",
            caseNumber = "WP-10/2026",
            title = "Calendar Imported Matter",
            calendarSourceId = "feed_1",
            notes = "Prepare stay application",
            googleKeepUrl = "https://keep.google.com/#NOTE/k1",
        )
        val evLinked = SyncedCalendarEvent(
            id = "ev_1",
            linkedCaseId = "case_1",
            title = "[WP-10/2026] Calendar Imported Matter",
            description = "Prepare stay application",
            keepNoteUrl = "https://keep.google.com/#NOTE/k1",
        )
        val evStandalone = SyncedCalendarEvent(
            id = "ev_2",
            linkedCaseId = "",
            sourceLabel = "Chamber Cal",
            title = "Registry Inspection",
            description = "Inspect original record",
        )
        val keepLink = PlatformLink(
            id = "link_keep",
            type = IntegrationType.GOOGLE_KEEP.wireValue,
            label = "Supreme Court Precedents",
            url = "https://keep.google.com/#home",
        )
        val taskWithNote = LitigationTask(
            id = "t1",
            caseNumber = "WP-10/2026",
            title = "File rejoinder",
            description = "Attach annexure B",
        )

        val notes = collectImportedNotes(
            ChambersDashboardState(
                cases = listOf(c1),
                tasks = listOf(taskWithNote),
                integrations = listOf(keepLink),
                calendarEvents = listOf(evLinked, evStandalone),
            )
        )

        assertEquals(4, notes.size)
        assertTrue(notes.any { it.sourceLabel == "Calendar Case" && it.body == "Prepare stay application" })
        assertTrue(notes.any { it.sourceLabel == "Chamber Cal" && it.body == "Inspect original record" })
        assertTrue(notes.any { it.sourceLabel == "Google Keep" && it.header == "Supreme Court Precedents" })
        assertTrue(notes.any { it.sourceLabel == "Task Note" && it.body == "Attach annexure B" })
    }
}
