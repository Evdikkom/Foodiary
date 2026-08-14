package com.example.foodiary.presentation.fragment

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.foodiary.R
import com.example.foodiary.data.local.database.AppDatabase
import com.example.foodiary.data.repository.FoodRepositoryImpl
import com.example.foodiary.data.repository.MealRepositoryImpl
import com.example.foodiary.data.repository.UserRepositoryImpl
import com.example.foodiary.domain.model.DailyNutrition
import com.example.foodiary.domain.model.NutritionTargets
import com.example.foodiary.domain.usecase.CalculateNutritionTargetsUseCase
import com.example.foodiary.domain.usecase.GetDailyNutritionUseCase
import com.example.foodiary.presentation.activity.MainActivity
import com.example.foodiary.presentation.util.popBackStackSafely
import com.example.foodiary.presentation.util.setDebouncedClickListener
import com.example.foodiary.presentation.util.EffectiveNutritionTargetsResolver
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

class NutritionAnalyticsFragment : Fragment(R.layout.fragment_nutrition_analytics) {

    private val database by lazy { AppDatabase.getInstance(requireContext()) }
    private val foodRepository by lazy { FoodRepositoryImpl(database.foodDao()) }
    private val mealRepository by lazy {
        MealRepositoryImpl(
            mealDao = database.mealDao(),
            foodRepository = foodRepository
        )
    }
    private val userRepository by lazy {
        UserRepositoryImpl(
            userDao = database.userDao(),
            allergenDao = database.allergenDao(),
            userRestrictionDao = database.userRestrictionDao()
        )
    }
    private val getDailyNutritionUseCase by lazy {
        GetDailyNutritionUseCase(
            mealRepository = mealRepository,
            foodRepository = foodRepository
        )
    }
    private val calculateNutritionTargets = CalculateNutritionTargetsUseCase()
    private val effectiveTargetsResolver by lazy { EffectiveNutritionTargetsResolver(requireContext()) }
    private val dayFormat = SimpleDateFormat("d MMM", Locale("ru"))
    private val monthTitleFormat = SimpleDateFormat("LLLL yyyy", Locale("ru"))
    private val monthShortFormat = SimpleDateFormat("LLL", Locale("ru"))

    private var selectedPeriod = AnalyticsPeriod.WEEK
    private var currentTargets: NutritionTargets? = null
    private var weekDays: List<DayNutritionSummary> = emptyList()
    private var currentMonthDays: List<DayNutritionSummary> = emptyList()
    private var currentYearMonths: List<MonthNutritionSummary> = emptyList()
    private var analyticsProgressJob: Job? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.buttonBack).apply {
            visibility = if (parentFragmentManager.backStackEntryCount > 0) View.VISIBLE else View.GONE
            setDebouncedClickListener {
            if (parentFragmentManager.backStackEntryCount > 0) {
                popBackStackSafely()
            } else {
                (activity as? MainActivity)?.navigateToRoot(MainActivity.MainTab.DIARY)
            }
            }
        }
        view.findViewById<View>(R.id.buttonActiveDayHint).setDebouncedClickListener {
            Toast.makeText(
                requireContext(),
                "Активный день - день, где был добавлен хотя бы один продукт.",
                Toast.LENGTH_LONG
            ).show()
        }

        setupPeriodFilters(view)
        loadAnalytics(view)
    }

    override fun onDestroyView() {
        analyticsProgressJob?.cancel()
        analyticsProgressJob = null
        super.onDestroyView()
    }

    private fun setupPeriodFilters(root: View) {
        mapOf(
            AnalyticsPeriod.WEEK to R.id.chipPeriodWeek,
            AnalyticsPeriod.MONTH to R.id.chipPeriodMonth,
            AnalyticsPeriod.YEAR to R.id.chipPeriodYear
        ).forEach { (period, viewId) ->
            root.findViewById<TextView>(viewId).setDebouncedClickListener {
                selectedPeriod = period
                bindSelectedPeriod(root)
            }
        }
        updatePeriodChips(root)
    }

    private fun loadAnalytics(root: View) {
        val progress = root.findViewById<ProgressBar>(R.id.progressAnalytics)
        val error = root.findViewById<TextView>(R.id.textAnalyticsError)

        viewLifecycleOwner.lifecycleScope.launch {
            analyticsProgressJob?.cancel()
            progress.visibility = View.GONE
            analyticsProgressJob = launch {
                delay(LOAD_INDICATOR_DELAY_MS)
                if (view != null) {
                    progress.visibility = View.VISIBLE
                }
            }
            error.visibility = View.GONE

            try {
                val user = userRepository.getCurrentUser()
                currentTargets = user?.let { effectiveTargetsResolver.resolve(it) }
                weekDays = loadLastDays(days = 7)
                currentMonthDays = loadCurrentMonthDays()
                currentYearMonths = loadCurrentYearMonths()

                bindSelectedPeriod(root)
            } catch (e: Exception) {
                error.visibility = View.VISIBLE
                error.text = e.message ?: "Не удалось загрузить историю питания"
            } finally {
                analyticsProgressJob?.cancel()
                analyticsProgressJob = null
                if (view != null) {
                    progress.visibility = View.GONE
                }
            }
        }
    }

    private suspend fun loadLastDays(days: Int): List<DayNutritionSummary> {
        val todayStart = startOfDay(Calendar.getInstance()).timeInMillis

        return (0 until days).map { offset ->
            val startCalendar = Calendar.getInstance().apply {
                timeInMillis = todayStart
                add(Calendar.DAY_OF_YEAR, -offset)
            }
            loadDaySummary(
                startCalendar = startCalendar,
                label = buildRelativeDayLabel(offset, startCalendar)
            )
        }
    }

    private suspend fun loadCurrentMonthDays(): List<DayNutritionSummary> {
        val calendar = Calendar.getInstance()
        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val firstDay = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
        }

        return (0 until daysInMonth).map { offset ->
            val day = firstDay.clone() as Calendar
            day.add(Calendar.DAY_OF_MONTH, offset)
            loadDaySummary(
                startCalendar = day,
                label = day.get(Calendar.DAY_OF_MONTH).toString()
            )
        }
    }

    private suspend fun loadDaySummary(
        startCalendar: Calendar,
        label: String
    ): DayNutritionSummary {
        val start = startOfDay(startCalendar)
        val end = start.clone() as Calendar
        end.add(Calendar.DAY_OF_YEAR, 1)

        val nutrition = getDailyNutritionUseCase(
            startOfDay = start.timeInMillis,
            endOfDay = end.timeInMillis
        )

        return DayNutritionSummary(
            label = label,
            dayOfMonth = start.get(Calendar.DAY_OF_MONTH),
            startMillis = start.timeInMillis,
            nutrition = nutrition,
            hasMeals = nutrition.mealsCount > 0
        )
    }

    private suspend fun loadCurrentYearMonths(): List<MonthNutritionSummary> {
        val now = Calendar.getInstance()
        val currentYear = now.get(Calendar.YEAR)
        val currentMonth = now.get(Calendar.MONTH)

        return (Calendar.JANUARY..Calendar.DECEMBER).map { monthIndex ->
            val start = Calendar.getInstance().apply {
                set(Calendar.YEAR, currentYear)
                set(Calendar.MONTH, monthIndex)
                set(Calendar.DAY_OF_MONTH, 1)
            }.let { startOfDay(it) }
            val end = start.clone() as Calendar
            end.add(Calendar.MONTH, 1)

            val nutrition = getDailyNutritionUseCase(
                startOfDay = start.timeInMillis,
                endOfDay = end.timeInMillis
            )
            val activeDays = countActiveDays(start.timeInMillis, end.timeInMillis)

            MonthNutritionSummary(
                label = capitalizeFirst(monthShortFormat.format(start.time)),
                fullLabel = capitalizeFirst(monthTitleFormat.format(start.time)),
                isCurrentMonth = monthIndex == currentMonth,
                stats = buildStatsFromTotals(
                    totalDays = start.getActualMaximum(Calendar.DAY_OF_MONTH),
                    trackedDays = activeDays,
                    nutrition = nutrition
                )
            )
        }
    }

    private suspend fun countActiveDays(startMillis: Long, endMillis: Long): Int {
        return mealRepository.getMealsForPeriod(startMillis, endMillis)
            .map { meal ->
                startOfDay(
                    Calendar.getInstance().apply {
                        timeInMillis = meal.timestamp
                    }
                ).timeInMillis
            }
            .toSet()
            .size
    }

    private fun bindSelectedPeriod(root: View) {
        updatePeriodChips(root)

        when (selectedPeriod) {
            AnalyticsPeriod.WEEK -> {
                val stats = buildStats(weekDays)
                bindSummary(
                    root = root,
                    label = "7 дней",
                    stats = stats,
                    detailsTitle = "Последние 7 дней",
                    detailsSubtitle = "Краткая динамика по дням"
                )
                renderDayList(root, weekDays)
            }

            AnalyticsPeriod.MONTH -> {
                val stats = buildStats(currentMonthDays)
                bindSummary(
                    root = root,
                    label = capitalizeFirst(monthTitleFormat.format(Calendar.getInstance().time)),
                    stats = stats,
                    detailsTitle = capitalizeFirst(monthTitleFormat.format(Calendar.getInstance().time)),
                    detailsSubtitle = "Календарь питания"
                )
                renderMonthCalendar(root, currentMonthDays)
            }

            AnalyticsPeriod.YEAR -> {
                val stats = buildStatsFromMonths(currentYearMonths)
                bindSummary(
                    root = root,
                    label = Calendar.getInstance().get(Calendar.YEAR).toString(),
                    stats = stats,
                    detailsTitle = "Год по месяцам",
                    detailsSubtitle = "Средний день и активность за каждый месяц"
                )
                renderYearOverview(root, currentYearMonths)
            }
        }
    }

    private fun updatePeriodChips(root: View) {
        mapOf(
            AnalyticsPeriod.WEEK to R.id.chipPeriodWeek,
            AnalyticsPeriod.MONTH to R.id.chipPeriodMonth,
            AnalyticsPeriod.YEAR to R.id.chipPeriodYear
        ).forEach { (period, viewId) ->
            val chip = root.findViewById<TextView>(viewId)
            val selected = period == selectedPeriod
            chip.setBackgroundResource(
                if (selected) R.drawable.bg_product_config_portion_chip_selected
                else R.drawable.bg_product_config_portion_chip
            )
            chip.setTextColor(Color.parseColor(if (selected) "#2F2433" else "#6B5B73"))
        }
    }

    private fun bindSummary(
        root: View,
        label: String,
        stats: PeriodStats,
        detailsTitle: String,
        detailsSubtitle: String
    ) {
        val macroProtein = root.findViewById<TextView>(R.id.textMacroProteinValue)
        val macroFat = root.findViewById<TextView>(R.id.textMacroFatValue)
        val macroCarbs = root.findViewById<TextView>(R.id.textMacroCarbsValue)

        root.findViewById<TextView>(R.id.textSelectedPeriodLabel).text = label
        root.findViewById<TextView>(R.id.textSelectedAverageCalories).text =
            if (stats.trackedDays > 0) formatKcal(stats.averageCalories) else "Пока пусто"
        root.findViewById<TextView>(R.id.textSelectedTrackedDays).text =
            if (stats.trackedDays > 0) "${stats.trackedDays} активных дней" else "Нет записей"
        root.findViewById<TextView>(R.id.textSelectedAverageMacros).text =
            if (stats.trackedDays > 0) {
                "Б ${formatGrams(stats.averageProtein)}, Ж ${formatGrams(stats.averageFat)}, У ${formatGrams(stats.averageCarbs)}"
            } else {
                "БЖУ появятся после первых записей"
            }
        if (stats.trackedDays > 0) {
            macroProtein.text = formatGrams(stats.averageProtein)
            macroFat.text = formatGrams(stats.averageFat)
            macroCarbs.text = formatGrams(stats.averageCarbs)
        } else {
            macroProtein.text = "-"
            macroFat.text = "-"
            macroCarbs.text = "-"
        }

        bindTargetSummary(root, stats, currentTargets)
        root.findViewById<TextView>(R.id.textPeriodDetailsTitle).text = detailsTitle
        root.findViewById<TextView>(R.id.textPeriodDetailsSubtitle).text = detailsSubtitle
    }

    private fun bindTargetSummary(
        root: View,
        stats: PeriodStats,
        targets: NutritionTargets?
    ) {
        val caloriePercent = root.findViewById<TextView>(R.id.textTargetCaloriesPercent)
        val proteinPercent = root.findViewById<TextView>(R.id.textTargetProteinPercent)
        val fatPercent = root.findViewById<TextView>(R.id.textTargetFatPercent)
        val carbsPercent = root.findViewById<TextView>(R.id.textTargetCarbsPercent)

        if (stats.trackedDays == 0 || targets == null) {
            caloriePercent.text = "-"
            proteinPercent.text = "-"
            fatPercent.text = "-"
            carbsPercent.text = "-"
            return
        }

        caloriePercent.text = "${buildPercent(stats.averageCalories, targets.targetCalories)}%"
        proteinPercent.text = "${buildPercent(stats.averageProtein, targets.proteinGrams)}%"
        fatPercent.text = "${buildPercent(stats.averageFat, targets.fatGrams)}%"
        carbsPercent.text = "${buildPercent(stats.averageCarbs, targets.carbsGrams)}%"
    }

    private fun renderDayList(
        root: View,
        summaries: List<DayNutritionSummary>
    ) {
        val container = root.findViewById<LinearLayout>(R.id.layoutPeriodDetails)
        val empty = root.findViewById<TextView>(R.id.textAnalyticsEmpty)
        container.removeAllViews()

        if (summaries.none { it.hasMeals }) {
            empty.visibility = View.VISIBLE
            return
        }

        empty.visibility = View.GONE
        summaries.forEach { summary ->
            container.addView(createDayRow(summary))
        }
    }

    private fun renderMonthCalendar(
        root: View,
        summaries: List<DayNutritionSummary>
    ) {
        val container = root.findViewById<LinearLayout>(R.id.layoutPeriodDetails)
        val empty = root.findViewById<TextView>(R.id.textAnalyticsEmpty)
        container.removeAllViews()

        if (summaries.isEmpty()) {
            empty.visibility = View.VISIBLE
            return
        }

        empty.visibility = View.GONE
        container.addView(createWeekdayHeader())

        val firstDay = Calendar.getInstance().apply {
            timeInMillis = summaries.first().startMillis
        }
        val firstDayOffset = calculateCalendarOffset(
            dayOfWeek = firstDay.get(Calendar.DAY_OF_WEEK),
            firstDayOfWeek = firstDay.firstDayOfWeek
        )
        val totalCells = roundCalendarCells(firstDayOffset + summaries.size)

        for (rowStart in 0 until totalCells step DAYS_PER_WEEK) {
            val row = createCalendarRow()
            for (column in 0 until DAYS_PER_WEEK) {
                val cellIndex = rowStart + column
                val dayIndex = cellIndex - firstDayOffset
                val cell = if (dayIndex in summaries.indices) {
                    createCalendarDayCell(summaries[dayIndex])
                } else {
                    createCalendarEmptyCell()
                }
                row.addView(cell)
            }
            container.addView(row)
        }
    }

    private fun renderYearOverview(
        root: View,
        months: List<MonthNutritionSummary>
    ) {
        val container = root.findViewById<LinearLayout>(R.id.layoutPeriodDetails)
        val empty = root.findViewById<TextView>(R.id.textAnalyticsEmpty)
        container.removeAllViews()

        if (months.none { it.stats.trackedDays > 0 }) {
            empty.visibility = View.VISIBLE
            return
        }

        empty.visibility = View.GONE
        months.chunked(2).forEach { rowMonths ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            rowMonths.forEach { month ->
                row.addView(createMonthCard(month))
            }

            if (rowMonths.size == 1) {
                row.addView(createWeightedSpacer())
            }

            container.addView(row)
        }
    }

    private fun createDayRow(summary: DayNutritionSummary): View {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.bg_addmeal_card)
            setPadding(16.dp(), 14.dp(), 16.dp(), 14.dp())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 10.dp()
            }
        }

        val left = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        left.addView(createText(summary.label, sizeSp = 16f, color = "#2F2433", bold = true))
        left.addView(
            createText(
                buildMealCountLabel(summary.nutrition.mealsCount),
                sizeSp = 13f,
                color = "#6B5B73",
                topMarginDp = 5
            )
        )
        left.addView(
            createText(
                "Б ${formatGrams(summary.nutrition.totalProtein)}, Ж ${formatGrams(summary.nutrition.totalFat)}, У ${formatGrams(summary.nutrition.totalCarbs)}",
                sizeSp = 12f,
                color = "#6B5B73",
                topMarginDp = 8
            )
        )

        val right = LinearLayout(requireContext()).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.75f)
        }
        right.addView(
            createText(
                formatKcal(summary.nutrition.totalCalories),
                sizeSp = 18f,
                color = "#2F2433",
                bold = true
            )
        )
        right.addView(
            createText(
                currentTargets?.let { "${buildPercent(summary.nutrition.totalCalories, it.targetCalories)}% цели" }
                    ?: "без цели",
                sizeSp = 12f,
                color = "#7D63BF",
                topMarginDp = 6
            )
        )

        row.addView(left)
        row.addView(right)
        return row
    }

    private fun createWeekdayHeader(): View {
        val row = createCalendarRow().apply {
            setPadding(0, 0, 0, 4.dp())
        }
        val firstDay = Calendar.getInstance().firstDayOfWeek
        repeat(DAYS_PER_WEEK) { index ->
            val day = ((firstDay - 1 + index) % DAYS_PER_WEEK) + 1
            row.addView(
                createText(
                    text = buildWeekdayLabel(day),
                    sizeSp = 12f,
                    color = "#6B5B73",
                    bold = true
                ).apply {
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
            )
        }
        return row
    }

    private fun createCalendarRow(): LinearLayout {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun createCalendarDayCell(summary: DayNutritionSummary): View {
        val cell = LinearLayout(requireContext()).apply {
            gravity = Gravity.CENTER
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(
                if (summary.hasMeals) R.drawable.bg_macro_card_yellow
                else R.drawable.bg_addmeal_card
            )
            setPadding(4.dp(), 8.dp(), 4.dp(), 8.dp())
            layoutParams = LinearLayout.LayoutParams(0, 70.dp(), 1f).apply {
                setMargins(3.dp(), 3.dp(), 3.dp(), 3.dp())
            }
        }

        cell.addView(
            createText(
                text = summary.dayOfMonth.toString(),
                sizeSp = 14f,
                color = "#2F2433",
                bold = true
            ).apply {
                gravity = Gravity.CENTER
            }
        )

        cell.addView(
            createText(
                text = if (summary.hasMeals) summary.nutrition.totalCalories.roundToInt().toString() else "",
                sizeSp = 11f,
                color = "#6B5B73",
                topMarginDp = 4
            ).apply {
                gravity = Gravity.CENTER
            }
        )

        return cell
    }

    private fun createCalendarEmptyCell(): View {
        return View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(0, 70.dp(), 1f).apply {
                setMargins(3.dp(), 3.dp(), 3.dp(), 3.dp())
            }
        }
    }

    private fun createMonthCard(month: MonthNutritionSummary): View {
        val card = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(
                if (month.isCurrentMonth) R.drawable.bg_macro_card_yellow
                else R.drawable.bg_addmeal_card
            )
            setPadding(14.dp(), 14.dp(), 14.dp(), 14.dp())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(0, 0, 8.dp(), 10.dp())
            }
        }

        card.addView(createText(month.label, sizeSp = 16f, color = "#2F2433", bold = true))
        card.addView(
            createText(
                text = if (month.stats.trackedDays > 0) formatKcal(month.stats.averageCalories) else "пусто",
                sizeSp = 18f,
                color = "#2F2433",
                bold = true,
                topMarginDp = 8
            )
        )
        card.addView(
            createText(
                text = if (month.stats.trackedDays > 0) {
                    "${month.stats.trackedDays} активных дней"
                } else {
                    "нет записей"
                },
                sizeSp = 12f,
                color = "#6B5B73",
                topMarginDp = 5
            )
        )
        return card
    }

    private fun createWeightedSpacer(): View {
        return View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1.dp(), 1f)
        }
    }

    private fun createText(
        text: String,
        sizeSp: Float,
        color: String,
        bold: Boolean = false,
        topMarginDp: Int = 0
    ): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            textSize = sizeSp
            setTextColor(Color.parseColor(color))
            if (bold) setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = topMarginDp.dp()
            }
        }
    }

    private fun buildStats(days: List<DayNutritionSummary>): PeriodStats {
        val tracked = days.filter { it.hasMeals }
        val totalCalories = tracked.sumOf { it.nutrition.totalCalories }
        val totalProtein = tracked.sumOf { it.nutrition.totalProtein }
        val totalFat = tracked.sumOf { it.nutrition.totalFat }
        val totalCarbs = tracked.sumOf { it.nutrition.totalCarbs }

        return buildStatsFromTotals(
            totalDays = days.size,
            trackedDays = tracked.size,
            totalCalories = totalCalories,
            totalProtein = totalProtein,
            totalFat = totalFat,
            totalCarbs = totalCarbs
        )
    }

    private fun buildStatsFromTotals(
        totalDays: Int,
        trackedDays: Int,
        nutrition: DailyNutrition
    ): PeriodStats {
        return buildStatsFromTotals(
            totalDays = totalDays,
            trackedDays = trackedDays,
            totalCalories = nutrition.totalCalories,
            totalProtein = nutrition.totalProtein,
            totalFat = nutrition.totalFat,
            totalCarbs = nutrition.totalCarbs
        )
    }

    private fun buildStatsFromTotals(
        totalDays: Int,
        trackedDays: Int,
        totalCalories: Double,
        totalProtein: Double,
        totalFat: Double,
        totalCarbs: Double
    ): PeriodStats {
        val safeTrackedDays = trackedDays.coerceAtLeast(0)
        return PeriodStats(
            totalDays = totalDays,
            trackedDays = safeTrackedDays,
            totalCalories = totalCalories,
            totalProtein = totalProtein,
            totalFat = totalFat,
            totalCarbs = totalCarbs,
            averageCalories = averageByTrackedDays(totalCalories, safeTrackedDays),
            averageProtein = averageByTrackedDays(totalProtein, safeTrackedDays),
            averageFat = averageByTrackedDays(totalFat, safeTrackedDays),
            averageCarbs = averageByTrackedDays(totalCarbs, safeTrackedDays)
        )
    }

    private fun buildStatsFromMonths(months: List<MonthNutritionSummary>): PeriodStats {
        return buildStatsFromTotals(
            totalDays = months.sumOf { it.stats.totalDays },
            trackedDays = months.sumOf { it.stats.trackedDays },
            totalCalories = months.sumOf { it.stats.totalCalories },
            totalProtein = months.sumOf { it.stats.totalProtein },
            totalFat = months.sumOf { it.stats.totalFat },
            totalCarbs = months.sumOf { it.stats.totalCarbs }
        )
    }

    private fun startOfDay(calendar: Calendar): Calendar {
        return (calendar.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    private fun buildRelativeDayLabel(offset: Int, calendar: Calendar): String {
        return when (offset) {
            0 -> "Сегодня"
            1 -> "Вчера"
            else -> dayFormat.format(calendar.time)
        }
    }

    private fun buildWeekdayLabel(dayOfWeek: Int): String {
        return when (dayOfWeek) {
            Calendar.MONDAY -> "Пн"
            Calendar.TUESDAY -> "Вт"
            Calendar.WEDNESDAY -> "Ср"
            Calendar.THURSDAY -> "Чт"
            Calendar.FRIDAY -> "Пт"
            Calendar.SATURDAY -> "Сб"
            else -> "Вс"
        }
    }

    private fun buildMealCountLabel(count: Int): String {
        return when {
            count % 10 == 1 && count % 100 != 11 -> "$count продукт"
            count % 10 in 2..4 && count % 100 !in 12..14 -> "$count продукта"
            else -> "$count продуктов"
        }
    }

    private fun calculateCalendarOffset(
        dayOfWeek: Int,
        firstDayOfWeek: Int
    ): Int {
        val rawOffset = dayOfWeek - firstDayOfWeek
        return if (rawOffset >= 0) rawOffset else rawOffset + DAYS_PER_WEEK
    }

    private fun roundCalendarCells(usedCells: Int): Int {
        return ((usedCells + DAYS_PER_WEEK - 1) / DAYS_PER_WEEK) * DAYS_PER_WEEK
    }

    private fun averageByTrackedDays(value: Double, trackedDays: Int): Double {
        return if (trackedDays > 0) value / trackedDays else 0.0
    }

    private fun buildPercent(value: Double, target: Int): Int {
        if (target <= 0) return 0
        return ((value / target) * 100.0).roundToInt()
    }

    private fun capitalizeFirst(value: String): String {
        if (value.isBlank()) return value
        return value.substring(0, 1).uppercase(Locale("ru")) + value.substring(1)
    }

    private fun formatKcal(value: Double): String = "${value.roundToInt()} ккал"

    private fun formatGrams(value: Double): String = "${value.roundToInt()} г"

    private fun Int.dp(): Int {
        return (this * resources.displayMetrics.density).roundToInt()
    }

    private enum class AnalyticsPeriod {
        WEEK,
        MONTH,
        YEAR
    }

    private data class DayNutritionSummary(
        val label: String,
        val dayOfMonth: Int,
        val startMillis: Long,
        val nutrition: DailyNutrition,
        val hasMeals: Boolean
    )

    private data class MonthNutritionSummary(
        val label: String,
        val fullLabel: String,
        val isCurrentMonth: Boolean,
        val stats: PeriodStats
    )

    private data class PeriodStats(
        val totalDays: Int,
        val trackedDays: Int,
        val totalCalories: Double,
        val totalProtein: Double,
        val totalFat: Double,
        val totalCarbs: Double,
        val averageCalories: Double,
        val averageProtein: Double,
        val averageFat: Double,
        val averageCarbs: Double
    )

    private companion object {
        const val DAYS_PER_WEEK = 7
        const val LOAD_INDICATOR_DELAY_MS = 250L
    }
}
