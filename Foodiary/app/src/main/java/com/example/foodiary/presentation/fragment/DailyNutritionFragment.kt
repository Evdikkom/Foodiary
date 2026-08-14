package com.example.foodiary.presentation.fragment

import android.Manifest
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.Dialog
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.Window
import android.view.WindowManager
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.example.foodiary.R
import com.example.foodiary.data.local.database.AppDatabase
import com.example.foodiary.data.local.preferences.MealSchedulePreferences
import com.example.foodiary.data.local.preferences.UiPreferences
import com.example.foodiary.data.model.UserGoal
import com.example.foodiary.data.repository.AllergenRepositoryImpl
import com.example.foodiary.data.repository.FavoriteFoodsRepositoryImpl
import com.example.foodiary.data.repository.FoodRepositoryImpl
import com.example.foodiary.data.repository.MealRepositoryImpl
import com.example.foodiary.data.repository.UserRepositoryImpl
import com.example.foodiary.data.repository.WeatherRepositoryImpl
import com.example.foodiary.domain.model.DailyNutrition
import com.example.foodiary.domain.model.Food
import com.example.foodiary.domain.model.FoodRecommendation
import com.example.foodiary.domain.model.Meal
import com.example.foodiary.domain.model.MealType
import com.example.foodiary.domain.model.NutritionTargets
import com.example.foodiary.domain.model.SmartCoachFocus
import com.example.foodiary.domain.model.SmartCoachInsight
import com.example.foodiary.domain.model.SmartCoachMealPlan
import com.example.foodiary.domain.model.SmartCoachMealPlanItem
import com.example.foodiary.domain.model.SmartCoachMealPlanOption
import com.example.foodiary.domain.model.SmartCoachMealPlanSection
import com.example.foodiary.domain.model.SmartCoachScoreDetail
import com.example.foodiary.domain.model.SmartCoachScoreSection
import com.example.foodiary.domain.model.User
import com.example.foodiary.domain.model.WeatherFoodRecommendation
import com.example.foodiary.domain.model.WeatherSnapshot
import com.example.foodiary.domain.model.WeatherRecommendationAction
import com.example.foodiary.domain.repository.UserRepository
import com.example.foodiary.domain.usecase.AddMealUseCase
import com.example.foodiary.domain.usecase.DeleteMealUseCase
import com.example.foodiary.domain.usecase.GetDailyNutritionUseCase
import com.example.foodiary.domain.usecase.GetMealsForPeriodUseCase
import com.example.foodiary.domain.usecase.GetPersonalizedFoodRecommendationsUseCase
import com.example.foodiary.domain.usecase.GetSmartCoachInsightUseCase
import com.example.foodiary.domain.usecase.GetWeatherFoodRecommendationUseCase
import com.example.foodiary.presentation.activity.MainActivity
import com.example.foodiary.presentation.dialog.DailyScreenMenuDialogFragment
import com.example.foodiary.presentation.dialog.MealTypePickerDialogFragment
import com.example.foodiary.presentation.location.DeviceLocationProvider
import com.example.foodiary.presentation.util.EffectiveNutritionTargetsResolver
import com.example.foodiary.presentation.util.replaceFragmentSafely
import com.example.foodiary.presentation.util.setDebouncedClickListener
import com.example.foodiary.presentation.view.CalorieRingView
import com.example.foodiary.presentation.viewmodel.GetDailyNutritionViewModel
import com.example.foodiary.presentation.viewmodel.GetDailyNutritionViewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

class DailyNutritionFragment : Fragment(R.layout.fragment_daily_nutrition) {

    companion object {
        private const val WEATHER_RECOMMENDATION_DELAY_MS = 12_000L
        private const val STATE_SELECTED_DAY_START = "state_selected_day_start"
        private const val ARG_SELECTED_DAY_START = "arg_selected_day_start"
        private var isWeatherRecommendationConsumedThisLaunch = false

        fun newInstance(selectedDayStart: Long = System.currentTimeMillis()): DailyNutritionFragment {
            return DailyNutritionFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_SELECTED_DAY_START, selectedDayStart)
                }
            }
        }

        internal fun normalizeDayStart(timestamp: Long): Long {
            return Calendar.getInstance().apply {
                timeInMillis = timestamp
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }

        internal fun resolveInitialSelectedDayStart(
            savedDay: Long?,
            argDay: Long?,
            nowMillis: Long = System.currentTimeMillis()
        ): Long {
            return normalizeDayStart(savedDay ?: argDay ?: nowMillis)
        }

        const val REQUEST_MEALS_CHANGED = "daily_nutrition_meals_changed"
    }

    private val viewModel: GetDailyNutritionViewModel by viewModels {
        provideFactory()
    }

    private val weatherPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        val root = view ?: return@registerForActivityResult

        if (granted) {
            isWeatherRecommendationConsumedThisLaunch = false
            viewLifecycleOwner.lifecycleScope.launch {
                loadWeatherRecommendation(root, forceShow = true)
            }
        } else {
            bindWeatherRecommendation(
                root = root,
                recommendation = WeatherFoodRecommendation.permissionDenied(),
                forceShow = true
            )
        }
    }

    private data class DailyLimits(
        val calories: Int = 3500,
        val protein: Int = 200,
        val fat: Int = 120,
        val carbs: Int = 440,
        val goal: UserGoal = UserGoal.MAINTAIN_WEIGHT
    )

    private enum class RecommendationCardSlot {
        BALANCE,
        GOAL,
        HABIT
    }

    private enum class MomentRecommendationKind {
        BALANCE,
        GOAL,
        HABIT,
        FAVORITE,
        RECIPE,
        LIGHT
    }

    private enum class RecommendationFoodRole {
        PROTEIN,
        CARBS,
        LIGHT,
        SNACK,
        DENSE,
        OTHER
    }

    private data class RecommendationCardUi(
        val slot: RecommendationCardSlot,
        val description: String,
        val recommendation: FoodRecommendation
    )

    private data class MomentRecommendationUi(
        val kind: MomentRecommendationKind,
        val title: String,
        val badge: String,
        val description: String,
        val recommendation: FoodRecommendation,
        val mealType: MealType,
        val accentColor: String,
        val backgroundColor: String,
        val borderColor: String,
        val tagBackgroundColor: String
    )

    private data class SmartCoachMealKey(
        val mealType: MealType,
        val foodId: String
    )

    private data class SmartCoachPlanProgress(
        val consumedGrams: Int
    )

    private data class SmartCoachPlanSession(
        val dayStart: Long,
        val plan: SmartCoachMealPlan,
        val baselineQuantities: Map<SmartCoachMealKey, Double>,
        val planKeys: Set<SmartCoachMealKey>
    )

    private data class SmartCoachOptionNutrition(
        val calories: Int,
        val protein: Int,
        val fat: Int,
        val carbs: Int
    )

    private var dailyLimits = DailyLimits()
    private var mealGoals = buildMealGoals(dailyLimits.calories)
    private lateinit var userRepository: UserRepository
    private lateinit var foodRepository: FoodRepositoryImpl
    private lateinit var mealRepository: MealRepositoryImpl
    private lateinit var getMealsForPeriodUseCase: GetMealsForPeriodUseCase
    private lateinit var addMealUseCase: AddMealUseCase
    private lateinit var deleteMealUseCase: DeleteMealUseCase
    private lateinit var personalizedRecommendationsUseCase: GetPersonalizedFoodRecommendationsUseCase
    private lateinit var smartCoachInsightUseCase: GetSmartCoachInsightUseCase
    private lateinit var weatherRecommendationUseCase: GetWeatherFoodRecommendationUseCase
    private lateinit var weatherRepository: WeatherRepositoryImpl
    private lateinit var weatherLocationProvider: DeviceLocationProvider
    private lateinit var uiPreferences: UiPreferences
    private lateinit var mealSchedulePreferences: MealSchedulePreferences
    private lateinit var nutritionTargetsResolver: EffectiveNutritionTargetsResolver
    private var weatherRecommendationShowJob: Job? = null
    private var weatherRecommendationRevealAtMs: Long = 0L
    private var currentWeatherRecommendation: WeatherFoodRecommendation? = null
    private var currentRecommendationCards: Map<RecommendationCardSlot, FoodRecommendation> = emptyMap()
    private var currentMomentRecommendation: MomentRecommendationUi? = null
    private var selectedMomentRecommendationKind: MomentRecommendationKind? = null
    private var currentSmartCoachInsight: SmartCoachInsight? = null
    private var latestDailyNutrition: DailyNutrition? = null
    private var latestRecommendations: List<FoodRecommendation> = emptyList()
    private var smartCoachPlanSession: SmartCoachPlanSession? = null
    private var currentSmartCoachMealPlanProgress: Map<SmartCoachMealKey, SmartCoachPlanProgress> = emptyMap()
    private val selectedSmartCoachOptionIds = mutableMapOf<MealType, String>()
    private var smartCoachJob: Job? = null
    private var selectedDayStart: Long = 0L
    private var hasConfiguredProfile: Boolean = false

    private fun persistSelectedDayState() {
        val safeArgs = arguments ?: Bundle().also { arguments = it }
        safeArgs.putLong(ARG_SELECTED_DAY_START, selectedDayStart)
        (activity as? MainActivity)?.updateDiarySelectedDay(selectedDayStart)
    }

    private fun getSelectedDayBounds(): Pair<Long, Long> {
        val end = Calendar.getInstance().apply {
            timeInMillis = selectedDayStart
            add(Calendar.DAY_OF_MONTH, 1)
        }.timeInMillis
        return selectedDayStart to end
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val savedDay = savedInstanceState
            ?.takeIf { it.containsKey(STATE_SELECTED_DAY_START) }
            ?.getLong(STATE_SELECTED_DAY_START)
        val argDay = arguments
            ?.takeIf { it.containsKey(ARG_SELECTED_DAY_START) }
            ?.getLong(ARG_SELECTED_DAY_START)
        selectedDayStart = resolveInitialSelectedDayStart(
            savedDay = savedDay,
            argDay = argDay
        )
        persistSelectedDayState()

        val database = AppDatabase.getInstance(requireContext())
        userRepository = UserRepositoryImpl(
            userDao = database.userDao(),
            allergenDao = database.allergenDao(),
            userRestrictionDao = database.userRestrictionDao()
        )
        foodRepository = FoodRepositoryImpl(foodDao = database.foodDao())
        mealRepository = MealRepositoryImpl(
            mealDao = database.mealDao(),
            foodRepository = foodRepository
        )
        uiPreferences = UiPreferences(requireContext())
        mealSchedulePreferences = MealSchedulePreferences(requireContext())
        nutritionTargetsResolver = EffectiveNutritionTargetsResolver(requireContext())
        getMealsForPeriodUseCase = GetMealsForPeriodUseCase(mealRepository)
        addMealUseCase = AddMealUseCase(mealRepository)
        deleteMealUseCase = DeleteMealUseCase(mealRepository)
        val allergenRepository = AllergenRepositoryImpl(
            allergenDao = database.allergenDao(),
            foodAllergenDao = database.foodAllergenDao(),
            userRestrictionDao = database.userRestrictionDao()
        )
        personalizedRecommendationsUseCase = GetPersonalizedFoodRecommendationsUseCase(
            foodRepository = foodRepository,
            mealRepository = mealRepository,
            userRepository = userRepository,
            favoriteFoodsRepository = FavoriteFoodsRepositoryImpl(requireContext()),
            allergenRepository = allergenRepository,
            nutritionTargetsResolver = { user -> nutritionTargetsResolver.resolve(user) },
            getDailyNutritionUseCase = GetDailyNutritionUseCase(
                mealRepository = mealRepository,
                foodRepository = foodRepository
            )
        )
        smartCoachInsightUseCase = GetSmartCoachInsightUseCase(
            foodRepository = foodRepository,
            mealRepository = mealRepository,
            userRepository = userRepository,
            nutritionTargetsResolver = { user -> nutritionTargetsResolver.resolve(user) }
        )
        weatherRepository = WeatherRepositoryImpl()
        weatherLocationProvider = DeviceLocationProvider(requireContext())
        weatherRecommendationUseCase = GetWeatherFoodRecommendationUseCase(
            foodRepository = foodRepository,
            allergenRepository = allergenRepository
        )

        setupHeader(view)
        setupActions(view)
        childFragmentManager.setFragmentResultListener(
            DailyScreenMenuDialogFragment.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, result ->
            when (result.getString(DailyScreenMenuDialogFragment.RESULT_ACTION)) {
                DailyScreenMenuDialogFragment.ACTION_COPY_DAY -> {
                    view.post { openCopyDayPicker(view) }
                }
                DailyScreenMenuDialogFragment.ACTION_WEATHER -> {
                    replaceFragmentSafely(WeatherInsightsFragment.newInstance(selectedDayStart))
                }
            }
        }
        parentFragmentManager.setFragmentResultListener(
            REQUEST_MEALS_CHANGED,
            viewLifecycleOwner
        ) { _, _ ->
            refreshProfileAndNutrition(view)
        }
        observeViewModel(view)
        bindSmartCoach(view, null)
        bindWeatherRecommendation(view, null)
        bindRecommendationSection(view, emptyList())
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(STATE_SELECTED_DAY_START, selectedDayStart)
    }

    override fun onResume() {
        super.onResume()
        weatherRecommendationRevealAtMs =
            SystemClock.elapsedRealtime() + WEATHER_RECOMMENDATION_DELAY_MS
        view?.let { refreshProfileAndNutrition(it) }
    }

    override fun onDestroyView() {
        weatherRecommendationShowJob?.cancel()
        weatherRecommendationShowJob = null
        smartCoachJob?.cancel()
        smartCoachJob = null
        super.onDestroyView()
    }

    private fun setupHeader(root: View) {
        val title = root.findViewById<TextView>(R.id.textDateTitle)
        val subtitle = root.findViewById<TextView>(R.id.textDateSubtitle)

        val selectedDayButton = root.findViewById<TextView>(R.id.buttonSelectedDay)
        val dayActionsRow = root.findViewById<LinearLayout>(R.id.layoutSelectedDayActions)
        val jumpTodayButton = root.findViewById<TextView>(R.id.buttonJumpToday)
        val nextDayButton = root.findViewById<TextView>(R.id.buttonNextDay)
        val dayFormat = SimpleDateFormat("d MMMM", Locale("ru"))
        val weekFormat = SimpleDateFormat("EEEE", Locale("ru"))
        val selectedDate = Calendar.getInstance().apply { timeInMillis = selectedDayStart }.time

        title.text = "Дневник"
        selectedDayButton.text = if (isTodaySelected()) {
            "Сегодня, ${dayFormat.format(selectedDate)}"
        } else {
            dayFormat.format(selectedDate)
        }
        subtitle.text = weekFormat.format(selectedDate)
            .replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase(Locale("ru")) else char.toString() }
        jumpTodayButton.visibility = if (isTodaySelected()) View.GONE else View.VISIBLE
        dayActionsRow.visibility = if (isTodaySelected()) View.GONE else View.VISIBLE
        nextDayButton.isEnabled = !isTodaySelected()
        nextDayButton.alpha = if (isTodaySelected()) 0.55f else 1f
    }

    private fun setupActions(root: View) {
        root.findViewById<ScrollView>(R.id.scrollDailyNutrition).setOnScrollChangeListener { _, _, scrollY, _, _ ->
            updateWeatherRecommendationScrollAlpha(root, scrollY)
        }
        root.findViewById<View>(R.id.buttonDailyMenu).setDebouncedClickListener {
            openDailyMenu()
        }
        root.findViewById<View>(R.id.buttonPrevDay).setDebouncedClickListener {
            shiftSelectedDay(root, -1)
        }
        root.findViewById<View>(R.id.buttonNextDay).setDebouncedClickListener {
            if (!isTodaySelected()) {
                shiftSelectedDay(root, 1)
            }
        }
        root.findViewById<View>(R.id.buttonSelectedDay).setDebouncedClickListener {
            openDayPicker(root)
        }
        root.findViewById<View>(R.id.buttonJumpToday).setDebouncedClickListener {
            selectedDayStart = normalizeDayStart(System.currentTimeMillis())
            persistSelectedDayState()
            refreshProfileAndNutrition(root)
        }

        root.findViewById<View>(R.id.cardQuickRecommendationPopup).setDebouncedClickListener {
            handleWeatherRecommendationAction(root)
        }
        root.findViewById<View>(R.id.buttonQuickRecommendationPopup).setDebouncedClickListener {
            handleWeatherRecommendationAction(root)
        }
        root.findViewById<View>(R.id.buttonQuickRecommendationClose).setDebouncedClickListener {
            dismissWeatherRecommendation(root, consumeForThisLaunch = true)
        }
        root.findViewById<View>(R.id.buttonSmartCoachAction).setDebouncedClickListener {
            openSmartCoachSuggestion()
        }
        root.findViewById<View>(R.id.cardSmartCoachMomentRecommendation).apply {
            isClickable = false
            isFocusable = false
            setOnClickListener(null)
        }
        root.findViewById<View>(R.id.buttonSmartCoachMomentVariants).setDebouncedClickListener {
            showMomentRecommendationOptionsDialog()
        }
        root.findViewById<View>(R.id.cardSmartCoachReplacement).setDebouncedClickListener {
            openSmartCoachSuggestion()
        }
        root.findViewById<View>(R.id.cardRecommendationBalance).setDebouncedClickListener {
            openRecommendationCard(RecommendationCardSlot.BALANCE)
        }
        root.findViewById<View>(R.id.cardRecommendationGoal).setDebouncedClickListener {
            openRecommendationCard(RecommendationCardSlot.GOAL)
        }
        root.findViewById<View>(R.id.cardRecommendationHabit).setDebouncedClickListener {
            openRecommendationCard(RecommendationCardSlot.HABIT)
        }
        root.findViewById<Button>(R.id.buttonRecommendationBalance).setDebouncedClickListener {
            openRecommendationCard(RecommendationCardSlot.BALANCE)
        }
        root.findViewById<Button>(R.id.buttonRecommendationGoal).setDebouncedClickListener {
            openRecommendationCard(RecommendationCardSlot.GOAL)
        }
        root.findViewById<Button>(R.id.buttonRecommendationHabit).setDebouncedClickListener {
            openRecommendationCard(RecommendationCardSlot.HABIT)
        }

        root.findViewById<View>(R.id.cardBreakfast).setDebouncedClickListener {
            openMealDetails(MealType.BREAKFAST)
        }
        root.findViewById<View>(R.id.cardLunch).setDebouncedClickListener {
            openMealDetails(MealType.LUNCH)
        }
        root.findViewById<View>(R.id.cardDinner).setDebouncedClickListener {
            openMealDetails(MealType.DINNER)
        }
        root.findViewById<View>(R.id.cardSnack).setDebouncedClickListener {
            openMealDetails(MealType.SNACK)
        }

        root.findViewById<Button>(R.id.buttonAddBreakfast).setDebouncedClickListener {
            openAddMeal(MealType.BREAKFAST)
        }
        root.findViewById<Button>(R.id.buttonAddLunch).setDebouncedClickListener {
            openAddMeal(MealType.LUNCH)
        }
        root.findViewById<Button>(R.id.buttonAddDinner).setDebouncedClickListener {
            openAddMeal(MealType.DINNER)
        }
        root.findViewById<Button>(R.id.buttonAddSnack).setDebouncedClickListener {
            openAddMeal(MealType.SNACK)
        }
        root.findViewById<View>(R.id.cardAfternoonSnack).setDebouncedClickListener {
            openMealDetails(MealType.AFTERNOON_SNACK)
        }
        root.findViewById<View>(R.id.cardLateDinner).setDebouncedClickListener {
            openMealDetails(MealType.LATE_DINNER)
        }
        root.findViewById<Button>(R.id.buttonAddAfternoonSnack).setDebouncedClickListener {
            openAddMeal(MealType.AFTERNOON_SNACK)
        }
        root.findViewById<Button>(R.id.buttonAddLateDinner).setDebouncedClickListener {
            openAddMeal(MealType.LATE_DINNER)
        }
    }

    private fun openAddMeal(mealType: MealType) {
        replaceFragmentSafely(AddMealFragment.newInstance(mealType, selectedDayStart))
    }

    private fun openMealDetails(mealType: MealType) {
        replaceFragmentSafely(MealDetailsFragment.newInstance(mealType, selectedDayStart))
    }

    private fun handleWeatherRecommendationAction(root: View) {
        val recommendation = currentWeatherRecommendation ?: return
        when (recommendation.action) {
            WeatherRecommendationAction.OPEN_FOOD -> {
                val food = recommendation.food ?: return
                dismissWeatherRecommendation(root, consumeForThisLaunch = true)
                openRecommendedProduct(food.id, resolveCurrentMealType())
            }

            WeatherRecommendationAction.REQUEST_LOCATION -> {
                weatherPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }

            WeatherRecommendationAction.RETRY -> {
                isWeatherRecommendationConsumedThisLaunch = false
                viewLifecycleOwner.lifecycleScope.launch {
                    loadWeatherRecommendation(root, forceShow = true)
                }
            }

            WeatherRecommendationAction.DISMISS -> {
                dismissWeatherRecommendation(root, consumeForThisLaunch = true)
            }
        }
    }

    private fun openRecommendationCard(slot: RecommendationCardSlot) {
        val recommendation = currentRecommendationCards[slot] ?: return
        openRecommendedProduct(recommendation.food.id, resolveCurrentMealType())
    }

    private fun openSmartCoachSuggestion() {
        val moment = currentMomentRecommendation
        if (moment != null) {
            openRecommendedProduct(moment.recommendation.food.id, moment.mealType)
            return
        }

        val insight = currentSmartCoachInsight ?: return
        val food = insight.replacement?.replacement ?: insight.suggestedFood ?: return
        openRecommendedProduct(food.id, insight.suggestedMealType)
    }

    private fun openRecommendedProduct(
        foodId: String,
        suggestedMealType: MealType,
        initialQuantityInGrams: Double? = null
    ) {
        if (!canShowTransientUi()) return
        if (parentFragmentManager.findFragmentByTag("recommended_product_meal_type_picker") != null) return

        MealTypePickerDialogFragment.newProductInstance(
            foodId = foodId,
            targetDayStart = selectedDayStart,
            suggestedMealType = suggestedMealType,
            initialQuantityInGrams = initialQuantityInGrams
        ).show(parentFragmentManager, "recommended_product_meal_type_picker")
    }

    private fun canShowTransientUi(): Boolean {
        if (!isAdded || view == null) return false
        if (parentFragmentManager.isStateSaved) return false
        return viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    }

    private fun openDailyMenu() {
        DailyScreenMenuDialogFragment.newInstance(
            selectedDayLabel = buildSelectedDayMenuLabel()
        ).show(childFragmentManager, "daily_screen_menu")
    }

    private fun shiftSelectedDay(root: View, dayDelta: Int) {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = selectedDayStart
            add(Calendar.DAY_OF_MONTH, dayDelta)
        }
        val candidate = normalizeDayStart(calendar.timeInMillis)
        val todayStart = normalizeDayStart(System.currentTimeMillis())
        selectedDayStart = candidate.coerceAtMost(todayStart)
        persistSelectedDayState()
        refreshProfileAndNutrition(root)
    }

    private fun openDayPicker(root: View) {
        val calendar = Calendar.getInstance().apply { timeInMillis = selectedDayStart }
        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val pickedCalendar = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                }
                selectedDayStart = normalizeDayStart(pickedCalendar.timeInMillis)
                persistSelectedDayState()
                refreshProfileAndNutrition(root)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).apply {
            datePicker.maxDate = System.currentTimeMillis()
        }.show()
    }

    private fun openCopyDayPicker(root: View) {
        val initialDate = Calendar.getInstance().apply {
            timeInMillis = (selectedDayStart - 24L * 60L * 60L * 1000L)
                .coerceAtLeast(0L)
        }
        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val sourceCalendar = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                }
                val sourceDayStart = normalizeDayStart(sourceCalendar.timeInMillis)
                prepareCopyDay(root, sourceDayStart)
            },
            initialDate.get(Calendar.YEAR),
            initialDate.get(Calendar.MONTH),
            initialDate.get(Calendar.DAY_OF_MONTH)
        ).apply {
            datePicker.maxDate = System.currentTimeMillis()
        }.show()
    }

    private fun prepareCopyDay(root: View, sourceDayStart: Long) {
        if (sourceDayStart == selectedDayStart) {
            Toast.makeText(requireContext(), "Нельзя копировать день в самого себя", Toast.LENGTH_SHORT).show()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val sourceMeals = getMealsForPeriodUseCase(sourceDayStart, endOfDay(sourceDayStart))
            if (sourceMeals.isEmpty()) {
                Toast.makeText(requireContext(), "В выбранном дне нет продуктов для копирования", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val targetMeals = getMealsForPeriodUseCase(selectedDayStart, endOfDay(selectedDayStart))
            if (targetMeals.isEmpty()) {
                confirmAndCopyDay(root, sourceMeals, replaceTarget = false)
            } else {
                AlertDialog.Builder(requireContext())
                    .setTitle("В этом дне уже есть продукты")
                    .setMessage("Можно добавить скопированный день сверху или полностью заменить текущий день.")
                    .setPositiveButton("Добавить") { _, _ ->
                        confirmAndCopyDay(root, sourceMeals, replaceTarget = false)
                    }
                    .setNeutralButton("Заменить") { _, _ ->
                        confirmAndCopyDay(root, sourceMeals, replaceTarget = true)
                    }
                    .setNegativeButton("Отмена", null)
                    .show()
            }
        }
    }

    private fun confirmAndCopyDay(
        root: View,
        sourceMeals: List<Meal>,
        replaceTarget: Boolean
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                if (replaceTarget) {
                    val existingTargetMeals = getMealsForPeriodUseCase(
                        selectedDayStart,
                        endOfDay(selectedDayStart)
                    )
                    existingTargetMeals.forEach { meal ->
                        deleteMealUseCase(meal.id)
                    }
                }

                sourceMeals
                    .sortedBy { it.timestamp }
                    .forEach { meal ->
                        addMealUseCase(
                            Meal(
                                foodId = meal.foodId,
                                quantityInGrams = meal.quantityInGrams,
                                mealType = meal.mealType,
                                timestamp = shiftTimestampToSelectedDay(meal.timestamp),
                                note = meal.note
                            )
                        )
                    }

                Toast.makeText(requireContext(), "День скопирован", Toast.LENGTH_SHORT).show()
                refreshProfileAndNutrition(root)
            } catch (_: Exception) {
                Toast.makeText(requireContext(), "Не удалось скопировать день", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun shiftTimestampToSelectedDay(sourceTimestamp: Long): Long {
        val sourceCalendar = Calendar.getInstance().apply { timeInMillis = sourceTimestamp }
        return Calendar.getInstance().apply {
            timeInMillis = selectedDayStart
            set(Calendar.HOUR_OF_DAY, sourceCalendar.get(Calendar.HOUR_OF_DAY))
            set(Calendar.MINUTE, sourceCalendar.get(Calendar.MINUTE))
            set(Calendar.SECOND, sourceCalendar.get(Calendar.SECOND))
            set(Calendar.MILLISECOND, sourceCalendar.get(Calendar.MILLISECOND))
        }.timeInMillis
    }

    private fun observeViewModel(root: View) {
        val progress = root.findViewById<View>(R.id.progressBar)
        val error = root.findViewById<TextView>(R.id.textError)

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            progress.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.error.observe(viewLifecycleOwner) { message ->
            if (message.isNullOrBlank()) {
                error.visibility = View.GONE
            } else {
                error.visibility = View.VISIBLE
                error.text = message
            }
        }

        viewModel.dailyNutrition.observe(viewLifecycleOwner) { dailyNutrition ->
            bindDailyNutrition(root, dailyNutrition)
        }
    }

    private fun refreshProfileAndNutrition(root: View) {
        setupHeader(root)
        viewLifecycleOwner.lifecycleScope.launch {
            applyProfileTargets()
            loadSelectedDay()
            val dayAtRefresh = selectedDayStart
            launch {
                if (isAdded && selectedDayStart == dayAtRefresh) {
                    loadRecommendations(root)
                }
            }
        }
    }

    private suspend fun applyProfileTargets() {
        val user = userRepository.getCurrentUser()
        if (user == null) {
            hasConfiguredProfile = false
            dailyLimits = DailyLimits()
            mealGoals = mealSchedulePreferences.buildCalorieTargets(dailyLimits.calories)
            return
        }

        hasConfiguredProfile = true
        val targets = nutritionTargetsResolver.resolve(user)
        dailyLimits = DailyLimits(
            calories = targets.targetCalories,
            protein = targets.proteinGrams,
            fat = targets.fatGrams,
            carbs = targets.carbsGrams,
            goal = user.goal
        )
        mealGoals = mealSchedulePreferences.buildCalorieTargets(targets.targetCalories)
    }

    private fun bindProfileSummary(
        root: View,
        user: User?,
        targets: NutritionTargets?
    ) {
        val card = root.findViewById<LinearLayout>(R.id.cardProfileSummary)
        val secondColumn = card.getChildAt(1) as? LinearLayout ?: return
        val titleView = secondColumn.getChildAt(0) as? TextView ?: return
        val valueView = secondColumn.getChildAt(1) as? TextView ?: return

        if (user == null || targets == null) {
            titleView.text = "Профиль"
            valueView.text = "Настроить"
            return
        }

        titleView.text = "Текущая цель"
        valueView.text = when (user.goal) {
            UserGoal.WEIGHT_LOSS -> "Снижение"
            UserGoal.MAINTAIN_WEIGHT -> "Поддержание"
            UserGoal.WEIGHT_GAIN -> "Набор"
            UserGoal.MUSCLE_GAIN_TRAINING -> "Мышцы"
        }
    }

    private fun bindDailyNutrition(root: View, dailyNutrition: DailyNutrition) {
        latestDailyNutrition = dailyNutrition
        refreshSmartCoach(root)

        val ringView = root.findViewById<CalorieRingView>(R.id.calorieRingView)
        val textNormCalories = root.findViewById<TextView>(R.id.textNormCalories)
        val textConsumedCalories = root.findViewById<TextView>(R.id.textConsumedCalories)
        val textRemainingCalories = root.findViewById<TextView>(R.id.textRemainingCalories)
        val textProteinValue = root.findViewById<TextView>(R.id.textProteinValue)
        val textFatValue = root.findViewById<TextView>(R.id.textFatValue)
        val textCarbsValue = root.findViewById<TextView>(R.id.textCarbsValue)
        val progressProtein = root.findViewById<ProgressBar>(R.id.progressProtein)
        val progressFat = root.findViewById<ProgressBar>(R.id.progressFat)
        val progressCarbs = root.findViewById<ProgressBar>(R.id.progressCarbs)

        val breakfastCurrent = root.findViewById<TextView>(R.id.textBreakfastCaloriesCurrent)
        val breakfastGoal = root.findViewById<TextView>(R.id.textBreakfastCaloriesGoal)
        val lunchCurrent = root.findViewById<TextView>(R.id.textLunchCaloriesCurrent)
        val lunchGoal = root.findViewById<TextView>(R.id.textLunchCaloriesGoal)
        val dinnerCurrent = root.findViewById<TextView>(R.id.textDinnerCaloriesCurrent)
        val dinnerGoal = root.findViewById<TextView>(R.id.textDinnerCaloriesGoal)
        val snackCurrent = root.findViewById<TextView>(R.id.textSnackCaloriesCurrent)
        val snackGoal = root.findViewById<TextView>(R.id.textSnackCaloriesGoal)
        val afternoonSnackCurrent = root.findViewById<TextView>(R.id.textAfternoonSnackCaloriesCurrent)
        val afternoonSnackGoal = root.findViewById<TextView>(R.id.textAfternoonSnackCaloriesGoal)
        val lateDinnerCurrent = root.findViewById<TextView>(R.id.textLateDinnerCaloriesCurrent)
        val lateDinnerGoal = root.findViewById<TextView>(R.id.textLateDinnerCaloriesGoal)

        val textBreakfastCount = root.findViewById<TextView>(R.id.textBreakfastCount)
        val textLunchCount = root.findViewById<TextView>(R.id.textLunchCount)
        val textDinnerCount = root.findViewById<TextView>(R.id.textDinnerCount)
        val textSnackCount = root.findViewById<TextView>(R.id.textSnackCount)
        val textAfternoonSnackCount = root.findViewById<TextView>(R.id.textAfternoonSnackCount)
        val textLateDinnerCount = root.findViewById<TextView>(R.id.textLateDinnerCount)
        val textExtraMealsTitle = root.findViewById<TextView>(R.id.textExtraMealsTitle)
        val layoutExtraMeals = root.findViewById<LinearLayout>(R.id.layoutExtraMealsRow)
        val cardSnack = root.findViewById<View>(R.id.cardSnack)
        val spaceDinnerSnack = root.findViewById<View>(R.id.spaceDinnerSnack)
        val cardAfternoonSnack = root.findViewById<View>(R.id.cardAfternoonSnack)
        val cardLateDinner = root.findViewById<View>(R.id.cardLateDinner)
        val spaceExtraMeals = root.findViewById<View>(R.id.spaceExtraMeals)

        val consumedCalories = dailyNutrition.totalCalories.roundToInt()
        val remainingCalories = (dailyLimits.calories - consumedCalories).coerceAtLeast(0)

        ringView.setValues(
            value = consumedCalories.toFloat(),
            max = dailyLimits.calories.toFloat()
        )

        textNormCalories.text = dailyLimits.calories.toString()
        textConsumedCalories.text = consumedCalories.toString()
        textRemainingCalories.text = remainingCalories.toString()

        bindMacro(
            progressBar = progressProtein,
            current = dailyNutrition.totalProtein,
            limit = dailyLimits.protein,
            valueText = textProteinValue
        )

        bindMacro(
            progressBar = progressFat,
            current = dailyNutrition.totalFat,
            limit = dailyLimits.fat,
            valueText = textFatValue
        )

        bindMacro(
            progressBar = progressCarbs,
            current = dailyNutrition.totalCarbs,
            limit = dailyLimits.carbs,
            valueText = textCarbsValue
        )

        bindMealCard(
            currentText = breakfastCurrent,
            goalText = breakfastGoal,
            currentCalories = dailyNutrition.caloriesByMealType[MealType.BREAKFAST] ?: 0.0,
            goalCalories = mealGoals[MealType.BREAKFAST] ?: 0
        )
        bindMealCard(
            currentText = lunchCurrent,
            goalText = lunchGoal,
            currentCalories = dailyNutrition.caloriesByMealType[MealType.LUNCH] ?: 0.0,
            goalCalories = mealGoals[MealType.LUNCH] ?: 0
        )
        bindMealCard(
            currentText = dinnerCurrent,
            goalText = dinnerGoal,
            currentCalories = dailyNutrition.caloriesByMealType[MealType.DINNER] ?: 0.0,
            goalCalories = mealGoals[MealType.DINNER] ?: 0
        )
        bindMealCard(
            currentText = snackCurrent,
            goalText = snackGoal,
            currentCalories = dailyNutrition.caloriesByMealType[MealType.SNACK] ?: 0.0,
            goalCalories = mealGoals[MealType.SNACK] ?: 0
        )
        bindMealCard(
            currentText = afternoonSnackCurrent,
            goalText = afternoonSnackGoal,
            currentCalories = dailyNutrition.caloriesByMealType[MealType.AFTERNOON_SNACK] ?: 0.0,
            goalCalories = mealGoals[MealType.AFTERNOON_SNACK] ?: 0
        )
        bindMealCard(
            currentText = lateDinnerCurrent,
            goalText = lateDinnerGoal,
            currentCalories = dailyNutrition.caloriesByMealType[MealType.LATE_DINNER] ?: 0.0,
            goalCalories = mealGoals[MealType.LATE_DINNER] ?: 0
        )

        textBreakfastCount.text =
            buildMealAreaLabel(dailyNutrition.mealsByType[MealType.BREAKFAST] ?: 0)
        textLunchCount.text =
            buildMealAreaLabel(dailyNutrition.mealsByType[MealType.LUNCH] ?: 0)
        textDinnerCount.text =
            buildMealAreaLabel(dailyNutrition.mealsByType[MealType.DINNER] ?: 0)
        textSnackCount.text =
            buildMealAreaLabel(dailyNutrition.mealsByType[MealType.SNACK] ?: 0)
        textAfternoonSnackCount.text =
            buildMealAreaLabel(dailyNutrition.mealsByType[MealType.AFTERNOON_SNACK] ?: 0)
        textLateDinnerCount.text =
            buildMealAreaLabel(dailyNutrition.mealsByType[MealType.LATE_DINNER] ?: 0)

        val enabledMeals = mealSchedulePreferences.getEnabledMealTypes()
        val showSnack = MealType.SNACK in enabledMeals
        val showExtras = MealType.AFTERNOON_SNACK in enabledMeals || MealType.LATE_DINNER in enabledMeals
        cardSnack.isVisible = showSnack
        spaceDinnerSnack.isVisible = showSnack
        textExtraMealsTitle.isVisible = showExtras
        layoutExtraMeals.isVisible = showExtras
        cardAfternoonSnack.isVisible = MealType.AFTERNOON_SNACK in enabledMeals
        cardLateDinner.isVisible = MealType.LATE_DINNER in enabledMeals
        spaceExtraMeals.isVisible = cardAfternoonSnack.isVisible && cardLateDinner.isVisible
    }

    private fun bindMacro(
        progressBar: ProgressBar,
        current: Double,
        limit: Int,
        valueText: TextView
    ) {
        val roundedCurrent = current.roundToInt().coerceAtLeast(0)
        progressBar.max = limit
        progressBar.progress = roundedCurrent.coerceAtMost(limit)
        valueText.text = "$roundedCurrent / $limit г"
    }

    private fun bindMealCard(
        currentText: TextView,
        goalText: TextView,
        currentCalories: Double,
        goalCalories: Int
    ) {
        currentText.text = "${currentCalories.roundToInt()} ккал"
        goalText.text = "из $goalCalories ккал"
    }

    private fun formatKcal(value: Double): String = "${value.roundToInt()} ккал"

    private fun buildMealGoals(calories: Int): Map<MealType, Int> {
        return mapOf(
            MealType.BREAKFAST to (calories * 0.30).roundToInt(),
            MealType.LUNCH to (calories * 0.40).roundToInt(),
            MealType.DINNER to (calories * 0.20).roundToInt(),
            MealType.SNACK to (calories * 0.10).roundToInt()
        )
    }

    private fun buildProductCountLabel(count: Int): String {
        return when {
            count % 10 == 1 && count % 100 != 11 -> "$count продукт"
            count % 10 in 2..4 && count % 100 !in 12..14 -> "$count продукта"
            else -> "$count продуктов"
        }
    }

    private fun buildMealAreaLabel(count: Int): String {
        return if (count == 0) {
            "Пока пусто"
        } else {
            buildProductCountLabel(count)
        }
    }

    private fun buildSelectedDayMenuLabel(): String {
        return if (isTodaySelected()) {
            "сегодня"
        } else {
            SimpleDateFormat("d MMMM", Locale("ru")).format(selectedDayStart)
        }
    }

    private suspend fun loadRecommendations(root: View) {
        if (!isTodaySelected()) {
            bindWeatherRecommendation(root, null)
            bindRecommendationSection(root, emptyList())
            bindSmartCoach(root, null)
            return
        }
        val recommendations = runCatching {
            personalizedRecommendationsUseCase(
                mealType = null,
                limit = 24
            )
        }.getOrDefault(emptyList())
        if (!isRootActive(root)) return
        latestRecommendations = recommendations
        bindRecommendationSection(
            root,
            if (uiPreferences.isRecommendationSectionEnabled()) recommendations else emptyList()
        )
        refreshSmartCoach(root)
        loadWeatherRecommendation(root)
    }

    private suspend fun loadWeatherRecommendation(
        root: View,
        forceShow: Boolean = false
    ) {
        if (!isTodaySelected() || !uiPreferences.isRecommendationPopupEnabled()) {
            bindWeatherRecommendation(root, null)
            return
        }

        if (!weatherLocationProvider.hasLocationPermission()) {
            bindWeatherRecommendation(
                root = root,
                recommendation = WeatherFoodRecommendation.permissionRequired(),
                forceShow = forceShow
            )
            return
        }

        val location = weatherLocationProvider.getCurrentOrLastKnownLocation()
        if (!isRootActive(root)) return
        if (location == null) {
            bindWeatherRecommendation(
                root = root,
                recommendation = WeatherFoodRecommendation.locationUnavailable(),
                forceShow = forceShow
            )
            return
        }

        val snapshot = loadWeatherSnapshotWithFallback(
            latitude = location.latitude,
            longitude = location.longitude
        )
        if (!isRootActive(root)) return

        if (snapshot == null) {
            if (forceShow) {
                bindWeatherRecommendation(
                    root = root,
                    recommendation = WeatherFoodRecommendation.weatherUnavailable(),
                    forceShow = true
                )
            } else {
                bindWeatherRecommendation(root, null)
            }
            return
        }

        val weatherRecommendation = runCatching {
            weatherRecommendationUseCase(snapshot)
        }.getOrNull()

        if (weatherRecommendation == null && !forceShow) {
            bindWeatherRecommendation(root, null)
        } else {
            bindWeatherRecommendation(
                root = root,
                recommendation = weatherRecommendation,
                forceShow = forceShow
            )
        }
    }

    private suspend fun loadWeatherSnapshotWithFallback(
        latitude: Double,
        longitude: Double
    ): WeatherSnapshot? {
        return runCatching {
            weatherRepository.getWeather(latitude, longitude)
        }.getOrNull()
            ?: weatherRepository.getCachedWeather(latitude, longitude)
            ?: run {
                delay(650L)
                runCatching {
                    weatherRepository.getWeather(latitude, longitude)
                }.getOrNull()
                    ?: weatherRepository.getCachedWeather(latitude, longitude)
            }
    }

    private fun bindRecommendationSection(
        root: View,
        recommendations: List<FoodRecommendation>
    ) {
        val section = root.findViewById<View>(R.id.sectionRecommendations)
        val subtitle = root.findViewById<TextView>(R.id.textRecommendationsSubtitle)

        val cards = buildRecommendationCards(recommendations)
        currentRecommendationCards = cards.associate { it.slot to it.recommendation }

        if (section.id == R.id.sectionRecommendations) {
            section.visibility = View.GONE
            return
        }

        if (cards.isEmpty()) {
            section.visibility = View.GONE
            return
        }

        section.visibility = View.VISIBLE
        subtitle.text = when {
            cards.size >= 3 ->
                "Продукты-кандидаты по балансу, цели и привычкам. Умный помощник объясняет ситуацию, а здесь можно выбрать продукт."
            cards.any { it.slot == RecommendationCardSlot.HABIT } ->
                "Быстрые варианты по привычкам и текущему балансу. Выберите продукт и приём пищи для добавления."
            else ->
                "Быстрые варианты по цели и дневному балансу. Выберите продукт и приём пищи для добавления."
        }

        bindRecommendationCard(
            root = root,
            cardId = R.id.cardRecommendationBalance,
            nameId = R.id.textRecommendationBalanceName,
            reasonId = R.id.textRecommendationBalanceReason,
            slot = RecommendationCardSlot.BALANCE,
            cards = cards
        )
        bindRecommendationCard(
            root = root,
            cardId = R.id.cardRecommendationGoal,
            nameId = R.id.textRecommendationGoalName,
            reasonId = R.id.textRecommendationGoalReason,
            slot = RecommendationCardSlot.GOAL,
            cards = cards
        )
        bindRecommendationCard(
            root = root,
            cardId = R.id.cardRecommendationHabit,
            nameId = R.id.textRecommendationHabitName,
            reasonId = R.id.textRecommendationHabitReason,
            slot = RecommendationCardSlot.HABIT,
            cards = cards
        )
    }

    private fun bindRecommendationCard(
        root: View,
        cardId: Int,
        nameId: Int,
        reasonId: Int,
        slot: RecommendationCardSlot,
        cards: List<RecommendationCardUi>
    ) {
        val card = root.findViewById<View>(cardId)
        val name = root.findViewById<TextView>(nameId)
        val reason = root.findViewById<TextView>(reasonId)
        val model = cards.firstOrNull { it.slot == slot }

        if (model == null) {
            card.visibility = View.GONE
            return
        }

        card.visibility = View.VISIBLE
        name.text = model.recommendation.food.name
        reason.text = model.description
    }

    private fun buildRecommendationCards(
        recommendations: List<FoodRecommendation>
    ): List<RecommendationCardUi> {
        if (recommendations.isEmpty()) return emptyList()

        val cards = mutableListOf<RecommendationCardUi>()
        val usedFoodIds = mutableSetOf<String>()
        val usedFoodRoles = mutableSetOf<RecommendationFoodRole>()

        fun pickBest(
            slot: RecommendationCardSlot,
            selector: (FoodRecommendation) -> Int,
            minScore: Int = 0,
            fallbackAllowed: Boolean = false
        ) {
            val candidates = recommendations
                .asSequence()
                .filter { it.food.id !in usedFoodIds }
                .filter { fallbackAllowed || selector(it) >= minScore }
                .toList()

            val candidate = candidates
                .asSequence()
                .filter { recommendationFoodRole(it.food) !in usedFoodRoles }
                .maxWithOrNull(
                    compareByDescending<FoodRecommendation> { selector(it) }
                        .thenByDescending { it.totalScore }
                )
                ?: candidates.maxWithOrNull(
                    compareByDescending<FoodRecommendation> { selector(it) }
                        .thenByDescending { it.totalScore }
                )
                ?: return

            if (!fallbackAllowed && selector(candidate) < minScore) return

            usedFoodIds += candidate.food.id
            usedFoodRoles += recommendationFoodRole(candidate.food)
            cards += RecommendationCardUi(
                slot = slot,
                description = buildRecommendationCardDescription(slot, candidate),
                recommendation = candidate
            )
        }

        pickBest(
            slot = RecommendationCardSlot.BALANCE,
            selector = { it.breakdown.macroGapScore * 2 + it.breakdown.portionPracticalityScore },
            minScore = 55,
            fallbackAllowed = true
        )
        pickBest(
            slot = RecommendationCardSlot.GOAL,
            selector = { it.breakdown.goalFitScore * 2 + it.breakdown.mealTimingScore },
            minScore = 55
        )
        pickBest(
            slot = RecommendationCardSlot.HABIT,
            selector = {
                it.breakdown.historyScore +
                    it.breakdown.preferenceScore +
                    it.breakdown.confidenceScore / 2
            },
            minScore = 70
        )

        if (cards.isEmpty()) {
            val first = recommendations.first()
            cards += RecommendationCardUi(
                slot = RecommendationCardSlot.BALANCE,
                description = buildRecommendationCardDescription(RecommendationCardSlot.BALANCE, first),
                recommendation = first
            )
        }

        return cards
    }

    private fun recommendationFoodRole(food: Food): RecommendationFoodRole {
        val category = food.category.lowercase(Locale.getDefault())
        val name = food.name.lowercase(Locale.getDefault())
        return when {
            food.proteinPer100g >= 14.0 || category.contains("protein") -> RecommendationFoodRole.PROTEIN
            food.caloriesPer100g >= 420.0 || food.fatPer100g >= 22.0 -> RecommendationFoodRole.DENSE
            category.contains("fruit") || category.contains("dairy") || name.contains("йогурт") -> RecommendationFoodRole.SNACK
            category.contains("vegetable") || food.caloriesPer100g <= 90.0 -> RecommendationFoodRole.LIGHT
            food.carbsPer100g >= 22.0 || category.contains("grain") -> RecommendationFoodRole.CARBS
            else -> RecommendationFoodRole.OTHER
        }
    }

    private fun buildRecommendationCardDescription(
        slot: RecommendationCardSlot,
        recommendation: FoodRecommendation
    ): String {
        val secondary = recommendation.secondaryReason
        val breakdown = recommendation.breakdown
        return when (slot) {
            RecommendationCardSlot.BALANCE -> when {
                breakdown.portionPracticalityScore >= 78 ->
                    "Закрывает текущий остаток без завышенной порции"
                breakdown.portionPracticalityScore <= 45 ->
                    "Подойдет только небольшой порцией: продукт плотный по КБЖУ"
                else -> secondary ?: recommendation.primaryReason
            }
            RecommendationCardSlot.GOAL -> when {
                breakdown.mealTimingScore >= 78 ->
                    "Хорошо совпадает с целью и текущим приемом пищи"
                else -> recommendation.primaryReason
            }
            RecommendationCardSlot.HABIT -> when {
                breakdown.confidenceScore < 55 ->
                    "Предварительная подсказка: точность вырастет после новых записей"
                else -> secondary ?: recommendation.primaryReason
            }
        }
    }

    private fun bindMomentRecommendation(
        root: View,
        insight: SmartCoachInsight
    ): MomentRecommendationUi? {
        val card = root.findViewById<View>(R.id.cardSmartCoachMomentRecommendation)
        val options = buildMomentRecommendationOptions(insight)
        val active = resolveActiveMomentRecommendation(insight, options)
        currentMomentRecommendation = active

        if (active == null) {
            card.visibility = View.GONE
            root.findViewById<View>(R.id.buttonSmartCoachMomentVariants).visibility = View.GONE
            return null
        }

        card.visibility = View.VISIBLE
        card.background = GradientDrawable().apply {
            cornerRadius = 18.dp().toFloat()
            setColor(Color.parseColor(active.backgroundColor))
            setStroke(1.dp(), Color.parseColor(active.borderColor))
        }
        root.findViewById<TextView>(R.id.textSmartCoachCorrectionTitle).text = active.title
        root.findViewById<TextView>(R.id.textSmartCoachCorrectionMessage).text =
            buildMomentMainCardDescription(active, insight)
        root.findViewById<TextView>(R.id.textSmartCoachCorrectionDelta).apply {
            visibility = View.VISIBLE
            text = formatMomentNutritionTag(active.recommendation.food)
            setTextColor(Color.parseColor(active.accentColor))
            background = GradientDrawable().apply {
                cornerRadius = 999f
                setColor(Color.parseColor(active.tagBackgroundColor))
                setStroke(1.dp(), Color.parseColor(active.borderColor))
            }
        }
        root.findViewById<TextView>(R.id.buttonSmartCoachMomentVariants).apply {
            visibility = if (options.size > 1) View.VISIBLE else View.GONE
            text = "Варианты"
            setTextColor(Color.parseColor(active.accentColor))
            background = GradientDrawable().apply {
                cornerRadius = 999f
                setColor(Color.parseColor(active.tagBackgroundColor))
                setStroke(1.dp(), Color.parseColor(active.borderColor))
            }
        }
        return active
    }

    private fun buildMomentRecommendationOptions(
        insight: SmartCoachInsight
    ): List<MomentRecommendationUi> {
        val recommendations = latestRecommendations.distinctBy { it.food.id }
        if (recommendations.isEmpty()) return emptyList()

        val usedFoodIds = mutableSetOf<String>()
        val options = mutableListOf<MomentRecommendationUi>()

        fun addOption(
            kind: MomentRecommendationKind,
            title: String,
            badge: String,
            accentColor: String,
            backgroundColor: String,
            borderColor: String,
            tagBackgroundColor: String,
            candidates: List<FoodRecommendation>
        ) {
            val candidate = candidates
                .filter { it.food.id !in usedFoodIds }
                .maxWithOrNull(
                    compareBy<FoodRecommendation> { momentKindScore(kind, it) }
                        .thenBy { it.totalScore }
                )
                ?: return

            usedFoodIds += candidate.food.id
            options += MomentRecommendationUi(
                kind = kind,
                title = title,
                badge = badge,
                description = buildMomentRecommendationDescription(kind, candidate, insight),
                recommendation = candidate,
                mealType = insight.suggestedMealType,
                accentColor = accentColor,
                backgroundColor = backgroundColor,
                borderColor = borderColor,
                tagBackgroundColor = tagBackgroundColor
            )
        }

        val suggestedRecommendation = insight.suggestedFood?.let { food ->
            recommendations.firstOrNull { it.food.id == food.id }
        }
        addOption(
            kind = MomentRecommendationKind.BALANCE,
            title = buildMomentPrimaryTitle(insight),
            badge = "Актуально",
            accentColor = "#51631F",
            backgroundColor = "#EFFBE5",
            borderColor = "#CFE7A3",
            tagBackgroundColor = "#F5FFD9",
            candidates = listOfNotNull(suggestedRecommendation).ifEmpty {
                recommendations.sortedByDescending {
                    it.breakdown.macroGapScore * 2 +
                        it.breakdown.portionPracticalityScore +
                        it.breakdown.mealTimingScore / 2
                }
            }
        )
        addOption(
            kind = MomentRecommendationKind.GOAL,
            title = "Под вашу цель",
            badge = "Цель",
            accentColor = "#7A5A00",
            backgroundColor = "#FFF8E8",
            borderColor = "#EBCB67",
            tagBackgroundColor = "#FFF3CC",
            candidates = recommendations
        )
        addOption(
            kind = MomentRecommendationKind.HABIT,
            title = "Привычный выбор",
            badge = "Привычки",
            accentColor = "#6D46C9",
            backgroundColor = "#F7F0FF",
            borderColor = "#D9C6FF",
            tagBackgroundColor = "#F1E9FF",
            candidates = recommendations.filter {
                it.breakdown.historyScore + it.breakdown.confidenceScore / 2 >= 45
            }
        )
        addOption(
            kind = MomentRecommendationKind.FAVORITE,
            title = "Избранное и свои продукты",
            badge = "Личное",
            accentColor = "#6D46C9",
            backgroundColor = "#F7F0FF",
            borderColor = "#D9C6FF",
            tagBackgroundColor = "#F1E9FF",
            candidates = recommendations.filter {
                it.breakdown.preferenceScore >= 45 || it.food.isCustom
            }
        )
        addOption(
            kind = MomentRecommendationKind.RECIPE,
            title = "Рецепт под цель",
            badge = "Рецепт",
            accentColor = "#51631F",
            backgroundColor = "#F4FCEB",
            borderColor = "#CFE7A3",
            tagBackgroundColor = "#F5FFD9",
            candidates = recommendations.filter {
                it.food.isCustom && it.food.category.equals("custom_recipe", ignoreCase = true)
            }
        )
        addOption(
            kind = MomentRecommendationKind.LIGHT,
            title = "Легкий вариант",
            badge = "Без перебора",
            accentColor = "#7A5A00",
            backgroundColor = "#FFF8E8",
            borderColor = "#EBCB67",
            tagBackgroundColor = "#FFF3CC",
            candidates = recommendations.filter {
                it.food.caloriesPer100g <= 260.0 || it.breakdown.portionPracticalityScore >= 72
            }
        )

        if (options.isEmpty()) {
            val fallback = recommendations.first()
            options += MomentRecommendationUi(
                kind = MomentRecommendationKind.BALANCE,
                title = buildMomentPrimaryTitle(insight),
                badge = "Актуально",
                description = buildMomentRecommendationDescription(MomentRecommendationKind.BALANCE, fallback, insight),
                recommendation = fallback,
                mealType = insight.suggestedMealType,
                accentColor = "#51631F",
                backgroundColor = "#EFFBE5",
                borderColor = "#CFE7A3",
                tagBackgroundColor = "#F5FFD9"
            )
        }

        return options
    }

    private fun resolveActiveMomentRecommendation(
        insight: SmartCoachInsight,
        options: List<MomentRecommendationUi>
    ): MomentRecommendationUi? {
        if (options.isEmpty()) {
            selectedMomentRecommendationKind = null
            return null
        }

        selectedMomentRecommendationKind?.let { selectedKind ->
            options.firstOrNull { it.kind == selectedKind }?.let { return it }
        }
        selectedMomentRecommendationKind = null

        val preferredKinds = when {
            (latestDailyNutrition?.mealsCount ?: 0) == 0 -> listOf(
                MomentRecommendationKind.HABIT,
                MomentRecommendationKind.FAVORITE,
                MomentRecommendationKind.GOAL,
                MomentRecommendationKind.BALANCE
            )
            insight.focus == SmartCoachFocus.CALORIES_EXCESS -> listOf(
                MomentRecommendationKind.LIGHT,
                MomentRecommendationKind.GOAL,
                MomentRecommendationKind.HABIT,
                MomentRecommendationKind.BALANCE
            )
            insight.focus == SmartCoachFocus.BALANCED -> listOf(
                MomentRecommendationKind.GOAL,
                MomentRecommendationKind.LIGHT,
                MomentRecommendationKind.HABIT,
                MomentRecommendationKind.BALANCE
            )
            else -> listOf(
                MomentRecommendationKind.BALANCE,
                MomentRecommendationKind.RECIPE,
                MomentRecommendationKind.GOAL,
                MomentRecommendationKind.HABIT,
                MomentRecommendationKind.FAVORITE
            )
        }
        return preferredKinds.firstNotNullOfOrNull { kind ->
            options.firstOrNull { it.kind == kind }
        } ?: options.first()
    }

    private fun momentKindScore(
        kind: MomentRecommendationKind,
        recommendation: FoodRecommendation
    ): Int {
        val breakdown = recommendation.breakdown
        return when (kind) {
            MomentRecommendationKind.BALANCE ->
                breakdown.macroGapScore * 2 + breakdown.portionPracticalityScore + breakdown.mealTimingScore / 2
            MomentRecommendationKind.GOAL ->
                breakdown.goalFitScore * 2 + breakdown.mealTimingScore + breakdown.roleBalanceScore / 2
            MomentRecommendationKind.HABIT ->
                breakdown.historyScore * 2 + breakdown.confidenceScore + breakdown.preferenceScore / 2
            MomentRecommendationKind.FAVORITE ->
                breakdown.preferenceScore * 2 + breakdown.confidenceScore + if (recommendation.food.isCustom) 20 else 0
            MomentRecommendationKind.RECIPE ->
                breakdown.goalFitScore + breakdown.macroGapScore + breakdown.portionPracticalityScore
            MomentRecommendationKind.LIGHT ->
                inverseCaloriesScore(recommendation.food) +
                    breakdown.portionPracticalityScore +
                    breakdown.mealTimingScore +
                    breakdown.goalFitScore / 2
        }
    }

    private fun buildMomentPrimaryTitle(insight: SmartCoachInsight): String {
        return when (insight.focus) {
            SmartCoachFocus.PROTEIN_DEFICIT -> "Быстро добрать белок"
            SmartCoachFocus.CARBS_DEFICIT -> "Быстро добрать углеводы"
            SmartCoachFocus.FAT_DEFICIT -> "Быстро добрать жиры"
            SmartCoachFocus.CALORIES_DEFICIT -> "Быстрый добор КБЖУ"
            SmartCoachFocus.CALORIES_EXCESS -> "Легкий выбор сейчас"
            SmartCoachFocus.BALANCED -> "Актуальная рекомендация"
        }
    }

    private fun buildMomentRecommendationDescription(
        kind: MomentRecommendationKind,
        recommendation: FoodRecommendation,
        insight: SmartCoachInsight
    ): String {
        val food = recommendation.food
        val breakdown = recommendation.breakdown
        val base = when (kind) {
            MomentRecommendationKind.BALANCE ->
                "${food.name} выбран как быстрый способ приблизиться к дневной цели без составления отдельного приема пищи."
            MomentRecommendationKind.GOAL ->
                "${food.name} лучше всего совпадает с текущей целью пользователя и временем приема пищи."
            MomentRecommendationKind.HABIT ->
                "${food.name} похож на привычный выбор: учитываются история дневника и уверенность системы."
            MomentRecommendationKind.FAVORITE ->
                "${food.name} выбран из личных сигналов: избранного, пользовательских продуктов или часто отмечаемых позиций."
            MomentRecommendationKind.RECIPE ->
                "${food.name} — пользовательский рецепт, который можно добавить как готовое блюдо вместо подбора отдельных продуктов."
            MomentRecommendationKind.LIGHT ->
                "${food.name} подходит как спокойный вариант, когда важно не перегрузить остаток дня калориями."
        }
        val focusText = when (insight.focus) {
            SmartCoachFocus.PROTEIN_DEFICIT -> "Главный акцент сейчас — белок."
            SmartCoachFocus.CARBS_DEFICIT -> "Главный акцент сейчас — углеводы и энергия."
            SmartCoachFocus.FAT_DEFICIT -> "Главный акцент сейчас — мягко добрать жиры."
            SmartCoachFocus.CALORIES_DEFICIT -> "Главный акцент сейчас — закрыть заметный остаток калорий."
            SmartCoachFocus.CALORIES_EXCESS -> "Главный акцент сейчас — выбрать продукт без лишнего перебора."
            SmartCoachFocus.BALANCED -> "Критичного отклонения нет, поэтому важны цель, привычки и разнообразие."
        }
        val scoreText = "Учтены остаток КБЖУ ${formatScore(breakdown.macroGapScore)}, " +
            "цель ${formatScore(breakdown.goalFitScore)}, порция ${formatScore(breakdown.portionPracticalityScore)} " +
            "и уместность сейчас ${formatScore(breakdown.mealTimingScore)}."
        return listOf(base, focusText, scoreText, recommendation.secondaryReason ?: recommendation.primaryReason)
            .joinToString(" ")
    }

    private fun buildMomentMainCardDescription(
        option: MomentRecommendationUi,
        insight: SmartCoachInsight
    ): String {
        val food = option.recommendation.food
        val breakdown = option.recommendation.breakdown
        val reason = when (option.kind) {
            MomentRecommendationKind.BALANCE -> when (insight.focus) {
                SmartCoachFocus.PROTEIN_DEFICIT -> "помогает быстро добрать белок без сборки отдельного приема пищи"
                SmartCoachFocus.CARBS_DEFICIT -> "помогает закрыть углеводы и часть калорийного остатка"
                SmartCoachFocus.FAT_DEFICIT -> "помогает мягко добрать жиры без случайного выбора"
                SmartCoachFocus.CALORIES_EXCESS -> "подходит как более спокойный вариант без лишнего перебора"
                SmartCoachFocus.CALORIES_DEFICIT -> "помогает быстрее сократить заметный остаток по КБЖУ"
                SmartCoachFocus.BALANCED -> "поддерживает текущий баланс без перегруза"
            }
            MomentRecommendationKind.GOAL -> "лучше совпадает с выбранной целью и текущим временем приема пищи"
            MomentRecommendationKind.HABIT -> "опирается на историю дневника и привычные пищевые сигналы"
            MomentRecommendationKind.FAVORITE -> "выбран из личных сигналов: избранного, своих продуктов или частых позиций"
            MomentRecommendationKind.RECIPE -> "позволяет добавить готовое пользовательское блюдо вместо ручного подбора"
            MomentRecommendationKind.LIGHT -> "подходит, если нужен аккуратный выбор без перегруза остатка дня"
        }
        return "${food.name}: $reason. " +
            "Оценка: остаток ${formatScore(breakdown.macroGapScore)}, цель ${formatScore(breakdown.goalFitScore)}, " +
            "порция ${formatScore(breakdown.portionPracticalityScore)}."
    }

    private fun showMomentRecommendationOptionsDialog() {
        if (!canShowTransientUi()) return
        val insight = currentSmartCoachInsight ?: return
        val options = buildMomentRecommendationOptions(insight)
        if (options.size <= 1) return
        val active = resolveActiveMomentRecommendation(insight, options)

        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val panel = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18.dp(), 18.dp(), 18.dp(), 18.dp())
            background = GradientDrawable().apply {
                cornerRadius = 28.dp().toFloat()
                setColor(Color.parseColor("#FFFDF8"))
                setStroke(1.dp(), Color.parseColor("#E7D8F4"))
            }
        }

        panel.addView(
            View(requireContext()).apply {
                background = GradientDrawable().apply {
                    cornerRadius = 999f
                    setColor(Color.parseColor("#D9C6FF"))
                }
                layoutParams = LinearLayout.LayoutParams(52.dp(), 5.dp()).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    bottomMargin = 14.dp()
                }
            }
        )

        val header = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        header.addView(
            TextView(requireContext()).apply {
                text = "Выбрать рекомендацию"
                textSize = 22f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#2F2433"))
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }
        )
        header.addView(
            TextView(requireContext()).apply {
                text = "×"
                textSize = 26f
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#B28FEF"))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#F1E9FF"))
                    setStroke(1.dp(), Color.parseColor("#E3D4FA"))
                }
                layoutParams = LinearLayout.LayoutParams(42.dp(), 42.dp())
                setDebouncedClickListener { dialog.dismiss() }
            }
        )
        panel.addView(header)

        panel.addView(
            TextView(requireContext()).apply {
                text = "Основная карточка выбирается автоматически по ситуации дня, но здесь можно открыть другие логики: добор, цель, привычки, избранное или рецепт."
                textSize = 13f
                setTextColor(Color.parseColor("#7B6E84"))
                setLineSpacing(2f, 1.0f)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 6.dp()
                    bottomMargin = 10.dp()
                }
            }
        )

        val list = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }
        options.forEach { option ->
            list.addView(
                buildMomentRecommendationChoiceCard(
                    option = option,
                    isActive = option.kind == active?.kind,
                    onSelected = {
                        selectedMomentRecommendationKind = option.kind
                        dialog.dismiss()
                        view?.let { bindSmartCoach(it, currentSmartCoachInsight) }
                    }
                )
            )
        }

        panel.addView(
            ScrollView(requireContext()).apply {
                addView(list)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setPadding(10.dp(), 0, 10.dp(), 10.dp())
            }
        )

        dialog.setContentView(panel)
        if (!canShowTransientUi()) return
        dialog.show()
        dialog.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            window.setGravity(Gravity.BOTTOM)
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.attributes = window.attributes.apply {
                dimAmount = 0.32f
            }
        }
    }

    private fun buildMomentRecommendationChoiceCard(
        option: MomentRecommendationUi,
        isActive: Boolean,
        onSelected: () -> Unit
    ): View {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12.dp(), 11.dp(), 12.dp(), 11.dp())
            background = GradientDrawable().apply {
                cornerRadius = 16.dp().toFloat()
                setColor(Color.parseColor(option.backgroundColor))
                setStroke(
                    if (isActive) 2.dp() else 1.dp(),
                    Color.parseColor(option.borderColor)
                )
            }
            isClickable = true
            isFocusable = true
            setDebouncedClickListener { onSelected() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 8.dp()
            }

            val header = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            header.addView(
                TextView(requireContext()).apply {
                    text = option.title
                    textSize = 15f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.parseColor("#2F2433"))
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                }
            )
            if (isActive) {
                header.addView(
                    TextView(requireContext()).apply {
                        text = "Активно"
                        textSize = 11f
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(Color.parseColor("#51631F"))
                        background = GradientDrawable().apply {
                            cornerRadius = 999f
                            setColor(Color.parseColor("#F5FFD9"))
                            setStroke(1.dp(), Color.parseColor("#CFE7A3"))
                        }
                        setPadding(8.dp(), 4.dp(), 8.dp(), 4.dp())
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            marginStart = 8.dp()
                        }
                    }
                )
            }
            header.addView(
                TextView(requireContext()).apply {
                    text = option.badge
                    textSize = 11f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.parseColor(option.accentColor))
                    background = GradientDrawable().apply {
                        cornerRadius = 999f
                        setColor(Color.parseColor(option.tagBackgroundColor))
                        setStroke(1.dp(), Color.parseColor(option.borderColor))
                    }
                    setPadding(8.dp(), 4.dp(), 8.dp(), 4.dp())
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        marginStart = 8.dp()
                    }
                }
            )
            addView(header)

            addView(
                TextView(requireContext()).apply {
                    text = option.recommendation.food.name
                    textSize = 13f
                    setTextColor(Color.parseColor("#7B6E84"))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = 6.dp()
                    }
                }
            )

            addView(
                TextView(requireContext()).apply {
                    text = option.description
                    textSize = 12f
                    setTextColor(Color.parseColor("#6B5B73"))
                    setLineSpacing(2f, 1.0f)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = 6.dp()
                    }
                }
            )

            addView(
                TextView(requireContext()).apply {
                    text = formatMomentNutritionTag(option.recommendation.food)
                    textSize = 12f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.parseColor(option.accentColor))
                    background = GradientDrawable().apply {
                        cornerRadius = 999f
                        setColor(Color.parseColor(option.tagBackgroundColor))
                        setStroke(1.dp(), Color.parseColor(option.borderColor))
                    }
                    setPadding(10.dp(), 6.dp(), 10.dp(), 6.dp())
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = 9.dp()
                    }
                }
            )

            addView(
                TextView(requireContext()).apply {
                    text = if (isActive) "Этот вариант сейчас показан на главном экране" else "Сделать активным"
                    textSize = 13f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setTextColor(Color.parseColor(option.accentColor))
                    background = GradientDrawable().apply {
                        cornerRadius = 999f
                        setColor(Color.parseColor("#FFFFFF"))
                        setStroke(1.dp(), Color.parseColor(option.borderColor))
                    }
                    setPadding(12.dp(), 7.dp(), 12.dp(), 7.dp())
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = 10.dp()
                    }
                }
            )
        }
    }

    private fun formatMomentNutritionTag(food: Food): String {
        return "На 100 г: ${food.caloriesPer100g.roundToInt()} ккал, " +
            "${formatMacroValue(food.proteinPer100g)} Б / " +
            "${formatMacroValue(food.fatPer100g)} Ж / " +
            "${formatMacroValue(food.carbsPer100g)} У"
    }

    private fun inverseCaloriesScore(food: Food): Int {
        return (100 - (food.caloriesPer100g / 5.0).roundToInt()).coerceIn(0, 100)
    }

    private fun buildActiveMomentScoreDetails(
        insight: SmartCoachInsight,
        activeMoment: MomentRecommendationUi?
    ): List<SmartCoachScoreDetail> {
        val active = activeMoment
        if (active == null) {
            return insight.scoreDetails.filter { it.section != SmartCoachScoreSection.DAY_STATE }
        }

        val breakdown = active.recommendation.breakdown
        val details = mutableListOf<SmartCoachScoreDetail>()
        val mealsCount = latestDailyNutrition?.mealsCount ?: 0

        details += SmartCoachScoreDetail(
            section = SmartCoachScoreSection.RECOMMENDATION,
            label = "Попадание в остаток",
            value = breakdown.macroGapScore,
            description = if (mealsCount == 0) {
                "продукт сравнен с полной дневной нормой, так как записей за день еще нет"
            } else {
                "насколько выбранный продукт закрывает текущий остаток КБЖУ"
            }
        )
        details += SmartCoachScoreDetail(
            section = SmartCoachScoreSection.RECOMMENDATION,
            label = "Практичность порции",
            value = breakdown.portionPracticalityScore,
            description = "оценивает, можно ли закрыть потребность нормальной порцией без завышения граммовки"
        )
        details += SmartCoachScoreDetail(
            section = SmartCoachScoreSection.RECOMMENDATION,
            label = "Уместность сейчас",
            value = breakdown.mealTimingScore,
            description = "учитывает текущий прием пищи, время дня и пищевую роль продукта"
        )
        details += SmartCoachScoreDetail(
            section = SmartCoachScoreSection.RECOMMENDATION,
            label = "Соответствие цели",
            value = breakdown.goalFitScore,
            description = "соответствие снижению, поддержанию или набору"
        )
        details += SmartCoachScoreDetail(
            section = SmartCoachScoreSection.RECOMMENDATION,
            label = "Уверенность",
            value = breakdown.confidenceScore,
            description = "чем больше данных о профиле, дневнике, избранном и истории, тем надежнее рекомендация"
        )
        details += SmartCoachScoreDetail(
            section = SmartCoachScoreSection.RECOMMENDATION,
            label = "Привычность",
            value = (breakdown.historyScore + breakdown.preferenceScore).coerceIn(0, 100),
            description = "история питания, избранное и пользовательские продукты"
        )
        details += SmartCoachScoreDetail(
            section = SmartCoachScoreSection.RECOMMENDATION,
            label = "Свежесть выбора",
            value = ((breakdown.varietyScore * 0.65) + (breakdown.roleBalanceScore * 0.35))
                .roundToInt()
                .coerceIn(0, 100),
            description = "учитывает повторяемость продукта и его роль в рационе"
        )

        insight.weatherContext?.let {
            details += SmartCoachScoreDetail(
                section = SmartCoachScoreSection.CONTEXT,
                label = "Погода",
                value = 72,
                description = "внешний контекст добавлен к объяснению выбора"
            )
        }
        if (breakdown.safetyPenalty > 0) {
            details += SmartCoachScoreDetail(
                section = SmartCoachScoreSection.CONTEXT,
                label = "Безопасность",
                value = (100 - breakdown.safetyPenalty).coerceIn(0, 100),
                description = "учтен возможный аллергенный риск выбранного продукта"
            )
        }

        return details
    }

    private fun refreshSmartCoach(root: View) {
        smartCoachJob?.cancel()

        val dailyNutrition = latestDailyNutrition
        if (!isTodaySelected() || !hasConfiguredProfile || dailyNutrition == null) {
            smartCoachPlanSession = null
            currentSmartCoachMealPlanProgress = emptyMap()
            bindSmartCoach(root, null)
            return
        }

        val owner = viewLifecycleOwnerLiveData.value ?: return
        if (!isRootActive(root)) return

        smartCoachJob = owner.lifecycleScope.launch {
            val currentMeals = runCatching {
                getMealsForPeriodUseCase(selectedDayStart, endOfDay(selectedDayStart))
            }.getOrDefault(emptyList())
            val insight = runCatching {
                smartCoachInsightUseCase(
                    dayStart = selectedDayStart,
                    dailyNutrition = dailyNutrition,
                    recommendations = latestRecommendations,
                    weatherRecommendation = currentWeatherRecommendation,
                    nowMillis = System.currentTimeMillis()
                )
            }.getOrNull()
                ?.let { applySmartCoachPlanSession(it, currentMeals) }

            if (isRootActive(root)) {
                bindSmartCoach(root, insight)
            }
        }
    }

    private fun isRootActive(root: View): Boolean {
        return isAdded && view === root && viewLifecycleOwnerLiveData.value != null
    }

    private fun applySmartCoachPlanSession(
        insight: SmartCoachInsight,
        currentMeals: List<Meal>
    ): SmartCoachInsight {
        val computedPlan = insight.mealPlan
        if (computedPlan == null) {
            smartCoachPlanSession = null
            currentSmartCoachMealPlanProgress = emptyMap()
            return insight
        }

        val activeSession = smartCoachPlanSession
            ?.takeIf { it.dayStart == selectedDayStart }
            ?.takeUnless { hasSmartCoachPlanDeviation(it, currentMeals) }
            ?: SmartCoachPlanSession(
                dayStart = selectedDayStart,
                plan = computedPlan,
                baselineQuantities = buildMealQuantities(currentMeals),
                planKeys = buildMealPlanKeys(computedPlan)
            ).also { smartCoachPlanSession = it }

        val progress = buildSmartCoachPlanProgress(activeSession, currentMeals)
        currentSmartCoachMealPlanProgress = progress
        return insight.copy(
            mealPlan = adjustSmartCoachMealPlanForProgress(
                mealPlan = activeSession.plan,
                progress = progress
            )
        )
    }

    private fun hasSmartCoachPlanDeviation(
        session: SmartCoachPlanSession,
        currentMeals: List<Meal>
    ): Boolean {
        val currentQuantities = buildMealQuantities(currentMeals)
        return currentQuantities.any { (key, currentQuantity) ->
            val baselineQuantity = session.baselineQuantities[key] ?: 0.0
            currentQuantity > baselineQuantity + 2.0 && key !in session.planKeys
        }
    }

    private fun buildSmartCoachPlanProgress(
        session: SmartCoachPlanSession,
        currentMeals: List<Meal>
    ): Map<SmartCoachMealKey, SmartCoachPlanProgress> {
        val currentQuantities = buildMealQuantities(currentMeals)
        return session.planKeys.mapNotNull { key ->
            val baselineQuantity = session.baselineQuantities[key] ?: 0.0
            val consumed = ((currentQuantities[key] ?: 0.0) - baselineQuantity)
                .roundToInt()
                .coerceAtLeast(0)
            if (consumed <= 0) {
                null
            } else {
                key to SmartCoachPlanProgress(consumedGrams = consumed)
            }
        }.toMap()
    }

    private fun buildMealQuantities(meals: List<Meal>): Map<SmartCoachMealKey, Double> {
        return meals
            .groupBy { meal -> SmartCoachMealKey(meal.mealType, meal.foodId) }
            .mapValues { (_, groupedMeals) -> groupedMeals.sumOf { it.quantityInGrams } }
    }

    private fun buildMealPlanKeys(mealPlan: SmartCoachMealPlan): Set<SmartCoachMealKey> {
        return mealPlan.sections
            .flatMap { section ->
                section.options.flatMap { option ->
                    option.items.map { item ->
                        SmartCoachMealKey(section.mealType, item.food.id)
                    }
                }
            }
            .toSet()
    }

    private fun adjustSmartCoachMealPlanForProgress(
        mealPlan: SmartCoachMealPlan,
        progress: Map<SmartCoachMealKey, SmartCoachPlanProgress>
    ): SmartCoachMealPlan {
        return mealPlan.copy(
            sections = mealPlan.sections.map { section ->
                section.copy(
                    options = section.options.map { option ->
                        adjustSmartCoachMealPlanOptionForProgress(section, option, progress)
                    }
                )
            }
        )
    }

    private fun adjustSmartCoachMealPlanOptionForProgress(
        section: SmartCoachMealPlanSection,
        option: SmartCoachMealPlanOption,
        progress: Map<SmartCoachMealKey, SmartCoachPlanProgress>
    ): SmartCoachMealPlanOption {
        val adjustedItems = option.items.map { item ->
            val key = SmartCoachMealKey(section.mealType, item.food.id)
            val consumed = progress[key]?.consumedGrams ?: 0
            item.copy(quantityInGrams = (item.quantityInGrams - consumed).coerceAtLeast(0))
        }
        val nutrition = calculateSmartCoachOptionNutrition(adjustedItems)
        val isCompleted = adjustedItems.all { it.quantityInGrams == 0 } &&
            option.items.any { item ->
                val key = SmartCoachMealKey(section.mealType, item.food.id)
                (progress[key]?.consumedGrams ?: 0) > 0
            }
        return option.copy(
            subtitle = if (isCompleted) {
                "Все продукты из варианта добавлены"
            } else {
                option.subtitle
            },
            items = adjustedItems,
            calories = nutrition.calories,
            protein = nutrition.protein,
            fat = nutrition.fat,
            carbs = nutrition.carbs
        )
    }

    private fun calculateSmartCoachOptionNutrition(
        items: List<SmartCoachMealPlanItem>
    ): SmartCoachOptionNutrition {
        var calories = 0.0
        var protein = 0.0
        var fat = 0.0
        var carbs = 0.0
        items.forEach { item ->
            val multiplier = item.quantityInGrams / 100.0
            calories += item.food.caloriesPer100g * multiplier
            protein += item.food.proteinPer100g * multiplier
            fat += item.food.fatPer100g * multiplier
            carbs += item.food.carbsPer100g * multiplier
        }
        return SmartCoachOptionNutrition(
            calories = calories.roundToInt(),
            protein = protein.roundToInt(),
            fat = fat.roundToInt(),
            carbs = carbs.roundToInt()
        )
    }

    private fun bindSmartCoach(
        root: View,
        insight: SmartCoachInsight?
    ) {
        val section = root.findViewById<View>(R.id.sectionSmartCoach)
        currentSmartCoachInsight = insight

        if (insight == null) {
            currentSmartCoachMealPlanProgress = emptyMap()
            currentMomentRecommendation = null
            section.visibility = View.GONE
            return
        }

        section.visibility = View.VISIBLE
        root.findViewById<TextView>(R.id.textSmartCoachScore).text = formatScore(insight.balanceScore)
        root.findViewById<TextView>(R.id.textSmartCoachTitle).text = insight.balanceTitle
        root.findViewById<TextView>(R.id.textSmartCoachMessage).text = insight.balanceMessage
        root.findViewById<TextView>(R.id.textSmartCoachForecastTitle).text = insight.forecast.title
        root.findViewById<TextView>(R.id.textSmartCoachForecastMessage).text = insight.forecast.message
        val activeMomentRecommendation = bindMomentRecommendation(root, insight)

        val replacementCard = root.findViewById<View>(R.id.cardSmartCoachReplacement)
        replacementCard.visibility = View.GONE

        root.findViewById<View>(R.id.cardSmartCoachWeather).visibility = View.GONE

        val dayStateContainer = root.findViewById<LinearLayout>(R.id.layoutSmartCoachDayStateDetails)
        dayStateContainer.removeAllViews()
        bindSmartCoachScoreSection(
            container = dayStateContainer,
            section = SmartCoachScoreSection.DAY_STATE,
            details = insight.scoreDetails
        )

        bindSmartCoachMealPlan(
            root = root,
            mealPlan = insight.mealPlan
        )

        val recommendationContainer = root.findViewById<LinearLayout>(R.id.layoutSmartCoachRecommendationDetails)
        recommendationContainer.removeAllViews()
        val activeRecommendationDetails = buildActiveMomentScoreDetails(
            insight = insight,
            activeMoment = activeMomentRecommendation
        )
        listOf(
            SmartCoachScoreSection.RECOMMENDATION,
            SmartCoachScoreSection.CONTEXT
        ).forEach { scoreSection ->
            bindSmartCoachScoreSection(
                container = recommendationContainer,
                section = scoreSection,
                details = activeRecommendationDetails
            )
        }

        val actionButton = root.findViewById<Button>(R.id.buttonSmartCoachAction)
        val hasAction = currentMomentRecommendation != null || insight.suggestedFood != null
        actionButton.visibility = if (hasAction) View.VISIBLE else View.GONE
        actionButton.text = "Выбрать приём"
    }

    private fun bindSmartCoachMealPlan(
        root: View,
        mealPlan: SmartCoachMealPlan?
    ) {
        val container = root.findViewById<LinearLayout>(R.id.layoutSmartCoachMealPlan)
        container.removeAllViews()

        if (mealPlan == null || mealPlan.sections.isEmpty()) {
            container.visibility = View.GONE
            return
        }

        syncSelectedSmartCoachOptions(mealPlan)
        container.visibility = View.VISIBLE
        container.addView(
            TextView(requireContext()).apply {
                text = mealPlan.title
                textSize = 15f
                setTextColor(Color.parseColor("#2F2433"))
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
        )
        container.addView(
            TextView(requireContext()).apply {
                text = mealPlan.subtitle
                textSize = 12f
                setTextColor(Color.parseColor("#7B6E84"))
                setLineSpacing(2f, 1.0f)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 3.dp()
                    bottomMargin = 6.dp()
                }
            }
        )

        mealPlan.sections.forEach { section ->
            container.addView(buildSmartCoachMealPlanSection(section))
        }
    }

    private fun buildSmartCoachMealPlanSection(section: SmartCoachMealPlanSection): View {
        val sectionLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8.dp(), 0, 4.dp())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val header = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        header.addView(
            TextView(requireContext()).apply {
                text = section.title
                textSize = 14f
                setTextColor(Color.parseColor("#2F2433"))
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }
        )
        header.addView(
            TextView(requireContext()).apply {
                text = "${section.targetCalories} ккал"
                textSize = 11f
                setTextColor(Color.parseColor("#7A5A00"))
                typeface = Typeface.DEFAULT_BOLD
                background = GradientDrawable().apply {
                    cornerRadius = 999f
                    setColor(Color.parseColor("#FFF3CC"))
                    setStroke(1.dp(), Color.parseColor("#EBCB67"))
                }
                setPadding(10.dp(), 5.dp(), 10.dp(), 5.dp())
            }
        )
        sectionLayout.addView(header)

        sectionLayout.addView(
            TextView(requireContext()).apply {
                text = section.subtitle
                textSize = 12f
                setTextColor(Color.parseColor("#7B6E84"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 2.dp()
                    bottomMargin = 4.dp()
                }
            }
        )

        val activeOption = resolveActiveSmartCoachOption(section)
        if (section.options.size > 1) {
            header.addView(
                TextView(requireContext()).apply {
                    text = "Варианты"
                    textSize = 11f
                    setTextColor(Color.parseColor("#6D46C9"))
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    background = GradientDrawable().apply {
                        cornerRadius = 999f
                        setColor(Color.parseColor("#F1E9FF"))
                        setStroke(1.dp(), Color.parseColor("#D9C6FF"))
                    }
                    setPadding(10.dp(), 5.dp(), 10.dp(), 5.dp())
                    contentDescription = "Выбрать вариант для ${section.title}"
                    setDebouncedClickListener {
                        showSmartCoachMealOptionsDialog(section)
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        marginStart = 8.dp()
                    }
                }
            )
        }
        sectionLayout.addView(
            buildSmartCoachMealPlanOptionCard(
                section = section,
                option = activeOption,
                canOpenOptions = section.options.size > 1
            )
        )
        return sectionLayout
    }

    private fun buildSmartCoachMealPlanOptionCard(
        section: SmartCoachMealPlanSection,
        option: SmartCoachMealPlanOption,
        canOpenOptions: Boolean = false
    ): View {
        val card = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12.dp(), 10.dp(), 12.dp(), 10.dp())
            background = GradientDrawable().apply {
                cornerRadius = 12.dp().toFloat()
                setColor(Color.parseColor("#FFFDF8"))
                setStroke(1.dp(), Color.parseColor("#E7D8F4"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 6.dp()
            }
            if (canOpenOptions) {
                isClickable = true
                isFocusable = true
                setDebouncedClickListener {
                    showSmartCoachMealOptionsDialog(section)
                }
            }
        }

        val header = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(
            TextView(requireContext()).apply {
                text = option.title
                textSize = 13f
                setTextColor(Color.parseColor("#2F2433"))
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }
        )
        header.addView(
            TextView(requireContext()).apply {
                text = formatScore(option.score)
                textSize = 12f
                setTextColor(Color.parseColor("#6D46C9"))
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    cornerRadius = 999f
                    setColor(Color.parseColor("#F1E9FF"))
                    setStroke(1.dp(), Color.parseColor("#D9C6FF"))
                }
                setPadding(8.dp(), 4.dp(), 8.dp(), 4.dp())
            }
        )
        header.addView(
            TextView(requireContext()).apply {
                text = "?"
                textSize = 11f
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#7E57D9"))
                typeface = Typeface.DEFAULT_BOLD
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#FFF8DB"))
                    setStroke(1.dp(), Color.parseColor("#EBCB67"))
                }
                contentDescription = "Почему выбран вариант: ${option.title}"
                setDebouncedClickListener {
                    showSmartCoachMealPlanHint(this, option)
                }
                layoutParams = LinearLayout.LayoutParams(
                    22.dp(),
                    22.dp()
                ).apply {
                    marginStart = 8.dp()
                }
            }
        )
        card.addView(header)

        card.addView(
            TextView(requireContext()).apply {
                text = option.subtitle
                textSize = 12f
                setTextColor(Color.parseColor("#6B5B73"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 3.dp()
                }
            }
        )

        option.items.forEach { item ->
            val key = SmartCoachMealKey(section.mealType, item.food.id)
            val progress = currentSmartCoachMealPlanProgress[key]
            val consumedGrams = progress?.consumedGrams ?: 0
            val isCompleted = item.quantityInGrams <= 0 && consumedGrams > 0
            card.addView(
                TextView(requireContext()).apply {
                    text = when {
                        isCompleted -> "✓ ${item.food.name}: добавлено"
                        consumedGrams > 0 -> "↻ ${item.food.name}, осталось ${item.quantityInGrams} г"
                        else -> "+ ${item.food.name}, ${item.quantityInGrams} г"
                    }
                    textSize = 12f
                    setTextColor(
                        Color.parseColor(
                            if (isCompleted) "#51631F" else "#5F3FB1"
                        )
                    )
                    typeface = Typeface.DEFAULT_BOLD
                    background = GradientDrawable().apply {
                        cornerRadius = 10.dp().toFloat()
                        setColor(Color.parseColor(if (isCompleted) "#EEF8D9" else "#F7F0FF"))
                        setStroke(1.dp(), Color.parseColor(if (isCompleted) "#CFE7A3" else "#E5D6FF"))
                    }
                    setPadding(10.dp(), 7.dp(), 10.dp(), 7.dp())
                    if (!isCompleted) {
                        setDebouncedClickListener {
                            openRecommendedProduct(
                                foodId = item.food.id,
                                suggestedMealType = section.mealType,
                                initialQuantityInGrams = item.quantityInGrams.toDouble()
                            )
                        }
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = 7.dp()
                    }
                }
            )
        }

        val isOptionCompleted = option.items.isNotEmpty() && option.items.all { item ->
            val key = SmartCoachMealKey(section.mealType, item.food.id)
            item.quantityInGrams <= 0 && (currentSmartCoachMealPlanProgress[key]?.consumedGrams ?: 0) > 0
        }
        card.addView(
            TextView(requireContext()).apply {
                text = if (isOptionCompleted) {
                    "Выполнено"
                } else {
                    "${option.calories} ккал | ${option.protein} Б / ${option.fat} Ж / ${option.carbs} У"
                }
                textSize = 11f
                setTextColor(Color.parseColor("#51631F"))
                typeface = Typeface.DEFAULT_BOLD
                background = GradientDrawable().apply {
                    cornerRadius = 999f
                    setColor(Color.parseColor("#EEF8D9"))
                    setStroke(1.dp(), Color.parseColor("#CFE7A3"))
                }
                setPadding(10.dp(), 6.dp(), 10.dp(), 6.dp())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 9.dp()
                }
            }
        )
        if (canOpenOptions) {
            card.addView(
                TextView(requireContext()).apply {
                    text = "Нажмите на карточку, чтобы сравнить варианты"
                    textSize = 11f
                    setTextColor(Color.parseColor("#8A7B93"))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = 7.dp()
                    }
                }
            )
        }
        return card
    }

    private fun syncSelectedSmartCoachOptions(mealPlan: SmartCoachMealPlan) {
        val sectionsByType = mealPlan.sections.associateBy { it.mealType }
        selectedSmartCoachOptionIds.entries.removeAll { (mealType, optionId) ->
            sectionsByType[mealType]?.options?.none { it.id == optionId } ?: true
        }
    }

    private fun resolveActiveSmartCoachOption(
        section: SmartCoachMealPlanSection
    ): SmartCoachMealPlanOption {
        val selectedId = selectedSmartCoachOptionIds[section.mealType]
        return section.options.firstOrNull { it.id == selectedId }
            ?: section.options.first()
    }

    private fun showSmartCoachMealOptionsDialog(section: SmartCoachMealPlanSection) {
        if (!canShowTransientUi()) return
        val dialog = Dialog(requireContext()).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
        }
        val activeOption = resolveActiveSmartCoachOption(section)
        val panel = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18.dp(), 18.dp(), 18.dp(), 18.dp())
            background = GradientDrawable().apply {
                cornerRadius = 28.dp().toFloat()
                setColor(Color.parseColor("#FFFDF8"))
                setStroke(1.dp(), Color.parseColor("#E7D8F4"))
            }
        }
        val dragHandle = View(requireContext()).apply {
            background = GradientDrawable().apply {
                cornerRadius = 999f
                setColor(Color.parseColor("#D8C8F2"))
            }
            layoutParams = LinearLayout.LayoutParams(52.dp(), 5.dp()).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = 14.dp()
            }
        }
        panel.addView(dragHandle)
        val titleRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(
            TextView(requireContext()).apply {
                text = "Выбрать вариант"
                textSize = 22f
                setTextColor(Color.parseColor("#2F2433"))
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }
        )
        titleRow.addView(
            TextView(requireContext()).apply {
                text = "×"
                textSize = 26f
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#B395E9"))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#F5ECFF"))
                    setStroke(1.dp(), Color.parseColor("#E3D4FA"))
                }
                setDebouncedClickListener { dialog.dismiss() }
                layoutParams = LinearLayout.LayoutParams(42.dp(), 42.dp())
            }
        )
        panel.addView(titleRow)
        panel.addView(
            TextView(requireContext()).apply {
                text = "${section.title}: выберите один вариант, который станет активным на главном экране."
                textSize = 13f
                setTextColor(Color.parseColor("#7B6E84"))
                setLineSpacing(2f, 1.0f)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 6.dp()
                    bottomMargin = 10.dp()
                }
            }
        )

        val optionsContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }
        section.options.forEach { option ->
            val isActive = option.id == activeOption.id
            optionsContainer.addView(
                buildSmartCoachMealOptionChoiceCard(
                    option = option,
                    isActive = isActive,
                    onSelected = {
                        selectedSmartCoachOptionIds[section.mealType] = option.id
                        dialog.dismiss()
                        view?.let { bindSmartCoach(it, currentSmartCoachInsight) }
                    }
                )
            )
        }

        panel.addView(
            ScrollView(requireContext()).apply {
                isFillViewport = false
                addView(optionsContainer)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
        )

        val root = FrameLayout(requireContext()).apply {
            setPadding(10.dp(), 0, 10.dp(), 10.dp())
            addView(panel)
        }

        dialog.setContentView(root)
        dialog.setOnShowListener {
            dialog.window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
                setGravity(Gravity.BOTTOM)
                addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                setDimAmount(0.32f)
            }
        }
        if (!canShowTransientUi()) return
        dialog.show()
    }

    private fun buildSmartCoachMealOptionChoiceCard(
        option: SmartCoachMealPlanOption,
        isActive: Boolean,
        onSelected: () -> Unit
    ): View {
        val card = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12.dp(), 11.dp(), 12.dp(), 11.dp())
            background = GradientDrawable().apply {
                cornerRadius = 16.dp().toFloat()
                setColor(Color.parseColor(if (isActive) "#FCF8FF" else "#FFFDF8"))
                setStroke(
                    if (isActive) 2.dp() else 1.dp(),
                    Color.parseColor(if (isActive) "#BFA5F4" else "#E7D8F4")
                )
            }
            setDebouncedClickListener { onSelected() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 8.dp()
            }
        }
        val header = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(
            TextView(requireContext()).apply {
                text = option.title
                textSize = 14f
                setTextColor(Color.parseColor("#2F2433"))
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }
        )
        if (isActive) {
            header.addView(
                TextView(requireContext()).apply {
                    text = "Активно"
                    textSize = 11f
                    setTextColor(Color.parseColor("#51631F"))
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    background = GradientDrawable().apply {
                        cornerRadius = 999f
                        setColor(Color.parseColor("#EEF8D9"))
                        setStroke(1.dp(), Color.parseColor("#CFE7A3"))
                    }
                    setPadding(8.dp(), 4.dp(), 8.dp(), 4.dp())
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        marginStart = 8.dp()
                    }
                }
            )
        }
        header.addView(
            TextView(requireContext()).apply {
                text = formatScore(option.score)
                textSize = 12f
                setTextColor(Color.parseColor("#6D46C9"))
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    cornerRadius = 999f
                    setColor(Color.parseColor("#F1E9FF"))
                    setStroke(1.dp(), Color.parseColor("#D9C6FF"))
                }
                setPadding(8.dp(), 4.dp(), 8.dp(), 4.dp())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = 8.dp()
                }
            }
        )
        card.addView(header)
        card.addView(
            TextView(requireContext()).apply {
                text = option.subtitle
                textSize = 12f
                setTextColor(Color.parseColor("#6B5B73"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 5.dp()
                }
            }
        )
        option.items.forEach { item ->
            card.addView(
                TextView(requireContext()).apply {
                    text = "${item.food.name}, ${item.quantityInGrams} г"
                    textSize = 12f
                    setTextColor(Color.parseColor("#5F3FB1"))
                    typeface = Typeface.DEFAULT_BOLD
                    background = GradientDrawable().apply {
                        cornerRadius = 10.dp().toFloat()
                        setColor(Color.parseColor("#F7F0FF"))
                        setStroke(1.dp(), Color.parseColor("#E5D6FF"))
                    }
                    setPadding(10.dp(), 7.dp(), 10.dp(), 7.dp())
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = 7.dp()
                    }
                }
            )
        }
        card.addView(
            TextView(requireContext()).apply {
                text = "${option.calories} ккал | ${option.protein} Б / ${option.fat} Ж / ${option.carbs} У"
                textSize = 11f
                setTextColor(Color.parseColor("#51631F"))
                typeface = Typeface.DEFAULT_BOLD
                background = GradientDrawable().apply {
                    cornerRadius = 999f
                    setColor(Color.parseColor("#EEF8D9"))
                    setStroke(1.dp(), Color.parseColor("#CFE7A3"))
                }
                setPadding(10.dp(), 6.dp(), 10.dp(), 6.dp())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 9.dp()
                }
            }
        )
        card.addView(
            TextView(requireContext()).apply {
                text = if (isActive) "Этот вариант сейчас показан в плане" else "Сделать активным"
                textSize = 12f
                setTextColor(Color.parseColor(if (isActive) "#7B6E84" else "#6D46C9"))
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    cornerRadius = 999f
                    setColor(Color.parseColor(if (isActive) "#F6F1FA" else "#F1E9FF"))
                    setStroke(1.dp(), Color.parseColor(if (isActive) "#E7D8F4" else "#D9C6FF"))
                }
                setPadding(12.dp(), 7.dp(), 12.dp(), 7.dp())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 10.dp()
                }
            }
        )
        return card
    }

    private fun showSmartCoachMealPlanHint(
        anchor: View,
        option: SmartCoachMealPlanOption
    ) {
        val popupWidth = (resources.displayMetrics.widthPixels - 48.dp()).coerceAtMost(330.dp())
        val anchorLocation = IntArray(2)
        anchor.getLocationOnScreen(anchorLocation)
        val rootWidth = anchor.rootView.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val x = (anchorLocation[0] + anchor.width / 2 - popupWidth + 24.dp())
            .coerceIn(12.dp(), (rootWidth - popupWidth - 12.dp()).coerceAtLeast(12.dp()))
        val tailCenterX = (anchorLocation[0] + anchor.width / 2 - x)
            .coerceIn(24.dp(), popupWidth - 24.dp())
        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp(), 14.dp(), 16.dp(), 14.dp())
            background = GradientDrawable().apply {
                cornerRadius = 18.dp().toFloat()
                setColor(Color.parseColor("#FFFDF8"))
                setStroke(1.dp(), Color.parseColor("#E7D8F4"))
            }
            layoutParams = LinearLayout.LayoutParams(
                popupWidth,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val header = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(
            TextView(requireContext()).apply {
                text = option.title
                textSize = 15f
                setTextColor(Color.parseColor("#2F2433"))
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }
        )
        header.addView(
            TextView(requireContext()).apply {
                text = formatScore(option.score)
                textSize = 12f
                setTextColor(Color.parseColor("#6D46C9"))
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    cornerRadius = 999f
                    setColor(Color.parseColor("#F1E9FF"))
                    setStroke(1.dp(), Color.parseColor("#D9C6FF"))
                }
                setPadding(10.dp(), 5.dp(), 10.dp(), 5.dp())
            }
        )
        content.addView(header)
        content.addView(
            TextView(requireContext()).apply {
                text = option.reason.toSentenceCase()
                textSize = 13f
                setTextColor(Color.parseColor("#5F5367"))
                setLineSpacing(3f, 1.0f)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 10.dp()
                }
            }
        )

        val shell = buildSmartCoachTooltipShell(
            popupWidth = popupWidth,
            tailCenterX = tailCenterX,
            bubble = content
        )
        val popup = PopupWindow(
            shell,
            popupWidth,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isOutsideTouchable = true
            elevation = 10.dp().toFloat()
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
        val y = anchorLocation[1] + anchor.height + 4.dp()
        popup.showAtLocation(anchor.rootView, Gravity.TOP or Gravity.START, x, y)
    }

    private fun bindSmartCoachScoreSection(
        container: LinearLayout,
        section: SmartCoachScoreSection,
        details: List<SmartCoachScoreDetail>
    ) {
        val sectionDetails = details.filter { it.section == section }
        if (sectionDetails.isEmpty()) return

        container.addView(buildSmartCoachScoreSectionHeader(section))
        sectionDetails.forEach { detail ->
            container.addView(buildSmartCoachScoreRow(detail))
        }
    }

    private fun buildSmartCoachScoreSectionHeader(section: SmartCoachScoreSection): View {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 12.dp(), 0, 4.dp())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        container.addView(
            TextView(requireContext()).apply {
                text = when (section) {
                    SmartCoachScoreSection.DAY_STATE -> "Состояние дня"
                    SmartCoachScoreSection.RECOMMENDATION -> "Оценка рекомендации"
                    SmartCoachScoreSection.CONTEXT -> "Контекст и ограничения"
                }
                textSize = 14f
                setTextColor(android.graphics.Color.parseColor("#2F2433"))
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
        )
        container.addView(
            TextView(requireContext()).apply {
                text = when (section) {
                    SmartCoachScoreSection.DAY_STATE ->
                        "Фактическая оценка уже добавленных записей и прогноза дня."
                    SmartCoachScoreSection.RECOMMENDATION ->
                        "Критерии подбора продукта к текущей ситуации."
                    SmartCoachScoreSection.CONTEXT ->
                        "Дополнительные факторы, которые влияют на итоговый совет."
                }
                textSize = 12f
                setTextColor(android.graphics.Color.parseColor("#7B6E84"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 2.dp()
                }
            }
        )
        return container
    }

    private fun buildSmartCoachScoreRow(detail: SmartCoachScoreDetail): View {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(10.dp(), 7.dp(), 10.dp(), 7.dp())
            background = GradientDrawable().apply {
                cornerRadius = 8.dp().toFloat()
                setColor(Color.parseColor("#FCF8FF"))
                setStroke(1.dp(), Color.parseColor("#EDE3F4"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 3.dp()
                bottomMargin = 3.dp()
            }
        }
        val header = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(
            TextView(requireContext()).apply {
                text = detail.label
                textSize = 13f
                setTextColor(Color.parseColor("#2F2433"))
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }
        )
        header.addView(
            TextView(requireContext()).apply {
                text = formatScore(detail.value)
                textSize = 13f
                setTextColor(Color.parseColor("#7A5A00"))
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
        )
        header.addView(
            TextView(requireContext()).apply {
                text = "?"
                textSize = 11f
                gravity = Gravity.CENTER
                minWidth = 22.dp()
                minHeight = 22.dp()
                setTextColor(Color.parseColor("#7E57D9"))
                typeface = Typeface.DEFAULT_BOLD
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#FFF8DB"))
                    setStroke(1.dp(), Color.parseColor("#EBCB67"))
                }
                contentDescription = "Почему так: ${detail.label}"
                setDebouncedClickListener {
                    showSmartCoachScoreHint(this, detail)
                }
                layoutParams = LinearLayout.LayoutParams(
                    22.dp(),
                    22.dp()
                ).apply {
                    marginStart = 8.dp()
                }
            }
        )
        row.addView(header)
        row.addView(
            TextView(requireContext()).apply {
                text = detail.description
                textSize = 12f
                setTextColor(Color.parseColor("#6B5B73"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 2.dp()
                }
            }
        )
        return row
    }

    private fun showSmartCoachScoreHint(
        anchor: View,
        detail: SmartCoachScoreDetail
    ) {
        val popupWidth = (resources.displayMetrics.widthPixels - 48.dp()).coerceAtMost(320.dp())
        val anchorLocation = IntArray(2)
        anchor.getLocationOnScreen(anchorLocation)
        val rootWidth = anchor.rootView.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val x = (anchorLocation[0] + anchor.width / 2 - popupWidth + 24.dp())
            .coerceIn(12.dp(), (rootWidth - popupWidth - 12.dp()).coerceAtLeast(12.dp()))
        val tailCenterX = (anchorLocation[0] + anchor.width / 2 - x)
            .coerceIn(24.dp(), popupWidth - 24.dp())
        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp(), 14.dp(), 16.dp(), 14.dp())
            background = GradientDrawable().apply {
                cornerRadius = 18.dp().toFloat()
                setColor(Color.parseColor("#FFFDF8"))
                setStroke(1.dp(), Color.parseColor("#E7D8F4"))
            }
            layoutParams = LinearLayout.LayoutParams(
                popupWidth,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val header = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(
            TextView(requireContext()).apply {
                text = detail.label
                textSize = 15f
                setTextColor(Color.parseColor("#2F2433"))
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }
        )
        header.addView(
            TextView(requireContext()).apply {
                text = formatScore(detail.value)
                textSize = 12f
                setTextColor(Color.parseColor("#6D46C9"))
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    cornerRadius = 999f
                    setColor(Color.parseColor("#F1E9FF"))
                    setStroke(1.dp(), Color.parseColor("#D9C6FF"))
                }
                setPadding(10.dp(), 5.dp(), 10.dp(), 5.dp())
            }
        )
        content.addView(header)
        content.addView(
            TextView(requireContext()).apply {
                text = buildSmartCoachScoreHintText(detail)
                textSize = 13f
                setTextColor(Color.parseColor("#5F5367"))
                setLineSpacing(3f, 1.0f)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 10.dp()
                }
            }
        )

        val shell = buildSmartCoachTooltipShell(
            popupWidth = popupWidth,
            tailCenterX = tailCenterX,
            bubble = content
        )
        val popup = PopupWindow(
            shell,
            popupWidth,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isOutsideTouchable = true
            elevation = 10.dp().toFloat()
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }

        val y = anchorLocation[1] + anchor.height + 4.dp()
        popup.showAtLocation(anchor.rootView, Gravity.TOP or Gravity.START, x, y)
    }

    private fun buildSmartCoachTooltipShell(
        popupWidth: Int,
        tailCenterX: Int,
        bubble: View
    ): FrameLayout {
        return FrameLayout(requireContext()).apply {
            clipChildren = false
            clipToPadding = false
            layoutParams = LinearLayout.LayoutParams(
                popupWidth,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            addView(
                bubble.apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = 12.dp()
                    }
                }
            )
            addView(
                View(requireContext()).apply {
                    background = TooltipTailDrawable(
                        fillColor = Color.parseColor("#FFFDF8"),
                        strokeColor = Color.parseColor("#E7D8F4"),
                        strokeWidth = 1.dp().toFloat()
                    )
                    elevation = 10.dp().toFloat()
                    layoutParams = FrameLayout.LayoutParams(
                        24.dp(),
                        14.dp()
                    ).apply {
                        leftMargin = (tailCenterX - 12.dp())
                            .coerceIn(14.dp(), (popupWidth - 38.dp()).coerceAtLeast(14.dp()))
                        topMargin = 0
                    }
                }
            )
        }
    }

    private fun buildSmartCoachScoreHintText(detail: SmartCoachScoreDetail): String {
        val formula = when (detail.label) {
            "Баланс дня" ->
                "Сравнивается прогноз дня с целевыми калориями, белками, жирами и углеводами. Чем меньше отклонение от цели, тем выше оценка."
            "Записи дня" ->
                "Пока дневник пустой, Foodiary не делает выводов по фактическому рациону. После первой записи появится расчет по реальному темпу дня."
            "Заполненность" ->
                "Оценивается, какая часть дневной нормы уже закрыта по калориям и макронутриентам."
            "Попадание в остаток" ->
                "Продукт сравнивается не с уже съеденным рационом, а с оставшейся дневной нормой. Поэтому высокий балл означает, что продукт помогает закрыть текущий дефицит КБЖУ."
            "Практичность порции" ->
                "Foodiary проверяет, можно ли получить пользу обычной порцией. Если для попадания в цель пришлось бы выбрать слишком большую или слишком маленькую граммовку, оценка снижается."
            "Уместность сейчас" ->
                "Учитываются время дня, предполагаемый прием пищи и пищевая роль продукта: например, плотный жирный продукт хуже подходит для позднего перекуса, чем для основного приема пищи."
            "Соответствие цели" ->
                "Продукт сопоставляется с выбранной целью пользователя: снижение, поддержание или набор массы. Для разных целей вес белка, калорийности, жиров и углеводов отличается."
            "Уверенность" ->
                "Оценка зависит от количества доступных данных: профиля, истории питания, избранного, пользовательских продуктов и записей за день. Чем больше данных, тем стабильнее рекомендация."
            "Привычность" ->
                "Учитывается история выбора продукта, избранное и пользовательские продукты. Это помогает советовать не случайный продукт, а вариант, близкий к привычному рациону."
            "Свежесть выбора" ->
                "Проверяется, не повторяется ли продукт слишком часто, и оценивается его роль в рационе. Так система не предлагает одно и то же без необходимости."
            "Погода" ->
                "Если есть погодный контекст, он добавляется как дополнительный фактор: температура, жара, холод или дождь могут менять приоритеты подсказок."
            "Безопасность" ->
                "Foodiary учитывает аллергены и ограничения. Если найден только предполагаемый риск, продукт не скрывается полностью, но получает штраф в оценке."
            "Близость замены" ->
                "Оценивается, насколько предложенная замена похожа по роли на исходный продукт и при этом лучше поддерживает текущую цель."
            "Данные" ->
                "Когда данных мало, система показывает предварительную оценку и ждет новые записи дневника, чтобы рекомендация стала точнее."
            else -> null
        }
        return listOfNotNull(
            detail.description.toSentenceCase(),
            formula?.toSentenceCase()
        )
            .distinct()
            .joinToString("\n\n")
    }

    private fun String.toSentenceCase(): String {
        val trimmed = trim()
        if (trimmed.isEmpty()) return trimmed
        return trimmed.replaceFirstChar { char ->
            if (char.isLowerCase()) char.titlecase(Locale("ru")) else char.toString()
        }
    }

    private fun buildReplacementDeltaText(
        calorieDelta: Int,
        proteinDelta: Int
    ): String {
        val calories = when {
            calorieDelta > 0 -> "+$calorieDelta ккал"
            calorieDelta < 0 -> "$calorieDelta ккал"
            else -> "0 ккал"
        }
        val protein = when {
            proteinDelta > 0 -> "+$proteinDelta г белка"
            proteinDelta < 0 -> "$proteinDelta г белка"
            else -> "0 г белка"
        }
        return "На 100 г: $calories, $protein"
    }

    private fun formatMacroValue(value: Double): String {
        return if (value % 1.0 == 0.0) {
            value.roundToInt().toString()
        } else {
            String.format(Locale.US, "%.1f", value)
        }
    }

    private fun formatScore(score: Int): String {
        return "${(score / 10.0).roundToInt().coerceIn(0, 10)}/10"
    }

    private fun bindWeatherRecommendation(
        root: View,
        recommendation: WeatherFoodRecommendation?,
        forceShow: Boolean = false
    ) {
        val card = root.findViewById<View>(R.id.cardQuickRecommendationPopup)
        val title = root.findViewById<TextView>(R.id.textQuickRecommendationPopupTitle)
        val name = root.findViewById<TextView>(R.id.textQuickRecommendationPopupName)
        val reason = root.findViewById<TextView>(R.id.textQuickRecommendationPopupReason)
        val button = root.findViewById<Button>(R.id.buttonQuickRecommendationPopup)

        currentWeatherRecommendation = recommendation
        refreshSmartCoach(root)
        weatherRecommendationShowJob?.cancel()
        card.animate().cancel()

        if (recommendation == null || (isWeatherRecommendationConsumedThisLaunch && !forceShow)) {
            card.visibility = View.GONE
            card.alpha = 0f
            card.scaleX = 1f
            card.scaleY = 1f
            card.translationX = 0f
            card.translationY = 0f
            return
        }

        title.text = recommendation.title
        name.text = recommendation.headline
        reason.text = recommendation.message
        button.text = recommendation.buttonText

        card.visibility = View.GONE
        card.alpha = 0f
        card.scaleX = 1f
        card.scaleY = 1f
        card.translationX = 0f
        card.translationY = 0f

        val delayMs = if (forceShow) {
            0L
        } else {
            (weatherRecommendationRevealAtMs - SystemClock.elapsedRealtime())
                .coerceAtLeast(0L)
        }

        val owner = viewLifecycleOwnerLiveData.value ?: return
        weatherRecommendationShowJob = owner.lifecycleScope.launch {
            delay(delayMs)

            if (!isAdded || view !== root) return@launch

            if (recommendation.action == WeatherRecommendationAction.OPEN_FOOD) {
                isWeatherRecommendationConsumedThisLaunch = true
            }
            card.visibility = View.VISIBLE
            card.alpha = 0f
            card.scaleX = 0.84f
            card.scaleY = 0.84f
            card.translationX = -14f
            card.translationY = 10f
            card.animate()
                .alpha(0.42f)
                .scaleX(0.93f)
                .scaleY(0.93f)
                .translationX(-6f)
                .translationY(4f)
                .setDuration(950L)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction outerReveal@{
                    if (!isAdded || view !== root || card.visibility != View.VISIBLE) return@outerReveal
                    card.animate()
                        .alpha(1f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .translationX(0f)
                        .translationY(0f)
                        .setDuration(1_150L)
                        .setInterpolator(DecelerateInterpolator())
                        .withEndAction innerReveal@{
                            if (!isAdded || view !== root || card.visibility != View.VISIBLE) return@innerReveal
                            updateWeatherRecommendationScrollAlpha(root)
                        }
                        .start()
                }
                .start()
        }
    }

    private fun updateWeatherRecommendationScrollAlpha(
        root: View,
        scrollY: Int = root.findViewById<ScrollView>(R.id.scrollDailyNutrition).scrollY
    ) {
        val card = root.findViewById<View>(R.id.cardQuickRecommendationPopup)
        if (card.visibility != View.VISIBLE) return
        val targetAlpha = if (scrollY > 36.dp()) 0.45f else 1f
        if (card.alpha != targetAlpha) {
            card.animate()
                .alpha(targetAlpha)
                .setDuration(160L)
                .start()
        }
    }

    private fun dismissWeatherRecommendation(root: View, consumeForThisLaunch: Boolean) {
        val card = root.findViewById<View>(R.id.cardQuickRecommendationPopup)
        weatherRecommendationShowJob?.cancel()
        card.animate().cancel()
        if (consumeForThisLaunch) {
            isWeatherRecommendationConsumedThisLaunch = true
        }
        card.visibility = View.GONE
        card.alpha = 0f
        card.scaleX = 1f
        card.scaleY = 1f
        card.translationX = 0f
        card.translationY = 0f
    }

    private fun resolveCurrentMealType(): MealType {
        val enabledMealTypes = mealSchedulePreferences.getEnabledMealTypes()
        fun fallback(vararg variants: MealType): MealType {
            return variants.firstOrNull { it in enabledMealTypes } ?: MealType.BREAKFAST
        }
        return when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..10 -> fallback(MealType.BREAKFAST, MealType.SNACK, MealType.LUNCH)
            in 11..14 -> fallback(MealType.LUNCH, MealType.SNACK, MealType.DINNER)
            15 -> if (MealType.AFTERNOON_SNACK in enabledMealTypes) {
                MealType.AFTERNOON_SNACK
            } else {
                fallback(MealType.SNACK, MealType.LUNCH, MealType.DINNER)
            }
            in 16..18 -> fallback(MealType.SNACK, MealType.DINNER, MealType.LUNCH)
            in 19..20 -> fallback(MealType.DINNER, MealType.SNACK, MealType.LATE_DINNER)
            in 21..23 -> if (MealType.LATE_DINNER in enabledMealTypes) {
                MealType.LATE_DINNER
            } else {
                fallback(MealType.DINNER, MealType.SNACK, MealType.BREAKFAST)
            }
            else -> fallback(MealType.BREAKFAST, MealType.SNACK, MealType.DINNER)
        }
    }

    private fun loadSelectedDay() {
        val (start, end) = getSelectedDayBounds()
        viewModel.loadDailyNutrition(start, end)
    }

    private fun isTodaySelected(): Boolean {
        return selectedDayStart == normalizeDayStart(System.currentTimeMillis())
    }

    private fun endOfDay(dayStart: Long): Long {
        return Calendar.getInstance().apply {
            timeInMillis = dayStart
            add(Calendar.DAY_OF_MONTH, 1)
        }.timeInMillis
    }

    private fun Int.dp(): Int {
        return (this * resources.displayMetrics.density).roundToInt()
    }

    private fun provideFactory(): GetDailyNutritionViewModelFactory {
        val database = AppDatabase.getInstance(requireContext())

        val foodRepository = FoodRepositoryImpl(foodDao = database.foodDao())
        val mealRepository = MealRepositoryImpl(
            mealDao = database.mealDao(),
            foodRepository = foodRepository
        )

        val getDailyNutrition = GetDailyNutritionUseCase(
            mealRepository = mealRepository,
            foodRepository = foodRepository
        )
        val getMealsForPeriod = GetMealsForPeriodUseCase(mealRepository)
        val deleteMeal = DeleteMealUseCase(mealRepository)

        return GetDailyNutritionViewModelFactory(
            getDailyNutritionUseCase = getDailyNutrition,
            getMealsForPeriodUseCase = getMealsForPeriod,
            deleteMealUseCase = deleteMeal,
            foodRepository = foodRepository
        )
    }
}

private class TooltipTailDrawable(
    fillColor: Int,
    strokeColor: Int,
    strokeWidth: Float
) : Drawable() {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = fillColor
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        this.strokeWidth = strokeWidth
        color = strokeColor
    }

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        val path = Path().apply {
            moveTo(bounds.exactCenterX(), bounds.top.toFloat())
            lineTo(bounds.right.toFloat(), bounds.bottom.toFloat())
            lineTo(bounds.left.toFloat(), bounds.bottom.toFloat())
            close()
        }
        canvas.drawPath(path, fillPaint)
        canvas.drawPath(path, strokePaint)
    }

    override fun setAlpha(alpha: Int) {
        fillPaint.alpha = alpha
        strokePaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
        fillPaint.colorFilter = colorFilter
        strokePaint.colorFilter = colorFilter
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
