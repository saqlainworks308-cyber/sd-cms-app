package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class LitigationReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(LitigationReminderScheduler.EXTRA_TITLE)
            ?: "DILAWAR & SAQLAIN CMS"
        val body = intent.getStringExtra(LitigationReminderScheduler.EXTRA_BODY)
            ?: "Upcoming litigation deadline or court hearing."
        val notifId = intent.getIntExtra(
            LitigationReminderScheduler.EXTRA_NOTIF_ID,
            System.currentTimeMillis().toInt()
        )
        LitigationReminderScheduler.showInstantNotification(context, notifId, title, body)
    }
}
