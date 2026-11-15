package com.example.data.sync

import android.content.Intent
import android.net.Uri
import com.example.data.model.LitigationCase
import com.example.data.model.LitigationTask
import com.example.data.model.SyncedCalendarEvent
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class CauseListDraft(
    val rangeLabel: String,
    val subject: String,
    val body: String,
    val hearingCount: Int,
    val deadlineCount: Int,
    val eventCount: Int,
    val suggestedRecipients: List<String>,
)

object CauseListEmailComposer {

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

    fun endOfDayMs(epochMs: Long): Long = startOfDayMs(epochMs) + 86_400_000L - 1L

    fun buildCauseListDraft(
        startDateMs: Long,
        endDateMs: Long,
        allCases: List<LitigationCase>,
        allCalendarEvents: List<SyncedCalendarEvent>,
        allTasks: List<LitigationTask>,
        counselName: String,
    ): CauseListDraft {
        val rangeStart = startOfDayMs(minOf(startDateMs, endDateMs))
        val rangeEnd = endOfDayMs(maxOf(startDateMs, endDateMs))

        val dateFmt = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault())
        val shortDateFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val dateTimeFmt = SimpleDateFormat("EEE, dd MMM • hh:mm a", Locale.getDefault())

        val isSingleDay = startOfDayMs(rangeStart) == startOfDayMs(rangeEnd)
        val rangeLabel = if (isSingleDay) {
            dateFmt.format(Date(rangeStart))
        } else {
            "${shortDateFmt.format(Date(rangeStart))} – ${shortDateFmt.format(Date(rangeEnd))}"
        }

        val hearingsInRange = allCases
            .filter { it.nextHearingEpochMs in rangeStart..rangeEnd }
            .sortedBy { it.nextHearingEpochMs }

        val deadlinesInRange = allCases
            .filter { it.filingDeadlineEpochMs in rangeStart..rangeEnd }
            .sortedBy { it.filingDeadlineEpochMs }

        val eventsInRange = allCalendarEvents
            .filter { it.startEpochMs in rangeStart..rangeEnd }
            .sortedBy { it.startEpochMs }

        val tasksInRange = allTasks
            .filter { !it.completed && (it.dueEpochMs == 0L || it.dueEpochMs in rangeStart..rangeEnd) }
            .take(10)

        val suggestedRecipients = (hearingsInRange + deadlinesInRange)
            .map { it.clientEmail.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        val body = buildString {
            appendLine("DILAWAR & SAQLAIN CMS — CAUSE LIST & SCHEDULE")
            appendLine("Period: $rangeLabel")
            if (counselName.isNotBlank()) appendLine("Prepared by: $counselName")
            appendLine("==================================================")
            appendLine()

            appendLine("1. COURT HEARINGS (${hearingsInRange.size})")
            if (hearingsInRange.isEmpty()) {
                appendLine("   No court hearings scheduled within this date range.")
            } else {
                hearingsInRange.forEachIndexed { idx, c ->
                    appendLine("   ${idx + 1}. [${c.caseNumber}] ${c.title}")
                    appendLine("      Date/Time: ${dateTimeFmt.format(Date(c.nextHearingEpochMs))} | Priority: ${c.priority.uppercase(Locale.US)}")
                    val forum = listOf(c.courtName, c.courtRoom, c.judgeName)
                        .filter { it.isNotBlank() }
                        .joinToString(" • ")
                    if (forum.isNotBlank()) appendLine("      Forum: $forum")
                    if (c.stage.isNotBlank()) appendLine("      Stage: ${c.stage}")
                    if (c.clientName.isNotBlank()) appendLine("      Client: ${c.clientName}")
                    if (c.googleKeepUrl.isNotBlank()) appendLine("      Brief (Google Keep): ${c.googleKeepUrl}")
                    if (c.notes.isNotBlank()) appendLine("      Notes: ${c.notes.take(200)}")
                    appendLine()
                }
            }
            appendLine()

            appendLine("2. FILING DEADLINES (${deadlinesInRange.size})")
            if (deadlinesInRange.isEmpty()) {
                appendLine("   No filing deadlines within this date range.")
            } else {
                deadlinesInRange.forEachIndexed { idx, c ->
                    appendLine("   ${idx + 1}. [${c.caseNumber}] ${c.title} — Due: ${dateFmt.format(Date(c.filingDeadlineEpochMs))}")
                    if (c.courtName.isNotBlank()) appendLine("      Court: ${c.courtName}")
                }
            }
            appendLine()

            appendLine("3. SCHEDULED CALENDAR EVENTS (${eventsInRange.size})")
            if (eventsInRange.isEmpty()) {
                appendLine("   No synced calendar events in this date range.")
            } else {
                eventsInRange.forEachIndexed { idx, ev ->
                    val whenStr = if (ev.allDay) {
                        "${shortDateFmt.format(Date(ev.startEpochMs))} (All Day)"
                    } else {
                        dateTimeFmt.format(Date(ev.startEpochMs))
                    }
                    appendLine("   ${idx + 1}. ${ev.title} — $whenStr")
                    if (ev.location.isNotBlank()) appendLine("      Location: ${ev.location}")
                    if (ev.sourceLabel.isNotBlank()) appendLine("      Calendar: ${ev.sourceLabel}")
                    if (ev.keepNoteUrl.isNotBlank()) appendLine("      Google Keep: ${ev.keepNoteUrl}")
                }
            }

            if (tasksInRange.isNotEmpty()) {
                appendLine()
                appendLine("4. PRIORITY CHAMBERS TASKS (${tasksInRange.size})")
                tasksInRange.forEachIndexed { idx, t ->
                    val caseTag = if (t.caseNumber.isNotBlank()) "[${t.caseNumber}] " else ""
                    appendLine("   ${idx + 1}. $caseTag${t.title} (${t.priority.uppercase(Locale.US)})")
                }
            }
        }

        val subject = "Cause List ($rangeLabel) — DILAWAR & SAQLAIN CMS"
        return CauseListDraft(
            rangeLabel = rangeLabel,
            subject = subject,
            body = body,
            hearingCount = hearingsInRange.size,
            deadlineCount = deadlinesInRange.size,
            eventCount = eventsInRange.size,
            suggestedRecipients = suggestedRecipients,
        )
    }

    fun buildSendCauseListEmailIntent(
        recipientEmails: String,
        subject: String,
        body: String,
    ): Intent {
        val recipients = recipientEmails
            .split(',', ';')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toTypedArray()

        return Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            if (recipients.isNotEmpty()) {
                putExtra(Intent.EXTRA_EMAIL, recipients)
            }
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
    }
}
