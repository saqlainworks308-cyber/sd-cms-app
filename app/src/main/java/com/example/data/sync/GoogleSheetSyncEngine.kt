package com.example.data.sync

import com.example.data.model.CasePriority
import com.example.data.model.CaseStatus
import com.example.data.model.LitigationCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

object GoogleSheetSyncEngine {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private val sheetIdRegex = Regex("""spreadsheets/d/([a-zA-Z0-9\-_]+)""")
    private val gidRegex = Regex("""gid=(\d+)""")

    fun toCsvDownloadUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        if (trimmed.contains("output=csv", ignoreCase = true) ||
            trimmed.contains("format=csv", ignoreCase = true) ||
            trimmed.contains("tqx=out:csv", ignoreCase = true)
        ) {
            return trimmed
        }
        val idMatch = sheetIdRegex.find(trimmed) ?: return trimmed
        val sheetId = idMatch.groupValues[1]
        val gid = gidRegex.find(trimmed)?.groupValues?.getOrNull(1) ?: "0"
        return "https://docs.google.com/spreadsheets/d/$sheetId/export?format=csv&gid=$gid"
    }

    suspend fun fetchSheetCases(sheetUrl: String): Result<List<LitigationCase>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val csvUrl = toCsvDownloadUrl(sheetUrl)
                val request = Request.Builder()
                    .url(csvUrl)
                    .header("User-Agent", "DilawarSaqlainCMS/1.0")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        error("Google Sheet returned HTTP ${response.code}. Ensure link sharing ('Anyone with link can view') or 'Publish to web (CSV)' is enabled.")
                    }
                    val body = response.body?.string() ?: error("Empty Google Sheet response")
                    if (body.trimStart().startsWith("<!DOCTYPE html", ignoreCase = true) ||
                        body.trimStart().startsWith("<html", ignoreCase = true)
                    ) {
                        error("Google Sheet requires 'Anyone with the link' view access or 'Publish to the web' CSV link.")
                    }
                    parseCsvCases(body, sheetUrl)
                }
            }
        }

    fun parseCsvCases(csvText: String, sourceUrl: String): List<LitigationCase> {
        val rows = parseCsvRows(csvText)
        if (rows.size < 2) return emptyList()

        val headers = rows.first().map { it.trim().lowercase(Locale.US) }
        fun findCol(vararg candidates: String): Int =
            headers.indexOfFirst { h -> candidates.any { c -> h.contains(c) } }

        val caseNoCol = findCol("case no", "case number", "docket", "suit no", "number")
        val titleCol = findCol("title", "case name", "caption", "parties", "matter")
        val courtCol = findCol("court", "forum", "tribunal")
        val roomCol = findCol("room", "bench")
        val judgeCol = findCol("judge", "justice", "coram")
        val clientCol = findCol("client")
        val emailCol = findCol("email")
        val oppCol = findCol("opposing", "respondent", "defendant")
        val stageCol = findCol("stage", "purpose", "proceeding")
        val statusCol = findCol("status")
        val priorityCol = findCol("priority", "urgency")
        val hearingCol = findCol("next hearing", "hearing", "court date", "date")
        val deadlineCol = findCol("deadline", "filing")
        val keepCol = findCol("keep", "note link")
        val notesCol = findCol("notes", "remarks", "brief")

        val results = mutableListOf<LitigationCase>()
        for (row in rows.drop(1)) {
            fun cell(idx: Int): String = if (idx in row.indices) row[idx].trim() else ""

            val caseNumber = cell(caseNoCol).ifBlank {
                if (titleCol >= 0) "CASE-${results.size + 1}" else ""
            }
            val title = cell(titleCol).ifBlank { caseNumber }
            if (caseNumber.isBlank() || title.isBlank()) continue

            results.add(
                LitigationCase(
                    caseNumber = caseNumber.take(80),
                    title = title.take(200),
                    courtName = cell(courtCol).take(160),
                    courtRoom = cell(roomCol).take(80),
                    judgeName = cell(judgeCol).take(120),
                    clientName = cell(clientCol).take(140),
                    clientEmail = cell(emailCol).take(160),
                    opposingParty = cell(oppCol).take(160),
                    stage = cell(stageCol).take(100),
                    status = CaseStatus.fromWire(cell(statusCol)).wireValue,
                    priority = CasePriority.fromWire(cell(priorityCol)).wireValue,
                    nextHearingEpochMs = parseFlexibleDateMs(cell(hearingCol)),
                    filingDeadlineEpochMs = parseFlexibleDateMs(cell(deadlineCol)),
                    googleKeepUrl = cell(keepCol).take(500),
                    googleSheetUrl = sourceUrl.take(500),
                    notes = cell(notesCol).take(4000),
                )
            )
        }
        return results
    }

    fun exportCasesToCsv(cases: List<LitigationCase>): String {
        val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        fun esc(s: String): String = "\"${s.replace("\"", "\"\"")}\""
        val sb = StringBuilder()
        sb.appendLine("Case Number,Title,Court,Court Room,Judge,Client,Client Email,Opposing Party,Stage,Status,Priority,Next Hearing,Filing Deadline,Google Keep URL,Notes")
        cases.forEach { c ->
            val hearing = if (c.nextHearingEpochMs > 0L) dateFmt.format(c.nextHearingEpochMs) else ""
            val deadline = if (c.filingDeadlineEpochMs > 0L) dateFmt.format(c.filingDeadlineEpochMs) else ""
            sb.appendLine(
                listOf(
                    c.caseNumber, c.title, c.courtName, c.courtRoom, c.judgeName,
                    c.clientName, c.clientEmail, c.opposingParty, c.stage,
                    c.status, c.priority, hearing, deadline, c.googleKeepUrl, c.notes
                ).joinToString(",") { esc(it) }
            )
        }
        return sb.toString()
    }

    private fun parseCsvRows(csv: String): List<List<String>> {
        val rows = mutableListOf<MutableList<String>>()
        var currentRow = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < csv.length) {
            val ch = csv[i]
            when {
                inQuotes -> {
                    if (ch == '"' && i + 1 < csv.length && csv[i + 1] == '"') {
                        field.append('"')
                        i++
                    } else if (ch == '"') {
                        inQuotes = false
                    } else {
                        field.append(ch)
                    }
                }
                ch == '"' -> inQuotes = true
                ch == ',' -> {
                    currentRow.add(field.toString())
                    field.clear()
                }
                ch == '\n' || ch == '\r' -> {
                    if (ch == '\r' && i + 1 < csv.length && csv[i + 1] == '\n') i++
                    currentRow.add(field.toString())
                    field.clear()
                    if (currentRow.any { it.isNotBlank() }) rows.add(currentRow)
                    currentRow = mutableListOf()
                }
                else -> field.append(ch)
            }
            i++
        }
        if (field.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow.add(field.toString())
            if (currentRow.any { it.isNotBlank() }) rows.add(currentRow)
        }
        return rows
    }

    private fun parseFlexibleDateMs(raw: String): Long {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return 0L
        trimmed.toLongOrNull()?.let { if (it > 1_000_000_000_000L) return it }
        val patterns = listOf(
            "yyyy-MM-dd HH:mm",
            "yyyy-MM-dd'T'HH:mm",
            "yyyy-MM-dd",
            "dd/MM/yyyy HH:mm",
            "dd/MM/yyyy",
            "MM/dd/yyyy",
            "dd-MM-yyyy",
            "MMM dd, yyyy",
        )
        for (p in patterns) {
            try {
                val fmt = SimpleDateFormat(p, Locale.US)
                val parsed = fmt.parse(trimmed)
                if (parsed != null) return parsed.time
            } catch (_: Exception) {
            }
        }
        return 0L
    }
}
