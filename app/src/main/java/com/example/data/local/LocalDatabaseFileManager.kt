package com.example.data.local

import android.content.Context
import android.net.Uri
import com.example.data.model.CasePriority
import com.example.data.model.CaseStatus
import com.example.data.model.LitigationCase
import com.example.data.model.LitigationTask
import com.example.data.model.TaskCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class ImportedDatabaseBundle(
    val cases: List<LitigationCase>,
    val tasks: List<LitigationTask>,
)

class LocalDatabaseFileManager(private val context: Context) {

    suspend fun exportToJsonUri(
        uri: Uri,
        cases: List<LitigationCase>,
        tasks: List<LitigationTask>,
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val root = JSONObject().apply {
                put("app", "DILAWAR & SAQLAIN CMS")
                put("schemaVersion", 1)
                put("exportedAtEpochMs", System.currentTimeMillis())
                put("cases", JSONArray().apply {
                    cases.forEach { c ->
                        put(JSONObject().apply {
                            put("caseNumber", c.caseNumber)
                            put("title", c.title)
                            put("courtName", c.courtName)
                            put("courtRoom", c.courtRoom)
                            put("judgeName", c.judgeName)
                            put("clientName", c.clientName)
                            put("clientEmail", c.clientEmail)
                            put("opposingParty", c.opposingParty)
                            put("opposingCounsel", c.opposingCounsel)
                            put("stage", c.stage)
                            put("status", c.status)
                            put("priority", c.priority)
                            put("nextHearingEpochMs", c.nextHearingEpochMs)
                            put("filingDeadlineEpochMs", c.filingDeadlineEpochMs)
                            put("hearingDurationMinutes", c.hearingDurationMinutes)
                            put("reminderMinutesBefore", c.reminderMinutesBefore)
                            put("googleKeepUrl", c.googleKeepUrl)
                            put("googleKeepLabel", c.googleKeepLabel)
                            put("googleSheetUrl", c.googleSheetUrl)
                            put("calendarEventId", c.calendarEventId)
                            put("notes", c.notes)
                        })
                    }
                })
                put("tasks", JSONArray().apply {
                    tasks.forEach { t ->
                        put(JSONObject().apply {
                            put("caseNumber", t.caseNumber)
                            put("title", t.title)
                            put("description", t.description)
                            put("category", t.category)
                            put("priority", t.priority)
                            put("dueEpochMs", t.dueEpochMs)
                            put("completed", t.completed)
                            put("reminderEnabled", t.reminderEnabled)
                            put("keepNoteUrl", t.keepNoteUrl)
                        })
                    }
                })
            }

            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(root.toString(2).toByteArray(Charsets.UTF_8))
                stream.flush()
            } ?: error("Unable to open local destination file.")

            cases.size + tasks.size
        }
    }

    suspend fun importFromUri(uri: Uri): Result<ImportedDatabaseBundle> = withContext(Dispatchers.IO) {
        runCatching {
            val content = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
                it.readText()
            } ?: error("Unable to read selected case database file.")

            val trimmed = content.trim()
            if (trimmed.startsWith("{")) {
                parseJsonBundle(trimmed)
            } else {
                val cases = com.example.data.sync.GoogleSheetSyncEngine.parseCsvCases(trimmed, sourceUrl = "")
                ImportedDatabaseBundle(cases = cases, tasks = emptyList())
            }
        }
    }

    private fun parseJsonBundle(jsonText: String): ImportedDatabaseBundle {
        val root = JSONObject(jsonText)
        val casesArray = root.optJSONArray("cases") ?: JSONArray()
        val cases = buildList {
            for (i in 0 until casesArray.length()) {
                val obj = casesArray.optJSONObject(i) ?: continue
                val caseNo = obj.optString("caseNumber").trim()
                val title = obj.optString("title").trim()
                if (caseNo.isBlank() || title.isBlank()) continue
                add(
                    LitigationCase(
                        caseNumber = caseNo,
                        title = title,
                        courtName = obj.optString("courtName"),
                        courtRoom = obj.optString("courtRoom"),
                        judgeName = obj.optString("judgeName"),
                        clientName = obj.optString("clientName"),
                        clientEmail = obj.optString("clientEmail"),
                        opposingParty = obj.optString("opposingParty"),
                        opposingCounsel = obj.optString("opposingCounsel"),
                        stage = obj.optString("stage"),
                        status = CaseStatus.fromWire(obj.optString("status")).wireValue,
                        priority = CasePriority.fromWire(obj.optString("priority")).wireValue,
                        nextHearingEpochMs = obj.optLong("nextHearingEpochMs", 0L).coerceAtLeast(0L),
                        filingDeadlineEpochMs = obj.optLong("filingDeadlineEpochMs", 0L).coerceAtLeast(0L),
                        hearingDurationMinutes = obj.optLong("hearingDurationMinutes", 45L).coerceIn(5L, 1440L),
                        reminderMinutesBefore = obj.optLong("reminderMinutesBefore", 120L).coerceIn(0L, 10080L),
                        googleKeepUrl = obj.optString("googleKeepUrl"),
                        googleKeepLabel = obj.optString("googleKeepLabel"),
                        googleSheetUrl = obj.optString("googleSheetUrl"),
                        calendarEventId = obj.optString("calendarEventId"),
                        notes = obj.optString("notes"),
                    )
                )
            }
        }

        val tasksArray = root.optJSONArray("tasks") ?: JSONArray()
        val tasks = buildList {
            for (i in 0 until tasksArray.length()) {
                val obj = tasksArray.optJSONObject(i) ?: continue
                val title = obj.optString("title").trim()
                if (title.isBlank()) continue
                add(
                    LitigationTask(
                        caseNumber = obj.optString("caseNumber"),
                        title = title,
                        description = obj.optString("description"),
                        category = TaskCategory.fromWire(obj.optString("category")).wireValue,
                        priority = CasePriority.fromWire(obj.optString("priority")).wireValue,
                        dueEpochMs = obj.optLong("dueEpochMs", 0L).coerceAtLeast(0L),
                        completed = obj.optBoolean("completed", false),
                        reminderEnabled = obj.optBoolean("reminderEnabled", true),
                        keepNoteUrl = obj.optString("keepNoteUrl"),
                    )
                )
            }
        }
        return ImportedDatabaseBundle(cases = cases, tasks = tasks)
    }
}
