package com.example.foodiary.presentation.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.foodiary.data.local.preferences.ReminderPreferences
import com.example.foodiary.domain.model.MealType
import com.example.foodiary.testing.AndroidTestStateHelper
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar

@RunWith(AndroidJUnit4::class)
class ReminderSystemIntegrationTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        AndroidTestStateHelper.resetAll()
    }

    @After
    fun tearDown() {
        cancelPendingIntent(RECEIVER_TEST_REQUEST_CODE)
        cancelPendingIntent(BREAKFAST_REQUEST_CODE)
        cancelPendingIntent(WEIGHT_REQUEST_CODE)
        notificationManager().cancel(RECEIVER_TEST_TYPE.hashCode())
        AndroidTestStateHelper.resetAll()
    }

    @Test
    fun reminder_receiver_creates_channel_and_reschedules_next_alarm() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager().deleteNotificationChannel(ReminderScheduler.CHANNEL_ID)
        }

        ReminderReceiver().onReceive(
            context,
            Intent(context, ReminderReceiver::class.java).apply {
                putExtra(ReminderScheduler.EXTRA_TITLE, "Foodiary test")
                putExtra(ReminderScheduler.EXTRA_MESSAGE, "Reminder integration test")
                putExtra(ReminderScheduler.EXTRA_TYPE, RECEIVER_TEST_TYPE)
                putExtra(ReminderScheduler.EXTRA_REPEAT_KIND, ReminderScheduler.REPEAT_DAILY)
                putExtra(ReminderScheduler.EXTRA_REQUEST_CODE, RECEIVER_TEST_REQUEST_CODE)
                putExtra(ReminderScheduler.EXTRA_HOUR, 8)
                putExtra(ReminderScheduler.EXTRA_MINUTE, 0)
            }
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            assertNotNull(notificationManager().getNotificationChannel(ReminderScheduler.CHANNEL_ID))
        }
        assertNotNull(findPendingIntent(RECEIVER_TEST_REQUEST_CODE))
    }

    @Test
    fun scheduler_respects_enabled_and_disabled_meal_reminders() {
        val prefs = ReminderPreferences(context)
        val scheduler = ReminderScheduler(context)

        prefs.setMealReminder(
            MealType.BREAKFAST,
            ReminderPreferences.DailyReminder(enabled = true, hour = 8, minute = 0)
        )
        scheduler.rescheduleAll()
        assertNotNull(findPendingIntent(BREAKFAST_REQUEST_CODE))

        prefs.setMealReminder(
            MealType.BREAKFAST,
            ReminderPreferences.DailyReminder(enabled = false, hour = 8, minute = 0)
        )
        scheduler.rescheduleAll()
        assertNull(findPendingIntent(BREAKFAST_REQUEST_CODE))
    }

    @Test
    fun scheduler_rejects_stale_reminder_intents_after_preferences_change() {
        val prefs = ReminderPreferences(context)
        val scheduler = ReminderScheduler(context)

        prefs.setMealReminder(
            MealType.BREAKFAST,
            ReminderPreferences.DailyReminder(enabled = true, hour = 8, minute = 0)
        )
        val activeIntent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderScheduler.EXTRA_TYPE, "meal_${MealType.BREAKFAST.name}")
            putExtra(ReminderScheduler.EXTRA_HOUR, 8)
            putExtra(ReminderScheduler.EXTRA_MINUTE, 0)
        }
        assertTrue(scheduler.isReminderEnabledForIntent(activeIntent))

        prefs.setMealReminder(
            MealType.BREAKFAST,
            ReminderPreferences.DailyReminder(enabled = false, hour = 8, minute = 0)
        )
        assertFalse(scheduler.isReminderEnabledForIntent(activeIntent))
    }

    @Test
    fun receiver_does_not_reschedule_disabled_stale_meal_reminder() {
        ReminderPreferences(context).setMealReminder(
            MealType.BREAKFAST,
            ReminderPreferences.DailyReminder(enabled = false, hour = 8, minute = 0)
        )

        ReminderReceiver().onReceive(
            context,
            Intent(context, ReminderReceiver::class.java).apply {
                putExtra(ReminderScheduler.EXTRA_TITLE, "Завтрак")
                putExtra(ReminderScheduler.EXTRA_MESSAGE, "Старое напоминание")
                putExtra(ReminderScheduler.EXTRA_TYPE, "meal_${MealType.BREAKFAST.name}")
                putExtra(ReminderScheduler.EXTRA_REPEAT_KIND, ReminderScheduler.REPEAT_DAILY)
                putExtra(ReminderScheduler.EXTRA_REQUEST_CODE, BREAKFAST_REQUEST_CODE)
                putExtra(ReminderScheduler.EXTRA_HOUR, 8)
                putExtra(ReminderScheduler.EXTRA_MINUTE, 0)
            }
        )

        assertNull(findPendingIntent(BREAKFAST_REQUEST_CODE))
    }

    @Test
    fun boot_receiver_reschedules_existing_preferences_without_crashing() {
        ReminderPreferences(context).setWeightReminder(
            ReminderPreferences.WeeklyReminder(
                enabled = true,
                dayOfWeek = Calendar.MONDAY,
                hour = 7,
                minute = 30
            )
        )

        ReminderBootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertNotNull(findPendingIntent(WEIGHT_REQUEST_CODE))
    }

    private fun notificationManager(): NotificationManager {
        return context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private fun findPendingIntent(requestCode: Int): PendingIntent? {
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun cancelPendingIntent(requestCode: Int) {
        findPendingIntent(requestCode)?.cancel()
    }

    private companion object {
        const val RECEIVER_TEST_TYPE = "receiver_integration_test"
        const val RECEIVER_TEST_REQUEST_CODE = 987_321
        const val BREAKFAST_REQUEST_CODE = 1000 + 0
        const val WEIGHT_REQUEST_CODE = 4000
    }
}
