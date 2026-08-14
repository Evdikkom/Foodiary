package com.example.foodiary.presentation.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.example.foodiary.presentation.activity.MainActivity

object ReminderNotificationHelper {

    fun ensureReminderChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(ReminderScheduler.CHANNEL_ID) != null) return

        manager.createNotificationChannel(
            NotificationChannel(
                ReminderScheduler.CHANNEL_ID,
                "Напоминания Foodiary",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Напоминания о приёмах пищи, воде и активности"
            }
        )
    }

    fun areReminderNotificationsEnabled(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = manager.getNotificationChannel(ReminderScheduler.CHANNEL_ID) ?: return true
        return channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun buildOpenAppPendingIntent(
        context: Context,
        requestCode: Int,
        reminderType: String
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(ReminderScheduler.EXTRA_TYPE, reminderType)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun buildNotificationSettingsIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        }
    }
}
