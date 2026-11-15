package com.example.data.sync

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.model.LitigationCase
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object IcsCalendarExporter {

    private fun utcFormat(): SimpleDateFormat =
        SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

    private fun escapeIcs(text: String): String =
        text.replace("\\", "\\\\")
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .replace("\n", "\\n")
            .replace(",", "\\,")
            .replace(";", "\\;")

    fun buildIcsForCases(
        cases: List<LitigationCase>,
        calendarName: String = "SD-CMS Court Docket",
    ): String {
        val fmt = utcFormat()
        val nowStamp = fmt.format(Date())

        return buildString {
            appendLine("BEGIN:VCALENDAR")
            appendLine("VERSION:2.0")
            appendLine("PRODID:-//Dilawar & Saqlain//SD-CMS//EN")
            appendLine("CALSCALE:GREGORIAN")
            appendLine("METHOD:PUBLISH")
            appendLine("X-WR-CALNAME:${escapeIcs(calendarName)}")

            cases.forEach { c ->
                val seq = ((c.updatedAtEpochMs.takeIf { it > 0L } ?: System.currentTimeMillis()) / 1000L)
                    .coerceAtLeast(1L)
                val baseUid = c.calendarEventId.ifBlank { "${c.id}@sd-cms.app" }
                val summaryTitle = if (c.caseNumber.isNotBlank()) "[${c.caseNumber}] ${c.title}" else c.title
                val location = listOf(c.courtName, c.courtRoom).filter { it.isNotBlank() }.joinToString(" • ")
                val fullNotes = buildString {
                    if (c.stage.isNotBlank()) appendLine("Stage: ${c.stage}")
                    if (c.clientName.isNotBlank()) appendLine("Client: ${c.clientName}")
                    if (c.judgeName.isNotBlank()) appendLine("Judge: ${c.judgeName}")
                    if (c.notes.isNotBlank()) appendLine(c.notes)
                    if (c.googleKeepUrl.isNotBlank()) appendLine("Keep Note: ${c.googleKeepUrl}")
                }.trim()

                if (c.nextHearingEpochMs > 0L) {
                    val endMs = c.nextHearingEpochMs + (c.hearingDurationMinutes.coerceIn(15L, 480L) * 60_000L)
                    appendLine("BEGIN:VEVENT")
                    appendLine("UID:$baseUid")
                    appendLine("DTSTAMP:$nowStamp")
                    appendLine("LAST-MODIFIED:$nowStamp")
                    appendLine("SEQUENCE:$seq")
                    appendLine("DTSTART:${fmt.format(Date(c.nextHearingEpochMs))}")
                    appendLine("DTEND:${fmt.format(Date(endMs))}")
                    appendLine("SUMMARY:${escapeIcs(summaryTitle)}")
                    if (location.isNotBlank()) appendLine("LOCATION:${escapeIcs(location)}")
                    if (fullNotes.isNotBlank()) appendLine("DESCRIPTION:${escapeIcs(fullNotes)}")
                    appendLine("END:VEVENT")
                }

                if (c.filingDeadlineEpochMs > 0L) {
                    val deadlineEnd = c.filingDeadlineEpochMs + 3_600_000L
                    appendLine("BEGIN:VEVENT")
                    appendLine("UID:${c.id}-deadline@sd-cms.app")
                    appendLine("DTSTAMP:$nowStamp")
                    appendLine("LAST-MODIFIED:$nowStamp")
                    appendLine("SEQUENCE:$seq")
                    appendLine("DTSTART:${fmt.format(Date(c.filingDeadlineEpochMs))}")
                    appendLine("DTEND:${fmt.format(Date(deadlineEnd))}")
                    appendLine("SUMMARY:${escapeIcs("DEADLINE: $summaryTitle")}")
                    if (location.isNotBlank()) appendLine("LOCATION:${escapeIcs(location)}")
                    if (fullNotes.isNotBlank()) appendLine("DESCRIPTION:${escapeIcs(fullNotes)}")
                    appendLine("END:VEVENT")
                }
            }

            appendLine("END:VCALENDAR")
        }
    }

    fun openCaseInExternalCalendar(context: Context, caseItem: LitigationCase) {
        val startMs = when {
            caseItem.nextHearingEpochMs > 0L -> caseItem.nextHearingEpochMs
            caseItem.filingDeadlineEpochMs > 0L -> caseItem.filingDeadlineEpochMs
            else -> {
                Toast.makeText(context, "Set a hearing or deadline date first", Toast.LENGTH_SHORT).show()
                return
            }
        }
        val endMs = startMs + (caseItem.hearingDurationMinutes.coerceIn(15L, 480L) * 60_000L)
        val summaryTitle = if (caseItem.caseNumber.isNotBlank()) {
            "[${caseItem.caseNumber}] ${caseItem.title}"
        } else {
            caseItem.title
        }
        val location = listOf(caseItem.courtName, caseItem.courtRoom)
            .filter { it.isNotBlank() }
            .joinToString(" • ")
        val description = buildString {
            if (caseItem.stage.isNotBlank()) appendLine("Stage: ${caseItem.stage}")
            if (caseItem.notes.isNotBlank()) appendLine(caseItem.notes)
            if (caseItem.googleKeepUrl.isNotBlank()) appendLine("Keep Note: ${caseItem.googleKeepUrl}")
        }.trim()

        val insertIntent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, summaryTitle)
            putExtra(CalendarContract.Events.EVENT_LOCATION, location)
            putExtra(CalendarContract.Events.DESCRIPTION, description)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMs)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMs)
        }

        runCatching {
            context.startActivity(insertIntent)
        }.onFailure {
            shareCasesAsIcsFeed(context, listOf(caseItem), fileName = "SD-CMS-Case-Event.ics")
        }
    }

    fun shareCasesAsIcsFeed(
        context: Context,
        cases: List<LitigationCase>,
        fileName: String = "SD-CMS-Calendar.ics",
    ) {
        val datedCases = cases.filter { it.nextHearingEpochMs > 0L || it.filingDeadlineEpochMs > 0L }
        if (datedCases.isEmpty()) {
            Toast.makeText(context, "No dated cases to sync to external calendar", Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            val icsContent = buildIcsForCases(datedCases)
            val shareDir = File(context.cacheDir, "share").apply { mkdirs() }
            val file = File(shareDir, fileName)
            file.writeText(icsContent, Charsets.UTF_8)

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "text/calendar")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(viewIntent, "Sync Dates to Calendar (.ics)")
            context.startActivity(chooser)
        }.onFailure {
            Toast.makeText(context, "Unable to export .ics calendar file", Toast.LENGTH_SHORT).show()
        }
    }
}
