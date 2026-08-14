package com.example.foodiary.presentation.notification

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.foodiary.R

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val scheduler = ReminderScheduler(context)
        if (!scheduler.isReminderEnabledForIntent(intent)) return

        val title = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE).orEmpty()
        val message = intent.getStringExtra(ReminderScheduler.EXTRA_MESSAGE).orEmpty()
        val type = intent.getStringExtra(ReminderScheduler.EXTRA_TYPE).orEmpty()
        val requestCode = intent.getIntExtra(ReminderScheduler.EXTRA_REQUEST_CODE, type.hashCode())
        val contentIntent = ReminderNotificationHelper.buildOpenAppPendingIntent(
            context = context,
            requestCode = requestCode,
            reminderType = type
        )

        ReminderNotificationHelper.ensureReminderChannel(context)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(
            type.hashCode(),
            NotificationCompat.Builder(context, ReminderScheduler.CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .addAction(R.mipmap.ic_launcher, "Открыть", contentIntent)
                .build()
        )

        scheduler.scheduleNextFromIntent(intent)
    }
}
