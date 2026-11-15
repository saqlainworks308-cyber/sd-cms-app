package com.example.data.model

enum class CaseStatus(val wireValue: String, val displayLabel: String) {
    ACTIVE("active", "Active"),
    PENDING_FILING("pending_filing", "Pending Filing"),
    ADJOURNED("adjourned", "Adjourned"),
    DISPOSED("disposed", "Disposed");

    companion object {
        fun fromWire(value: String): CaseStatus =
            entries.firstOrNull { it.wireValue.equals(value, ignoreCase = true) } ?: ACTIVE
    }
}

enum class CasePriority(val wireValue: String, val displayLabel: String) {
    CRITICAL("critical", "Critical"),
    HIGH("high", "High"),
    STANDARD("standard", "Standard");

    companion object {
        fun fromWire(value: String): CasePriority =
            entries.firstOrNull { it.wireValue.equals(value, ignoreCase = true) } ?: STANDARD
    }
}

enum class TaskCategory(val wireValue: String, val displayLabel: String) {
    HEARING_PREP("hearing_prep", "Hearing Prep"),
    FILING("filing", "Filing"),
    CLIENT_CALL("client_call", "Client Call"),
    RESEARCH("research", "Legal Research"),
    CAUSE_LIST("cause_list", "Cause List"),
    GENERAL("general", "General");

    companion object {
        fun fromWire(value: String): TaskCategory =
            entries.firstOrNull { it.wireValue.equals(value, ignoreCase = true) } ?: GENERAL
    }
}

enum class IntegrationType(val wireValue: String, val displayLabel: String) {
    ICS_CALENDAR("ics_calendar", "Calendar Link (.ics / webcal)"),
    GOOGLE_SHEET("google_sheet", "Google Sheet Link"),
    GOOGLE_KEEP("google_keep", "Google Keep Link");

    companion object {
        fun fromWire(value: String): IntegrationType =
            entries.firstOrNull { it.wireValue.equals(value, ignoreCase = true) } ?: ICS_CALENDAR
    }
}

data class LitigationCase(
    val id: String = "",
    val caseNumber: String = "",
    val title: String = "",
    val courtName: String = "",
    val courtRoom: String = "",
    val judgeName: String = "",
    val clientName: String = "",
    val clientEmail: String = "",
    val opposingParty: String = "",
    val opposingCounsel: String = "",
    val stage: String = "",
    val status: String = CaseStatus.ACTIVE.wireValue,
    val priority: String = CasePriority.STANDARD.wireValue,
    val nextHearingEpochMs: Long = 0L,
    val filingDeadlineEpochMs: Long = 0L,
    val hearingDurationMinutes: Long = 45L,
    val reminderMinutesBefore: Long = 120L,
    val googleKeepUrl: String = "",
    val googleKeepLabel: String = "",
    val googleSheetUrl: String = "",
    val calendarEventId: String = "",
    val calendarSourceId: String = "",
    val notes: String = "",
    val createdAtEpochMs: Long = 0L,
    val updatedAtEpochMs: Long = 0L,
)

data class LitigationTask(
    val id: String = "",
    val caseId: String = "",
    val caseNumber: String = "",
    val title: String = "",
    val description: String = "",
    val category: String = TaskCategory.GENERAL.wireValue,
    val priority: String = CasePriority.STANDARD.wireValue,
    val dueEpochMs: Long = 0L,
    val completed: Boolean = false,
    val reminderEnabled: Boolean = true,
    val keepNoteUrl: String = "",
    val createdAtEpochMs: Long = 0L,
    val updatedAtEpochMs: Long = 0L,
)

data class PlatformLink(
    val id: String = "",
    val type: String = IntegrationType.ICS_CALENDAR.wireValue,
    val label: String = "",
    val url: String = "",
    val autoSync: Boolean = true,
    val lastSyncedEpochMs: Long = 0L,
    val syncedItemCount: Long = 0L,
    val lastError: String = "",
    val createdAtEpochMs: Long = 0L,
    val updatedAtEpochMs: Long = 0L,
)

data class SyncedCalendarEvent(
    val id: String = "",
    val externalUid: String = "",
    val sourceId: String = "",
    val sourceLabel: String = "",
    val title: String = "",
    val location: String = "",
    val description: String = "",
    val startEpochMs: Long = 0L,
    val endEpochMs: Long = 0L,
    val allDay: Boolean = false,
    val linkedCaseId: String = "",
    val keepNoteUrl: String = "",
    val deviceEventId: String = "",
    val createdAtEpochMs: Long = 0L,
    val updatedAtEpochMs: Long = 0L,
)
