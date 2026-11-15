package com.example.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncParsersUnitTest {

    @Test
    fun parseIcsContent_extractsEventsAndKeepUrl() {
        val sampleIcs = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:hearing-101@google.com
            DTSTART:20261120T050000Z
            DTEND:20261120T060000Z
            SUMMARY:[WP-404/2026] Saqlain v. Federation
            LOCATION:High Court • Bench 2
            DESCRIPTION:Arguments on stay.\nBrief: https://keep.google.com/#NOTE/xyz123
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val events = IcsCalendarParser.parseIcsContent(sampleIcs, "src_1", "Chambers Cal")
        assertEquals(1, events.size)
        val ev = events.first()
        assertEquals("hearing-101@google.com", ev.externalUid)
        assertEquals("[WP-404/2026] Saqlain v. Federation", ev.title)
        assertEquals("High Court • Bench 2", ev.location)
        assertEquals("https://keep.google.com/#NOTE/xyz123", ev.keepNoteUrl)
        assertTrue(ev.startEpochMs > 0L)
    }

    @Test
    fun googleSheetCsv_parsesCasesAndConvertsUrl() {
        val csvUrl = GoogleSheetSyncEngine.toCsvDownloadUrl(
            "https://docs.google.com/spreadsheets/d/abc123XYZ/edit#gid=42"
        )
        assertEquals("https://docs.google.com/spreadsheets/d/abc123XYZ/export?format=csv&gid=42", csvUrl)

        val csv = """
            Case Number,Title,Court,Room,Stage,Priority,Next Hearing,Google Keep
            CS-88/2026,"Dilawar Corp v. Apex Bank",Civil Court,Room 7,Evidence,Critical,2026-11-22 10:30,https://keep.google.com/#NOTE/note88
        """.trimIndent()

        val parsed = GoogleSheetSyncEngine.parseCsvCases(csv, csvUrl)
        assertEquals(1, parsed.size)
        val c = parsed.first()
        assertEquals("CS-88/2026", c.caseNumber)
        assertEquals("Dilawar Corp v. Apex Bank", c.title)
        assertEquals("critical", c.priority)
        assertEquals("https://keep.google.com/#NOTE/note88", c.googleKeepUrl)
        assertTrue(c.nextHearingEpochMs > 0L)
    }

    @Test
    fun causeListDraft_filtersCasesDeadlinesAndEventsByDateRange() {
        val day1 = CauseListEmailComposer.startOfDayMs(1_795_000_000_000L) + 36_000_000L // +10h
        val day3 = day1 + (2L * 86_400_000L)
        val day10 = day1 + (9L * 86_400_000L)

        val cases = listOf(
            com.example.data.model.LitigationCase(
                id = "c1",
                caseNumber = "WP-10/2026",
                title = "Alpha v. State",
                clientEmail = "alpha@client.com",
                nextHearingEpochMs = day1,
                filingDeadlineEpochMs = day3,
            ),
            com.example.data.model.LitigationCase(
                id = "c2",
                caseNumber = "WP-99/2026",
                title = "Out of Range Case",
                nextHearingEpochMs = day10,
            ),
        )
        val events = listOf(
            com.example.data.model.SyncedCalendarEvent(
                id = "e1",
                title = "Client Conference",
                startEpochMs = day3,
                endEpochMs = day3 + 3_600_000L,
            ),
            com.example.data.model.SyncedCalendarEvent(
                id = "e2",
                title = "Future Hearing",
                startEpochMs = day10,
                endEpochMs = day10 + 3_600_000L,
            ),
        )

        val draft = CauseListEmailComposer.buildCauseListDraft(
            startDateMs = day1,
            endDateMs = day3,
            allCases = cases,
            allCalendarEvents = events,
            allTasks = emptyList(),
            counselName = "Adv. Saqlain",
        )

        assertEquals(1, draft.hearingCount)
        assertEquals(1, draft.deadlineCount)
        assertEquals(1, draft.eventCount)
        assertTrue(draft.body.contains("WP-10/2026"))
        assertTrue(draft.body.contains("Client Conference"))
        assertTrue(!draft.body.contains("WP-99/2026"))
        assertEquals(listOf("alpha@client.com"), draft.suggestedRecipients)
    }

    @Test
    fun normalizeCalendarUrl_handlesWebcalAndGoogleShareLinks() {
        assertEquals(
            "https://example.com/court.ics",
            IcsCalendarParser.normalizeCalendarUrl("webcal://example.com/court.ics")
        )
        assertEquals(
            "https://calendar.google.com/calendar/ical/chambers%40gmail.com/public/basic.ics",
            IcsCalendarParser.normalizeCalendarUrl("https://calendar.google.com/calendar/embed?src=chambers%40gmail.com&ctz=Asia/Karachi")
        )
    }

    @Test
    fun parseIcsContent_expandsWeeklyRecurringHearings() {
        val sample = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:weekly-mention@chambers
            DTSTART:20261115T090000Z
            DTEND:20261115T100000Z
            RRULE:FREQ=WEEKLY;COUNT=3
            SUMMARY:Weekly Cause Review
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        val events = IcsCalendarParser.parseIcsContent(
            icsText = sample,
            sourceId = "feed1",
            sourceLabel = "Chambers",
            referenceNowMs = 1_794_730_000_000L,
        )
        assertEquals(3, events.size)
        assertTrue(events[1].startEpochMs > events[0].startEpochMs)
    }

    @Test
    fun icsCalendarExporter_buildsValidCalendarForCasesAndRoundTrips() {
        val hearingMs = 1_795_000_000_000L
        val deadlineMs = hearingMs + 86_400_000L
        val item = com.example.data.model.LitigationCase(
            id = "case_export_1",
            caseNumber = "WP-77/2026",
            title = "Dilawar v. Authority",
            courtName = "High Court",
            nextHearingEpochMs = hearingMs,
            filingDeadlineEpochMs = deadlineMs,
            hearingDurationMinutes = 60L,
        )
        val ics = IcsCalendarExporter.buildIcsForCases(listOf(item))
        assertTrue(ics.contains("BEGIN:VCALENDAR"))
        assertTrue(ics.contains("UID:case_export_1@sd-cms.app"))
        assertTrue(ics.contains("SUMMARY:[WP-77/2026] Dilawar v. Authority"))
        assertTrue(ics.contains("SUMMARY:DEADLINE: [WP-77/2026] Dilawar v. Authority"))

        val parsed = IcsCalendarParser.parseIcsContent(ics, "exp", "SD-CMS")
        assertEquals(2, parsed.size)
    }
}
