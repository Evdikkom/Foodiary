package com.example.foodiary.data.local.preferences

import android.content.Context
import com.example.foodiary.domain.model.MealType
import com.example.foodiary.presentation.util.configurableMealTypes
import com.example.foodiary.presentation.util.defaultReminderHour
import com.example.foodiary.presentation.util.defaultReminderMinute

class ReminderPreferences(context: Context) {

    data class DailyReminder(
        val enabled: Boolean,
        val hour: Int,
        val minute: Int
    )

    data class WeeklyReminder(
        val enabled: Boolean,
        val dayOfWeek: Int,
        val hour: Int,
        val minute: Int
    )

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getMealReminder(mealType: MealType): DailyReminder {
        return DailyReminder(
            enabled = prefs.getBoolean("${mealType.name}_enabled", false),
            hour = prefs.getInt("${mealType.name}_hour", mealType.defaultReminderHour()),
            minute = prefs.getInt("${mealType.name}_minute", mealType.defaultReminderMinute())
        )
    }

    fun setMealReminder(mealType: MealType, reminder: DailyReminder) {
        prefs.edit()
            .putBoolean("${mealType.name}_enabled", reminder.enabled)
            .putInt("${mealType.name}_hour", reminder.hour)
            .putInt("${mealType.name}_minute", reminder.minute)
            .apply()
    }

    fun getWaterReminderCount(): Int = prefs.getInt(KEY_WATER_COUNT, 1).coerceIn(1, 3)

    fun setWaterReminderCount(count: Int) {
        prefs.edit().putInt(KEY_WATER_COUNT, count.coerceIn(1, 3)).apply()
    }

    fun getWaterReminder(index: Int): DailyReminder {
        val defaults = listOf(12 to 0, 15 to 0, 18 to 0)
        val safeIndex = index.coerceIn(0, 2)
        return DailyReminder(
            enabled = prefs.getBoolean("water_${safeIndex}_enabled", false),
            hour = prefs.getInt("water_${safeIndex}_hour", defaults[safeIndex].first),
            minute = prefs.getInt("water_${safeIndex}_minute", defaults[safeIndex].second)
        )
    }

    fun setWaterReminder(index: Int, reminder: DailyReminder) {
        val safeIndex = index.coerceIn(0, 2)
        prefs.edit()
            .putBoolean("water_${safeIndex}_enabled", reminder.enabled)
            .putInt("water_${safeIndex}_hour", reminder.hour)
            .putInt("water_${safeIndex}_minute", reminder.minute)
            .apply()
    }

    fun getActivityReminder(): DailyReminder {
        return DailyReminder(
            enabled = prefs.getBoolean(KEY_ACTIVITY_ENABLED, false),
            hour = prefs.getInt(KEY_ACTIVITY_HOUR, 19),
            minute = prefs.getInt(KEY_ACTIVITY_MINUTE, 0)
        )
    }

    fun setActivityReminder(reminder: DailyReminder) {
        prefs.edit()
            .putBoolean(KEY_ACTIVITY_ENABLED, reminder.enabled)
            .putInt(KEY_ACTIVITY_HOUR, reminder.hour)
            .putInt(KEY_ACTIVITY_MINUTE, reminder.minute)
            .apply()
    }

    fun getWeightReminder(): WeeklyReminder {
        return WeeklyReminder(
            enabled = prefs.getBoolean(KEY_WEIGHT_ENABLED, false),
            dayOfWeek = prefs.getInt(KEY_WEIGHT_DAY, java.util.Calendar.MONDAY),
            hour = prefs.getInt(KEY_WEIGHT_HOUR, 7),
            minute = prefs.getInt(KEY_WEIGHT_MINUTE, 30)
        )
    }

    fun setWeightReminder(reminder: WeeklyReminder) {
        prefs.edit()
            .putBoolean(KEY_WEIGHT_ENABLED, reminder.enabled)
            .putInt(KEY_WEIGHT_DAY, reminder.dayOfWeek)
            .putInt(KEY_WEIGHT_HOUR, reminder.hour)
            .putInt(KEY_WEIGHT_MINUTE, reminder.minute)
            .apply()
    }

    fun ensureDefaultsForEnabledMeals(enabledMeals: List<MealType>) {
        configurableMealTypes().forEach { mealType ->
            if (mealType !in enabledMeals && !prefs.contains("${mealType.name}_enabled")) {
                setMealReminder(
                    mealType,
                    DailyReminder(enabled = false, hour = mealType.defaultReminderHour(), minute = mealType.defaultReminderMinute())
                )
            }
        }
    }

    companion object {
        private const val PREFS_NAME = "foodiary_reminders"
        private const val KEY_WATER_COUNT = "water_count"
        private const val KEY_ACTIVITY_ENABLED = "activity_enabled"
        private const val KEY_ACTIVITY_HOUR = "activity_hour"
        private const val KEY_ACTIVITY_MINUTE = "activity_minute"
        private const val KEY_WEIGHT_ENABLED = "weight_enabled"
        private const val KEY_WEIGHT_DAY = "weight_day"
        private const val KEY_WEIGHT_HOUR = "weight_hour"
        private const val KEY_WEIGHT_MINUTE = "weight_minute"
    }
}
