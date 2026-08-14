package com.example.foodiary.presentation.fragment

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.foodiary.R
import com.example.foodiary.presentation.util.replaceFragmentSafely
import com.example.foodiary.presentation.util.setDebouncedClickListener

class ProfileHubFragment : Fragment(R.layout.fragment_profile_hub) {

    companion object {
        private const val ARG_RETURN_DAY_START = "arg_return_day_start"

        fun newInstance(returnDayStart: Long = System.currentTimeMillis()): ProfileHubFragment {
            return ProfileHubFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_RETURN_DAY_START, returnDayStart)
                }
            }
        }
    }

    private val returnDayStart: Long by lazy {
        arguments?.getLong(ARG_RETURN_DAY_START) ?: System.currentTimeMillis()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val personalization = view.findViewById<LinearLayout>(R.id.layoutPersonalizationRows)
        val additional = view.findViewById<LinearLayout>(R.id.layoutAdditionalRows)

        addRow(
            container = personalization,
            iconRes = android.R.drawable.ic_menu_myplaces,
            title = "Учетная запись",
            subtitle = "Локальная почта и персональные контактные данные"
        ) {
            replaceFragmentSafely(AccountProfileFragment.newInstance(returnDayStart))
        }
        addRow(
            container = personalization,
            iconRes = android.R.drawable.ic_menu_manage,
            title = "Параметры тела",
            subtitle = "Пол, возраст, рост, масса тела, жир, активность и ограничения"
        ) {
            replaceFragmentSafely(ProfileSettingsFragment.newInstance(returnDayStart))
        }
        addRow(
            container = personalization,
            iconRes = android.R.drawable.ic_menu_sort_by_size,
            title = "Суточные нормы",
            subtitle = "Ручная настройка калорий и КБЖУ поверх автоматической базы"
        ) {
            replaceFragmentSafely(DailyTargetsSettingsFragment.newInstance())
        }
        addRow(
            container = personalization,
            iconRes = android.R.drawable.ic_menu_agenda,
            title = "Приемы пищи",
            subtitle = "Структура дня, доли калорий и дополнительные слоты"
        ) {
            replaceFragmentSafely(MealScheduleSettingsFragment.newInstance())
        }
        addRow(
            container = personalization,
            iconRes = android.R.drawable.ic_menu_mylocation,
            title = "Изменить цель",
            subtitle = "Отдельный экран выбора цели с мгновенным пересчетом КБЖУ"
        ) {
            replaceFragmentSafely(GoalSettingsFragment.newInstance(returnDayStart))
        }

        addRow(
            container = additional,
            iconRes = android.R.drawable.ic_lock_idle_alarm,
            title = "Уведомления",
            subtitle = "Напоминания о еде, воде, активности и весе"
        ) {
            replaceFragmentSafely(NotificationPreferencesFragment.newInstance(returnDayStart))
        }
        addRow(
            container = additional,
            iconRes = android.R.drawable.stat_sys_data_bluetooth,
            title = "Настройки сервисов",
            subtitle = "Камера, фотоанализ, погода и внешние сервисы"
        ) {
            replaceFragmentSafely(ServiceSettingsFragment.newInstance())
        }
        addRow(
            container = additional,
            iconRes = android.R.drawable.ic_menu_help,
            title = "Поддержка",
            subtitle = "FAQ, диагностика и письмо разработчику"
        ) {
            replaceFragmentSafely(SupportFragment.newInstance())
        }
    }

    private fun addRow(
        container: LinearLayout,
        iconRes: Int,
        title: String,
        subtitle: String,
        onClick: () -> Unit
    ) {
        val row = layoutInflater.inflate(R.layout.item_settings_nav_row, container, false)
        row.findViewById<ImageView>(R.id.imageIcon).setImageResource(iconRes)
        row.findViewById<TextView>(R.id.textTitle).text = title
        row.findViewById<TextView>(R.id.textSubtitle).apply {
            text = subtitle
            visibility = View.VISIBLE
        }
        row.setDebouncedClickListener { onClick() }
        container.addView(row)
    }
}
