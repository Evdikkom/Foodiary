package com.example.foodiary.presentation.fragment

import android.Manifest
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.example.foodiary.R
import com.example.foodiary.data.local.preferences.MealSchedulePreferences
import com.example.foodiary.data.local.preferences.ReminderPreferences
import com.example.foodiary.data.local.preferences.UiPreferences
import com.example.foodiary.domain.model.MealType
import com.example.foodiary.presentation.notification.ReminderNotificationHelper
import com.example.foodiary.presentation.notification.ReminderScheduler
import com.example.foodiary.presentation.util.displayName
import com.example.foodiary.presentation.util.popBackStackSafely
import com.example.foodiary.presentation.util.setDebouncedClickListener
import java.util.Calendar
import java.util.Locale

class NotificationPreferencesFragment : Fragment(R.layout.fragment_notification_preferences) {

    companion object {
        fun newInstance(returnDayStart: Long = System.currentTimeMillis()): NotificationPreferencesFragment {
            return NotificationPreferencesFragment().apply {
                arguments = Bundle().apply { putLong("ignored_return_day_start", returnDayStart) }
            }
        }
    }

    private lateinit var reminderPreferences: ReminderPreferences
    private lateinit var mealSchedulePreferences: MealSchedulePreferences
    private lateinit var scheduler: ReminderScheduler
    private lateinit var uiPreferences: UiPreferences

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        renderPermissionHint(requireView())
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        reminderPreferences = ReminderPreferences(requireContext())
        mealSchedulePreferences = MealSchedulePreferences(requireContext())
        scheduler = ReminderScheduler(requireContext())
        uiPreferences = UiPreferences(requireContext())
        ReminderNotificationHelper.ensureReminderChannel(requireContext())

        view.findViewById<ImageView>(R.id.buttonBack).setDebouncedClickListener {
            popBackStackSafely()
        }

        renderPermissionHint(view)
        renderMealReminderRows(view)
        renderWaterReminderRows(view)
        renderOtherReminderRows(view)
        view.findViewById<TextView>(R.id.buttonNotificationPermissionAction).setDebouncedClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                startActivity(ReminderNotificationHelper.buildNotificationSettingsIntent(requireContext()))
            }
        }

        view.findViewById<TextView>(R.id.buttonAddWaterReminder).setDebouncedClickListener {
            val nextCount = (reminderPreferences.getWaterReminderCount() + 1).coerceAtMost(3)
            reminderPreferences.setWaterReminderCount(nextCount)
            renderWaterReminderRows(requireView())
            scheduler.rescheduleAll()
        }

        val popup = view.findViewById<SwitchCompat>(R.id.switchRecommendationPopup)
        val section = view.findViewById<SwitchCompat>(R.id.switchRecommendationSection)
        popup.isChecked = uiPreferences.isRecommendationPopupEnabled()
        section.isChecked = uiPreferences.isRecommendationSectionEnabled()
        popup.setOnCheckedChangeListener { _, checked ->
            uiPreferences.setRecommendationPopupEnabled(checked)
        }
        section.setOnCheckedChangeListener { _, checked ->
            uiPreferences.setRecommendationSectionEnabled(checked)
        }
    }

    private fun renderPermissionHint(root: View) {
        val hint = root.findViewById<TextView>(R.id.textPermissionHint)
        val action = root.findViewById<TextView>(R.id.buttonNotificationPermissionAction)
        val hasRuntimePermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        val notificationsEnabled = ReminderNotificationHelper.areReminderNotificationsEnabled(requireContext())
        val shouldShowHint = !hasRuntimePermission || !notificationsEnabled
        hint.isVisible = shouldShowHint
        action.isVisible = shouldShowHint
        hint.text = if (!hasRuntimePermission) {
            "Android пока не разрешил Foodiary отправлять уведомления. Включите доступ, чтобы напоминания появлялись вовремя."
        } else {
            "Системные уведомления или канал напоминаний Foodiary выключены в Android. Напоминания сохранены, но не будут видны до включения."
        }
        action.text = if (!hasRuntimePermission) "Разрешить уведомления" else "Открыть настройки Android"
    }

    private fun renderMealReminderRows(root: View) {
        val container = root.findViewById<LinearLayout>(R.id.layoutMealReminderRows)
        container.removeAllViews()
        mealSchedulePreferences.getEnabledMealTypes().forEach { mealType ->
            container.addView(createDailyReminderRow(
                title = mealType.displayName(),
                reminder = reminderPreferences.getMealReminder(mealType),
                onToggle = { enabled ->
                    if (enabled && !ensureNotificationPermission()) return@createDailyReminderRow false
                    reminderPreferences.setMealReminder(
                        mealType,
                        reminderPreferences.getMealReminder(mealType).copy(enabled = enabled)
                    )
                    scheduler.rescheduleAll()
                    true
                },
                onTimePicked = { hour, minute ->
                    reminderPreferences.setMealReminder(
                        mealType,
                        reminderPreferences.getMealReminder(mealType).copy(hour = hour, minute = minute)
                    )
                    scheduler.rescheduleAll()
                }
            ))
        }
    }

    private fun renderWaterReminderRows(root: View) {
        val container = root.findViewById<LinearLayout>(R.id.layoutWaterReminderRows)
        container.removeAllViews()
        val count = reminderPreferences.getWaterReminderCount()
        repeat(count) { index ->
            container.addView(createDailyReminderRow(
                title = if (index == 0) "Вода" else "Вода ${index + 1}",
                reminder = reminderPreferences.getWaterReminder(index),
                onToggle = { enabled ->
                    if (enabled && !ensureNotificationPermission()) return@createDailyReminderRow false
                    reminderPreferences.setWaterReminder(
                        index,
                        reminderPreferences.getWaterReminder(index).copy(enabled = enabled)
                    )
                    scheduler.rescheduleAll()
                    true
                },
                onTimePicked = { hour, minute ->
                    reminderPreferences.setWaterReminder(
                        index,
                        reminderPreferences.getWaterReminder(index).copy(hour = hour, minute = minute)
                    )
                    scheduler.rescheduleAll()
                }
            ))
        }
        root.findViewById<TextView>(R.id.buttonAddWaterReminder).isVisible = count < 3
    }

    private fun renderOtherReminderRows(root: View) {
        val container = root.findViewById<LinearLayout>(R.id.layoutOtherReminderRows)
        container.removeAllViews()

        container.addView(createDailyReminderRow(
            title = "Активность",
            reminder = reminderPreferences.getActivityReminder(),
            onToggle = { enabled ->
                if (enabled && !ensureNotificationPermission()) return@createDailyReminderRow false
                reminderPreferences.setActivityReminder(
                    reminderPreferences.getActivityReminder().copy(enabled = enabled)
                )
                scheduler.rescheduleAll()
                true
            },
            onTimePicked = { hour, minute ->
                reminderPreferences.setActivityReminder(
                    reminderPreferences.getActivityReminder().copy(hour = hour, minute = minute)
                )
                scheduler.rescheduleAll()
            }
        ))

        val weightRow = layoutInflater.inflate(R.layout.item_reminder_row, container, false)
        val weightSwitch = weightRow.findViewById<SwitchCompat>(R.id.switchReminder)
        val weightTitle = weightRow.findViewById<TextView>(R.id.textReminderTitle)
        val weightTime = weightRow.findViewById<TextView>(R.id.buttonReminderTime)
        val weightReminder = reminderPreferences.getWeightReminder()
        applyReminderRowSpacing(weightRow)
        weightTitle.text = "Вес"
        weightSwitch.isChecked = weightReminder.enabled
        weightTime.text = buildWeeklyLabel(weightReminder.dayOfWeek, weightReminder.hour, weightReminder.minute)
        weightSwitch.setOnCheckedChangeListener { _, enabled ->
            if (enabled && !ensureNotificationPermission()) {
                weightSwitch.isChecked = false
                return@setOnCheckedChangeListener
            }
            reminderPreferences.setWeightReminder(weightReminder.copy(enabled = enabled))
            scheduler.rescheduleAll()
        }
        weightTime.setDebouncedClickListener {
            openWeekdayAndTimePicker(weightReminder.dayOfWeek, weightReminder.hour, weightReminder.minute) { day, hour, minute ->
                reminderPreferences.setWeightReminder(
                    reminderPreferences.getWeightReminder().copy(dayOfWeek = day, hour = hour, minute = minute)
                )
                renderOtherReminderRows(requireView())
                scheduler.rescheduleAll()
            }
        }
        container.addView(weightRow)
    }

    private fun createDailyReminderRow(
        title: String,
        reminder: ReminderPreferences.DailyReminder,
        onToggle: (Boolean) -> Boolean,
        onTimePicked: (Int, Int) -> Unit
    ): View {
        val row = layoutInflater.inflate(R.layout.item_reminder_row, null, false)
        val switch = row.findViewById<SwitchCompat>(R.id.switchReminder)
        val rowTitle = row.findViewById<TextView>(R.id.textReminderTitle)
        val timeButton = row.findViewById<TextView>(R.id.buttonReminderTime)

        applyReminderRowSpacing(row)
        rowTitle.text = title
        switch.isChecked = reminder.enabled
        timeButton.text = buildTimeLabel(reminder.hour, reminder.minute)

        switch.setOnCheckedChangeListener { _, enabled ->
            val applied = onToggle(enabled)
            if (!applied) {
                switch.isChecked = false
            }
        }
        timeButton.setDebouncedClickListener {
            openTimePicker(reminder.hour, reminder.minute) { hour, minute ->
                timeButton.text = buildTimeLabel(hour, minute)
                onTimePicked(hour, minute)
            }
        }
        return row
    }

    private fun applyReminderRowSpacing(row: View) {
        val marginTop = (12f * resources.displayMetrics.density).toInt()
        val params = (row.layoutParams as? LinearLayout.LayoutParams)
            ?: LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        params.width = ViewGroup.LayoutParams.MATCH_PARENT
        params.height = ViewGroup.LayoutParams.WRAP_CONTENT
        params.topMargin = marginTop
        row.layoutParams = params
    }

    private fun ensureNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        val granted = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) return true
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        return false
    }

    private fun openTimePicker(initialHour: Int, initialMinute: Int, onSelected: (Int, Int) -> Unit) {
        TimePickerDialog(requireContext(), { _, hour, minute ->
            onSelected(hour, minute)
        }, initialHour, initialMinute, true).show()
    }

    private fun openWeekdayAndTimePicker(
        initialDay: Int,
        initialHour: Int,
        initialMinute: Int,
        onSelected: (Int, Int, Int) -> Unit
    ) {
        val weekdays = arrayOf("Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье")
        val calendarDays = arrayOf(
            Calendar.MONDAY,
            Calendar.TUESDAY,
            Calendar.WEDNESDAY,
            Calendar.THURSDAY,
            Calendar.FRIDAY,
            Calendar.SATURDAY,
            Calendar.SUNDAY
        )
        val checkedIndex = calendarDays.indexOf(initialDay).coerceAtLeast(0)
        AlertDialog.Builder(requireContext())
            .setTitle("День для напоминания")
            .setSingleChoiceItems(weekdays, checkedIndex) { dialog, which ->
                val selectedDay = calendarDays[which]
                dialog.dismiss()
                openTimePicker(initialHour, initialMinute) { hour, minute ->
                    onSelected(selectedDay, hour, minute)
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun buildTimeLabel(hour: Int, minute: Int): String {
        return String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
    }

    private fun buildWeeklyLabel(dayOfWeek: Int, hour: Int, minute: Int): String {
        val short = when (dayOfWeek) {
            Calendar.MONDAY -> "пн"
            Calendar.TUESDAY -> "вт"
            Calendar.WEDNESDAY -> "ср"
            Calendar.THURSDAY -> "чт"
            Calendar.FRIDAY -> "пт"
            Calendar.SATURDAY -> "сб"
            else -> "вс"
        }
        return "$short ${buildTimeLabel(hour, minute)}"
    }
}
