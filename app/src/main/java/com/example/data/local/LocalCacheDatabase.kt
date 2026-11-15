package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import com.example.data.model.LitigationCase
import com.example.data.model.LitigationTask
import com.example.data.model.PlatformLink
import com.example.data.model.SyncedCalendarEvent
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "cases")
data class LocalCaseEntity(
    @PrimaryKey val id: String,
    val caseNumber: String,
    val title: String,
    val courtName: String,
    val courtRoom: String,
    val judgeName: String,
    val clientName: String,
    val clientEmail: String,
    val opposingParty: String,
    val opposingCounsel: String,
    val stage: String,
    val status: String,
    val priority: String,
    val nextHearingEpochMs: Long,
    val filingDeadlineEpochMs: Long,
    val hearingDurationMinutes: Long,
    val reminderMinutesBefore: Long,
    val googleKeepUrl: String,
    val googleKeepLabel: String,
    val googleSheetUrl: String,
    val calendarEventId: String,
    val calendarSourceId: String,
    val notes: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

@Entity(tableName = "tasks")
data class LocalTaskEntity(
    @PrimaryKey val id: String,
    val caseId: String,
    val caseNumber: String,
    val title: String,
    val description: String,
    val category: String,
    val priority: String,
    val dueEpochMs: Long,
    val completed: Boolean,
    val reminderEnabled: Boolean,
    val keepNoteUrl: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

@Entity(tableName = "platform_links")
data class LocalPlatformLinkEntity(
    @PrimaryKey val id: String,
    val type: String,
    val label: String,
    val url: String,
    val autoSync: Boolean,
    val lastSyncedEpochMs: Long,
    val syncedItemCount: Long,
    val lastError: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

@Entity(tableName = "calendar_events")
data class LocalCalendarEventEntity(
    @PrimaryKey val id: String,
    val externalUid: String,
    val sourceId: String,
    val sourceLabel: String,
    val title: String,
    val location: String,
    val description: String,
    val startEpochMs: Long,
    val endEpochMs: Long,
    val allDay: Boolean,
    val linkedCaseId: String,
    val keepNoteUrl: String,
    val deviceEventId: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

fun LitigationCase.toEntity(nowMs: Long = System.currentTimeMillis()): LocalCaseEntity =
    LocalCaseEntity(
        id = id,
        caseNumber = caseNumber,
        title = title,
        courtName = courtName,
        courtRoom = courtRoom,
        judgeName = judgeName,
        clientName = clientName,
        clientEmail = clientEmail,
        opposingParty = opposingParty,
        opposingCounsel = opposingCounsel,
        stage = stage,
        status = status,
        priority = priority,
        nextHearingEpochMs = nextHearingEpochMs,
        filingDeadlineEpochMs = filingDeadlineEpochMs,
        hearingDurationMinutes = hearingDurationMinutes,
        reminderMinutesBefore = reminderMinutesBefore,
        googleKeepUrl = googleKeepUrl,
        googleKeepLabel = googleKeepLabel,
        googleSheetUrl = googleSheetUrl,
        calendarEventId = calendarEventId,
        calendarSourceId = calendarSourceId,
        notes = notes,
        createdAtEpochMs = if (createdAtEpochMs > 0L) createdAtEpochMs else nowMs,
        updatedAtEpochMs = nowMs,
    )

fun LocalCaseEntity.toDomain(): LitigationCase = LitigationCase(
    id = id,
    caseNumber = caseNumber,
    title = title,
    courtName = courtName,
    courtRoom = courtRoom,
    judgeName = judgeName,
    clientName = clientName,
    clientEmail = clientEmail,
    opposingParty = opposingParty,
    opposingCounsel = opposingCounsel,
    stage = stage,
    status = status,
    priority = priority,
    nextHearingEpochMs = nextHearingEpochMs,
    filingDeadlineEpochMs = filingDeadlineEpochMs,
    hearingDurationMinutes = hearingDurationMinutes,
    reminderMinutesBefore = reminderMinutesBefore,
    googleKeepUrl = googleKeepUrl,
    googleKeepLabel = googleKeepLabel,
    googleSheetUrl = googleSheetUrl,
    calendarEventId = calendarEventId,
    calendarSourceId = calendarSourceId,
    notes = notes,
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs,
)

fun LitigationTask.toEntity(nowMs: Long = System.currentTimeMillis()): LocalTaskEntity =
    LocalTaskEntity(
        id = id,
        caseId = caseId,
        caseNumber = caseNumber,
        title = title,
        description = description,
        category = category,
        priority = priority,
        dueEpochMs = dueEpochMs,
        completed = completed,
        reminderEnabled = reminderEnabled,
        keepNoteUrl = keepNoteUrl,
        createdAtEpochMs = if (createdAtEpochMs > 0L) createdAtEpochMs else nowMs,
        updatedAtEpochMs = nowMs,
    )

fun LocalTaskEntity.toDomain(): LitigationTask = LitigationTask(
    id = id,
    caseId = caseId,
    caseNumber = caseNumber,
    title = title,
    description = description,
    category = category,
    priority = priority,
    dueEpochMs = dueEpochMs,
    completed = completed,
    reminderEnabled = reminderEnabled,
    keepNoteUrl = keepNoteUrl,
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs,
)

fun PlatformLink.toEntity(nowMs: Long = System.currentTimeMillis()): LocalPlatformLinkEntity =
    LocalPlatformLinkEntity(
        id = id,
        type = type,
        label = label,
        url = url,
        autoSync = autoSync,
        lastSyncedEpochMs = lastSyncedEpochMs,
        syncedItemCount = syncedItemCount,
        lastError = lastError,
        createdAtEpochMs = if (createdAtEpochMs > 0L) createdAtEpochMs else nowMs,
        updatedAtEpochMs = nowMs,
    )

fun LocalPlatformLinkEntity.toDomain(): PlatformLink = PlatformLink(
    id = id,
    type = type,
    label = label,
    url = url,
    autoSync = autoSync,
    lastSyncedEpochMs = lastSyncedEpochMs,
    syncedItemCount = syncedItemCount,
    lastError = lastError,
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs,
)

fun SyncedCalendarEvent.toEntity(nowMs: Long = System.currentTimeMillis()): LocalCalendarEventEntity =
    LocalCalendarEventEntity(
        id = id,
        externalUid = externalUid,
        sourceId = sourceId,
        sourceLabel = sourceLabel,
        title = title,
        location = location,
        description = description,
        startEpochMs = startEpochMs,
        endEpochMs = endEpochMs,
        allDay = allDay,
        linkedCaseId = linkedCaseId,
        keepNoteUrl = keepNoteUrl,
        deviceEventId = deviceEventId,
        createdAtEpochMs = if (createdAtEpochMs > 0L) createdAtEpochMs else nowMs,
        updatedAtEpochMs = nowMs,
    )

fun LocalCalendarEventEntity.toDomain(): SyncedCalendarEvent = SyncedCalendarEvent(
    id = id,
    externalUid = externalUid,
    sourceId = sourceId,
    sourceLabel = sourceLabel,
    title = title,
    location = location,
    description = description,
    startEpochMs = startEpochMs,
    endEpochMs = endEpochMs,
    allDay = allDay,
    linkedCaseId = linkedCaseId,
    keepNoteUrl = keepNoteUrl,
    deviceEventId = deviceEventId,
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs,
)

@Dao
interface LocalCaseDao {
    @Query("SELECT * FROM cases ORDER BY nextHearingEpochMs ASC")
    fun observeAll(): Flow<List<LocalCaseEntity>>

    @Query("SELECT * FROM cases ORDER BY nextHearingEpochMs ASC")
    suspend fun getAllOnce(): List<LocalCaseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: LocalCaseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<LocalCaseEntity>)

    @Query("DELETE FROM cases WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface LocalTaskDao {
    @Query("SELECT * FROM tasks ORDER BY completed ASC, dueEpochMs ASC")
    fun observeAll(): Flow<List<LocalTaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: LocalTaskEntity)

    @Query("UPDATE tasks SET completed = :completed, updatedAtEpochMs = :updatedAt WHERE id = :id")
    suspend fun updateCompleted(id: String, completed: Boolean, updatedAt: Long)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface LocalPlatformLinkDao {
    @Query("SELECT * FROM platform_links ORDER BY label ASC")
    fun observeAll(): Flow<List<LocalPlatformLinkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: LocalPlatformLinkEntity)

    @Query("DELETE FROM platform_links WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface LocalCalendarEventDao {
    @Query("SELECT * FROM calendar_events ORDER BY startEpochMs ASC")
    fun observeAll(): Flow<List<LocalCalendarEventEntity>>

    @Query("SELECT * FROM calendar_events ORDER BY startEpochMs ASC")
    suspend fun getAllOnce(): List<LocalCalendarEventEntity>

    @Query("SELECT * FROM calendar_events WHERE sourceId = :sourceId")
    suspend fun getBySourceId(sourceId: String): List<LocalCalendarEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: LocalCalendarEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<LocalCalendarEventEntity>)

    @Query("DELETE FROM calendar_events WHERE sourceId = :sourceId")
    suspend fun deleteBySourceId(sourceId: String)

    @Query("DELETE FROM calendar_events WHERE linkedCaseId = :caseId")
    suspend fun deleteByLinkedCaseId(caseId: String)

    @Query("DELETE FROM calendar_events WHERE id = :id")
    suspend fun deleteById(id: String)

    @Transaction
    suspend fun replaceForSource(sourceId: String, events: List<LocalCalendarEventEntity>) {
        deleteBySourceId(sourceId)
        if (events.isNotEmpty()) {
            upsertAll(events)
        }
    }
}

@Database(
    entities = [
        LocalCaseEntity::class,
        LocalTaskEntity::class,
        LocalPlatformLinkEntity::class,
        LocalCalendarEventEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class LitigationLocalDatabase : RoomDatabase() {
    abstract fun localCaseDao(): LocalCaseDao
    abstract fun localTaskDao(): LocalTaskDao
    abstract fun localPlatformLinkDao(): LocalPlatformLinkDao
    abstract fun localCalendarEventDao(): LocalCalendarEventDao

    companion object {
        @Volatile
        private var INSTANCE: LitigationLocalDatabase? = null

        fun getInstance(context: Context): LitigationLocalDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LitigationLocalDatabase::class.java,
                    "dilawar_saqlain_cms_local.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
