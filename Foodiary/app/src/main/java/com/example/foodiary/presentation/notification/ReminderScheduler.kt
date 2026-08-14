package com.example.foodiary.presentation.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.foodiary.data.local.preferences.MealSchedulePreferences
import com.example.foodiary.data.local.preferences.ReminderPreferences
import com.example.foodiary.domain.model.MealType
import com.example.foodiary.presentation.util.configurableMealTypes
import com.example.foodiary.presentation.util.displayName
import java.util.Calendar

class ReminderScheduler(private val context: Context) {

    private val alarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val reminderPreferences = ReminderPreferences(context)
    private val mealSchedulePreferences = MealSchedulePreferences(context)

    fun rescheduleAll() {
        ReminderNotificationHelper.ensureReminderChannel(context)
        cancelAll()

        val enabledMeals = mealSchedulePreferences.getEnabledMealTypes()
        enabledMeals.forEach { mealType ->
            val reminder = reminderPreferences.getMealReminder(mealType)
            if (reminder.enabled) {
                scheduleDaily(
                    requestCode = requestCodeForMeal(mealType),
                    title = mealType.displayName(),
                    message = "Напоминание добавить ${mealType.displayName().lowercase()} в дневник.",
                    hour = reminder.hour,
                    minute = reminder.minute,
                    reminderType = "meal_${mealType.name}"
                )
            }
        }

        repeat(reminderPreferences.getWaterReminderCount()) { index ->
            val reminder = reminderPreferences.getWaterReminder(index)
            if (reminder.enabled) {
                scheduleDaily(
                    requestCode = REQUEST_CODE_WATER_BASE + index,
                    title = "Вода",
                    message = "Пора напомнить себе о воде и отметить стакан в Foodiary.",
                    hour = reminder.hour,
                    minute = reminder.minute,
                    reminderType = "water_$index"
                )
            }
        }

        val activityReminder = reminderPreferences.getActivityReminder()
        if (activityReminder.enabled) {
            scheduleDaily(
                requestCode = REQUEST_CODE_ACTIVITY,
                title = "Активность",
                message = "Короткая активность поможет поддерживать режим и цель.",
                hour = activityReminder.hour,
                minute = activityReminder.minute,
                reminderType = "activity"
            )
        }

        val weightReminder = reminderPreferences.getWeightReminder()
        if (weightReminder.enabled) {
            scheduleWeekly(
                requestCode = REQUEST_CODE_WEIGHT,
                title = "Вес",
                message = "Пора зафиксировать вес и обновить прогресс.",
                dayOfWeek = weightReminder.dayOfWeek,
                hour = weightReminder.hour,
                minute = weightReminder.minute,
                reminderType = "weight"
            )
        }
    }

    fun scheduleNextFromIntent(intent: Intent) {
        if (!isReminderEnabledForIntent(intent)) return
        when (intent.getStringExtra(EXTRA_REPEAT_KIND)) {
            REPEAT_DAILY -> scheduleDaily(
                requestCode = intent.getIntExtra(EXTRA_REQUEST_CODE, 0),
                title = intent.getStringExtra(EXTRA_TITLE).orEmpty(),
                message = intent.getStringExtra(EXTRA_MESSAGE).orEmpty(),
                hour = intent.getIntExtra(EXTRA_HOUR, 8),
                minute = intent.getIntExtra(EXTRA_MINUTE, 0),
                reminderType = intent.getStringExtra(EXTRA_TYPE).orEmpty()
            )
            REPEAT_WEEKLY -> scheduleWeekly(
                requestCode = intent.getIntExtra(EXTRA_REQUEST_CODE, 0),
                title = intent.getStringExtra(EXTRA_TITLE).orEmpty(),
                message = intent.getStringExtra(EXTRA_MESSAGE).orEmpty(),
                dayOfWeek = intent.getIntExtra(EXTRA_DAY_OF_WEEK, Calendar.MONDAY),
                hour = intent.getIntExtra(EXTRA_HOUR, 8),
                minute = intent.getIntExtra(EXTRA_MINUTE, 0),
                reminderType = intent.getStringExtra(EXTRA_TYPE).orEmpty()
            )
        }
    }

    fun isReminderEnabledForIntent(intent: Intent): Boolean {
        val type = intent.getStringExtra(EXTRA_TYPE).orEmpty()
        val hour = intent.getIntExtra(EXTRA_HOUR, -1)
        val minute = intent.getIntExtra(EXTRA_MINUTE, -1)

        if (type.startsWith("meal_")) {
            val mealType = runCatching {
                MealType.valueOf(type.removePrefix("meal_"))
            }.getOrNull() ?: return false
            if (mealType !in mealSchedulePreferences.getEnabledMealTypes()) return false
            val reminder = reminderPreferences.getMealReminder(mealType)
            return reminder.enabled && reminder.hour == hour && reminder.minute == minute
        }

        if (type.startsWith("water_")) {
            val index = type.removePrefix("water_").toIntOrNull() ?: return false
            if (index !in 0 until reminderPreferences.getWaterReminderCount()) return false
            val reminder = reminderPreferences.getWaterReminder(index)
            return reminder.enabled && reminder.hour == hour && reminder.minute == minute
        }

        if (type == "activity") {
            val reminder = reminderPreferences.getActivityReminder()
            return reminder.enabled && reminder.hour == hour && reminder.minute == minute
        }

        if (type == "weight") {
            val dayOfWeek = intent.getIntExtra(EXTRA_DAY_OF_WEEK, -1)
            val reminder = reminderPreferences.getWeightReminder()
            return reminder.enabled &&
                reminder.dayOfWeek == dayOfWeek &&
                reminder.hour == hour &&
                reminder.minute == minute
        }

        return type.isNotBlank()
    }

    private fun cancelAll() {
        configurableMealTypes().forEach { mealType ->
            cancelPendingIntent(requestCodeForMeal(mealType))
        }
        repeat(3) { index ->
            cancelPendingIntent(REQUEST_CODE_WATER_BASE + index)
        }
        cancelPendingIntent(REQUEST_CODE_ACTIVITY)
        cancelPendingIntent(REQUEST_CODE_WEIGHT)
    }

    private fun scheduleDaily(
        requestCode: Int,
        title: String,
        message: String,
        hour: Int,
        minute: Int,
        reminderType: String
    ) {
        val triggerAt = nextDailyTrigger(hour, minute)
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_MESSAGE, message)
            putExtra(EXTRA_TYPE, reminderType)
            putExtra(EXTRA_REPEAT_KIND, REPEAT_DAILY)
            putExtra(EXTRA_REQUEST_CODE, requestCode)
            putExtra(EXTRA_HOUR, hour)
            putExtra(EXTRA_MINUTE, minute)
        }
        scheduleAlarm(triggerAt, buildPendingIntent(requestCode, intent))
    }

    private fun scheduleWeekly(
        requestCode: Int,
        title: String,
        message: String,
        dayOfWeek: Int,
        hour: Int,
        minute: Int,
        reminderType: String
    ) {
        val triggerAt = nextWeeklyTrigger(dayOfWeek, hour, minute)
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_MESSAGE, message)
            putExtra(EXTRA_TYPE, reminderType)
            putExtra(EXTRA_REPEAT_KIND, REPEAT_WEEKLY)
            putExtra(EXTRA_REQUEST_CODE, requestCode)
            putExtra(EXTRA_DAY_OF_WEEK, dayOfWeek)
            putExtra(EXTRA_HOUR, hour)
            putExtra(EXTRA_MINUTE, minute)
        }
        scheduleAlarm(triggerAt, buildPendingIntent(requestCode, intent))
    }

    private fun scheduleAlarm(triggerAtMillis: Long, pendingIntent: PendingIntent) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    private fun buildPendingIntent(requestCode: Int, intent: Intent): PendingIntent {
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun cancelPendingIntent(requestCode: Int) {
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return

        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun nextDailyTrigger(hour: Int, minute: Int): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        return calendar.timeInMillis
    }

    private fun nextWeeklyTrigger(dayOfWeek: Int, hour: Int, minute: Int): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            set(Calendar.DAY_OF_WEEK, dayOfWeek)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.WEEK_OF_YEAR, 1)
        }
        return calendar.timeInMillis
    }

    private fun requestCodeForMeal(mealType: MealType): Int = REQUEST_CODE_MEAL_BASE + mealType.ordinal

    companion object {
        const val CHANNEL_ID = "foodiary_reminders"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_MESSAGE = "extra_message"
        const val EXTRA_TYPE = "extra_type"
        const val EXTRA_REPEAT_KIND = "extra_repeat_kind"
        const val EXTRA_REQUEST_CODE = "extra_request_code"
        const val EXTRA_DAY_OF_WEEK = "extra_day_of_week"
        const val EXTRA_HOUR = "extra_hour"
        const val EXTRA_MINUTE = "extra_minute"

        const val REPEAT_DAILY = "daily"
        const val REPEAT_WEEKLY = "weekly"

        private const val REQUEST_CODE_MEAL_BASE = 1000
        private const val REQUEST_CODE_WATER_BASE = 2000
        private const val REQUEST_CODE_ACTIVITY = 3000
        private const val REQUEST_CODE_WEIGHT = 4000
    }
}
