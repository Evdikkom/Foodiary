package com.example.foodiary.presentation.activity

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.example.foodiary.R
import com.example.foodiary.data.local.database.AppDatabase
import com.example.foodiary.data.local.preferences.LocalAccountPreferences
import com.example.foodiary.data.repository.UserRepositoryImpl
import com.example.foodiary.domain.model.MealType
import com.example.foodiary.presentation.dialog.MealTypePickerDialogFragment
import com.example.foodiary.presentation.fragment.AccountProfileFragment
import com.example.foodiary.presentation.fragment.AddMealFragment
import com.example.foodiary.presentation.fragment.BarcodeScannerFragment
import com.example.foodiary.presentation.fragment.CreateCustomFoodFragment
import com.example.foodiary.presentation.fragment.CreateRecipeFragment
import com.example.foodiary.presentation.fragment.DailyNutritionFragment
import com.example.foodiary.presentation.fragment.DailyTargetsSettingsFragment
import com.example.foodiary.presentation.fragment.FoodPhotoCameraFragment
import com.example.foodiary.presentation.fragment.FoodPhotoSelectionFragment
import com.example.foodiary.presentation.fragment.GoalSettingsFragment
import com.example.foodiary.presentation.fragment.MealScheduleSettingsFragment
import com.example.foodiary.presentation.fragment.NotificationPreferencesFragment
import com.example.foodiary.presentation.fragment.NutritionAnalyticsFragment
import com.example.foodiary.presentation.fragment.OnboardingFragment
import com.example.foodiary.presentation.fragment.ProductConfigFragment
import com.example.foodiary.presentation.fragment.ProfileHubFragment
import com.example.foodiary.presentation.fragment.ProfileInfoFragment
import com.example.foodiary.presentation.fragment.ProfileSettingsFragment
import com.example.foodiary.presentation.fragment.RecipesHubFragment
import com.example.foodiary.presentation.fragment.ServiceSettingsFragment
import com.example.foodiary.presentation.fragment.SupportFragment
import com.example.foodiary.presentation.notification.ReminderScheduler
import com.example.foodiary.presentation.util.FoodiaryMotionPattern
import com.example.foodiary.presentation.util.prepareFoodiaryTransition
import com.example.foodiary.presentation.util.setDebouncedClickListener
import kotlinx.coroutines.launch
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    enum class MainTab {
        DIARY,
        ANALYTICS,
        RECIPES,
        PROFILE
    }

    companion object {
        private const val STATE_LAST_DIARY_DAY = "state_last_diary_day"
    }

    private lateinit var navigationRoot: View
    private lateinit var navDiary: LinearLayout
    private lateinit var navAnalytics: LinearLayout
    private lateinit var navRecipes: LinearLayout
    private lateinit var navProfile: LinearLayout
    private lateinit var iconDiary: ImageView
    private lateinit var iconAnalytics: ImageView
    private lateinit var iconRecipes: ImageView
    private lateinit var iconProfile: ImageView
    private lateinit var textDiary: TextView
    private lateinit var textAnalytics: TextView
    private lateinit var textRecipes: TextView
    private lateinit var textProfile: TextView

    private val activeTextColor = Color.parseColor("#7C5CE4")
    private val inactiveTextColor = Color.parseColor("#8D84A0")
    private var lastDiarySelectedDayStart: Long = normalizeDayStart(System.currentTimeMillis())

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Foodiary)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        lastDiarySelectedDayStart = savedInstanceState?.getLong(STATE_LAST_DIARY_DAY)
            ?: normalizeDayStart(System.currentTimeMillis())

        bindNavigationViews()
        setupBottomNavigation()
        supportFragmentManager.addOnBackStackChangedListener {
            updateChromeForCurrentFragment()
        }
        supportFragmentManager.registerFragmentLifecycleCallbacks(
            object : FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
                    if (fm == supportFragmentManager && supportFragmentManager.findFragmentById(R.id.fragmentContainer) === f) {
                        updateChromeForCurrentFragment()
                    }
                }
            },
            false
        )

        if (savedInstanceState == null) {
            val database = AppDatabase.getInstance(this)
            val userRepository = UserRepositoryImpl(
                userDao = database.userDao(),
                allergenDao = database.allergenDao(),
                userRestrictionDao = database.userRestrictionDao()
            )

            lifecycleScope.launch {
                val startFragment = when {
                    !LocalAccountPreferences(this@MainActivity).isAccountReady() -> {
                        AccountProfileFragment.newSetupInstance()
                    }
                    userRepository.getCurrentUser() == null -> {
                        OnboardingFragment.newInstance()
                    }
                    else -> {
                        DailyNutritionFragment.newInstance(lastDiarySelectedDayStart)
                    }
                }

                supportFragmentManager.beginTransaction()
                    .setReorderingAllowed(true)
                    .replace(R.id.fragmentContainer, startFragment)
                    .runOnCommit {
                        updateChromeForCurrentFragment()
                        if (startFragment is DailyNutritionFragment) {
                            handleReminderIntent(intent)
                        }
                    }
                    .commit()
            }
        } else {
            updateChromeForCurrentFragment()
            handleReminderIntent(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleReminderIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(STATE_LAST_DIARY_DAY, lastDiarySelectedDayStart)
    }

    fun updateDiarySelectedDay(dayStart: Long) {
        lastDiarySelectedDayStart = normalizeDayStart(dayStart)
    }

    private fun bindNavigationViews() {
        navigationRoot = findViewById(R.id.navigationRoot)
        navDiary = findViewById(R.id.navDiary)
        navAnalytics = findViewById(R.id.navAnalytics)
        navRecipes = findViewById(R.id.navRecipes)
        navProfile = findViewById(R.id.navProfile)
        iconDiary = findViewById(R.id.iconDiary)
        iconAnalytics = findViewById(R.id.iconAnalytics)
        iconRecipes = findViewById(R.id.iconRecipes)
        iconProfile = findViewById(R.id.iconProfile)
        textDiary = findViewById(R.id.textDiary)
        textAnalytics = findViewById(R.id.textAnalytics)
        textRecipes = findViewById(R.id.textRecipes)
        textProfile = findViewById(R.id.textProfile)
    }

    private fun setupBottomNavigation() {
        navDiary.setDebouncedClickListener {
            navigateToRoot(MainTab.DIARY)
        }
        navAnalytics.setDebouncedClickListener {
            navigateToRoot(MainTab.ANALYTICS)
        }
        navRecipes.setDebouncedClickListener {
            navigateToRoot(MainTab.RECIPES)
        }
        navProfile.setDebouncedClickListener {
            navigateToRoot(MainTab.PROFILE)
        }
        findViewById<View>(R.id.buttonGlobalAdd).setDebouncedClickListener {
            if (!navigationRoot.isVisible) return@setDebouncedClickListener
            MealTypePickerDialogFragment.newInstance(lastDiarySelectedDayStart)
                .show(supportFragmentManager, "meal_type_picker")
        }
    }

    fun navigateToRoot(tab: MainTab) {
        val current = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
        val target = when (tab) {
            MainTab.DIARY -> DailyNutritionFragment.newInstance(lastDiarySelectedDayStart)
            MainTab.ANALYTICS -> NutritionAnalyticsFragment()
            MainTab.RECIPES -> RecipesHubFragment()
            MainTab.PROFILE -> ProfileHubFragment.newInstance(lastDiarySelectedDayStart)
        }

        val isAlreadyRoot = supportFragmentManager.backStackEntryCount == 0 &&
            current?.javaClass == target.javaClass
        if (isAlreadyRoot) {
            applySelectedTab(tab)
            return
        }

        supportFragmentManager.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        prepareFoodiaryTransition(current, target, FoodiaryMotionPattern.ROOT_FADE_THROUGH)
        supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .replace(R.id.fragmentContainer, target)
            .commit()
        applySelectedTab(tab)
    }

    private fun handleReminderIntent(intent: Intent?) {
        val reminderType = intent?.getStringExtra(ReminderScheduler.EXTRA_TYPE) ?: return
        intent.removeExtra(ReminderScheduler.EXTRA_TYPE)

        val current = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
        if (current is OnboardingFragment || current is AccountProfileFragment && current.isSetupMode()) {
            return
        }

        val todayStart = normalizeDayStart(System.currentTimeMillis())
        updateDiarySelectedDay(todayStart)

        val mealType = if (reminderType.startsWith("meal_")) {
            runCatching { MealType.valueOf(reminderType.removePrefix("meal_")) }.getOrNull()
        } else {
            null
        }

        if (mealType == null) {
            navigateToRoot(MainTab.DIARY)
            return
        }

        supportFragmentManager.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        val diaryFragment = DailyNutritionFragment.newInstance(todayStart)
        prepareFoodiaryTransition(current, diaryFragment, FoodiaryMotionPattern.ROOT_FADE_THROUGH)
        supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .replace(R.id.fragmentContainer, diaryFragment)
            .commit()
        supportFragmentManager.executePendingTransactions()
        val addMealFragment = AddMealFragment.newInstance(mealType, todayStart)
        prepareFoodiaryTransition(
            supportFragmentManager.findFragmentById(R.id.fragmentContainer),
            addMealFragment,
            FoodiaryMotionPattern.FORWARD_AXIS_X
        )
        supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .replace(R.id.fragmentContainer, addMealFragment)
            .addToBackStack(null)
            .commit()
        supportFragmentManager.executePendingTransactions()
        applySelectedTab(MainTab.DIARY)
    }

    private fun updateChromeForCurrentFragment() {
        val fragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
        val showBottomNav = when {
            fragment == null -> false
            fragment is OnboardingFragment -> false
            fragment is AccountProfileFragment && fragment.isSetupMode() -> false
            fragment is AddMealFragment -> false
            fragment is BarcodeScannerFragment -> false
            fragment is ProductConfigFragment -> false
            fragment is FoodPhotoSelectionFragment -> false
            fragment is FoodPhotoCameraFragment -> false
            fragment is CreateRecipeFragment -> false
            fragment is CreateCustomFoodFragment -> false
            fragment is ProfileInfoFragment -> false
            fragment is NotificationPreferencesFragment -> false
            fragment is ProfileSettingsFragment -> false
            fragment is GoalSettingsFragment -> false
            fragment is DailyTargetsSettingsFragment -> false
            fragment is MealScheduleSettingsFragment -> false
            fragment is ServiceSettingsFragment -> false
            fragment is SupportFragment -> false
            else -> true
        }
        navigationRoot.isVisible = showBottomNav
        if (!showBottomNav) return

        val tab = when (fragment) {
            is NutritionAnalyticsFragment -> MainTab.ANALYTICS
            is RecipesHubFragment,
            is CreateRecipeFragment,
            is CreateCustomFoodFragment -> MainTab.RECIPES
            is ProfileHubFragment,
            is ProfileInfoFragment,
            is AccountProfileFragment,
            is NotificationPreferencesFragment,
            is ProfileSettingsFragment,
            is GoalSettingsFragment,
            is DailyTargetsSettingsFragment,
            is MealScheduleSettingsFragment,
            is ServiceSettingsFragment,
            is SupportFragment -> MainTab.PROFILE
            else -> MainTab.DIARY
        }
        applySelectedTab(tab)
    }

    private fun applySelectedTab(selected: MainTab) {
        styleNavItem(navDiary, iconDiary, textDiary, selected == MainTab.DIARY)
        styleNavItem(navAnalytics, iconAnalytics, textAnalytics, selected == MainTab.ANALYTICS)
        styleNavItem(navRecipes, iconRecipes, textRecipes, selected == MainTab.RECIPES)
        styleNavItem(navProfile, iconProfile, textProfile, selected == MainTab.PROFILE)
    }

    private fun styleNavItem(
        container: LinearLayout,
        icon: ImageView,
        label: TextView,
        active: Boolean
    ) {
        val tint = if (active) activeTextColor else inactiveTextColor
        container.background = if (active) {
            getDrawable(R.drawable.bg_bottom_nav_item_active)
        } else {
            null
        }
        icon.imageTintList = ColorStateList.valueOf(tint)
        label.setTextColor(tint)
    }

    private fun normalizeDayStart(timestamp: Long): Long {
        return Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
