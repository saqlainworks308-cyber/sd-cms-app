package com.example.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.LitigationCase
import com.example.data.model.LitigationTask
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LitigationReminderScheduler {

    const val CHANNEL_ID = "litigation_reminders"
    const val EXTRA_TITLE = "extra_title"
    const val EXTRA_BODY = "extra_body"
    const val EXTRA_NOTIF_ID = "extra_notif_id"

    fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.notification_channel_desc)
                    enableVibration(true)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    fun hasNotificationPermission(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    fun scheduleAllReminders(
        context: Context,
        cases: List<LitigationCase>,
        tasks: List<LitigationTask>,
    ): Int {
        ensureNotificationChannel(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = System.currentTimeMillis()
        val timeFmt = SimpleDateFormat("EEE, dd MMM • hh:mm a", Locale.getDefault())
        var scheduledCount = 0

        cases.filter { it.status != "disposed" }.forEach { c ->
            if (c.nextHearingEpochMs > now) {
                val leadMs = c.reminderMinutesBefore.coerceIn(15L, 10080L) * 60_000L
                val triggerAt = (c.nextHearingEpochMs - leadMs).coerceAtLeast(now + 10_000L)
                val courtInfo = listOf(c.courtName, c.courtRoom).filter { it.isNotBlank() }.joinToString(" • ")
                val body = buildString {
                    append("${c.title} at ${timeFmt.format(Date(c.nextHearingEpochMs))}")
                    if (courtInfo.isNotBlank()) append(" ($courtInfo)")
                    if (c.stage.isNotBlank()) append(" — Stage: ${c.stage}")
                }
                scheduleAlarm(
                    context = context,
                    alarmManager = alarmManager,
                    requestCode = ("hearing_${c.id}").hashCode(),
                    triggerAtMs = triggerAt,
                    title = "Court Hearing Reminder: ${c.caseNumber}",
                    body = body,
                )
                scheduledCount++
            }

            if (c.filingDeadlineEpochMs > now) {
                // Remind 24 hours before filing deadline, or 1 hour from now if within 24h
                val triggerAt = (c.filingDeadlineEpochMs - 86_400_000L).coerceAtLeast(now + 15_000L)
                val body = "${c.title} — Filing due ${timeFmt.format(Date(c.filingDeadlineEpochMs))}"
                scheduleAlarm(
                    context = context,
                    alarmManager = alarmManager,
                    requestCode = ("filing_${c.id}").hashCode(),
                    triggerAtMs = triggerAt,
                    title = "Filing Deadline Alert: ${c.caseNumber}",
                    body = body,
                )
                scheduledCount++
            }
        }

        tasks.filter { !it.completed && it.reminderEnabled && it.dueEpochMs > now }.forEach { t ->
            val triggerAt = (t.dueEpochMs - 3_600_000L).coerceAtLeast(now + 20_000L)
            val prefix = if (t.caseNumber.isNotBlank()) "[${t.caseNumber}] " else ""
            scheduleAlarm(
                context = context,
                alarmManager = alarmManager,
                requestCode = ("task_${t.id}").hashCode(),
                triggerAtMs = triggerAt,
                title = "Priority Task Due: $prefix${t.title}",
                body = "Due ${timeFmt.format(Date(t.dueEpochMs))} • Priority: ${t.priority.uppercase(Locale.US)}",
            )
            scheduledCount++
        }

        return scheduledCount
    }

    private fun scheduleAlarm(
        context: Context,
        alarmManager: AlarmManager,
        requestCode: Int,
        triggerAtMs: Long,
        title: String,
        body: String,
    ) {
        val intent = Intent(context, LitigationReminderReceiver::class.java).apply {
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_BODY, body)
            putExtra(EXTRA_NOTIF_ID, requestCode)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pending)
    }

    fun showInstantNotification(
        context: Context,
        notificationId: Int,
        title: String,
        body: String,
    ): Boolean {
        if (!hasNotificationPermission(context)) return false
        ensureNotificationChannel(context)
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPending = PendingIntent.getActivity(
            context,
            notificationId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentPending)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
        return true
    }
}
