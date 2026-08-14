package com.example.foodiary.data.local.preferences

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.foodiary.domain.model.MealType
import com.example.foodiary.domain.model.NutritionTargets
import com.example.foodiary.domain.model.ProteinGoalBasis
import com.example.foodiary.testing.AndroidTestStateHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar

@RunWith(AndroidJUnit4::class)
class PreferencesIntegrationTest {

    private val context by lazy { ApplicationProvider.getApplicationContext<android.content.Context>() }

    @Before
    fun setUp() {
        AndroidTestStateHelper.resetAll()
    }

    @After
    fun tearDown() {
        AndroidTestStateHelper.resetAll()
    }

    @Test
    fun local_account_preferences_store_optional_profile() {
        val prefs = LocalAccountPreferences(context)

        assertFalse(prefs.isAccountReady())
        prefs.saveAccount(email = "user@example.com", displayName = "Аня")

        val account = prefs.getAccount()
        assertTrue(prefs.isAccountReady())
        assertNotNull(account)
        assertEquals("user@example.com", account?.email)
        assertEquals("Аня", account?.displayName)
    }

    @Test
    fun nutrition_targets_override_applies_and_clears() {
        val prefs = NutritionTargetsPreferences(context)
        val base = NutritionTargets(
            maintenanceCalories = 2200,
            targetCalories = 2200,
            proteinGrams = 120,
            fatGrams = 73,
            carbsGrams = 250,
            proteinGoalBasis = ProteinGoalBasis.TOTAL_BODY_WEIGHT,
            proteinReferenceWeightKg = 75.0
        )

        prefs.saveOverride(
            NutritionTargetsPreferences.NutritionOverride(
                calories = 2400,
                proteinPercent = 30,
                fatPercent = 25,
                carbsPercent = 45
            )
        )
        val overridden = prefs.apply(base)
        assertEquals(2400, overridden.targetCalories)
        assertEquals(180, overridden.proteinGrams)
        assertEquals(67, overridden.fatGrams)
        assertEquals(270, overridden.carbsGrams)

        prefs.clearOverride()
        val reset = prefs.apply(base)
        assertEquals(base, reset)
    }

    @Test
    fun meal_schedule_preferences_build_consistent_calorie_targets() {
        val prefs = MealSchedulePreferences(context)
        prefs.saveMealSlots(
            listOf(
                MealSchedulePreferences.MealSlotSettings(MealType.BREAKFAST, enabled = true, sharePercent = 25),
                MealSchedulePreferences.MealSlotSettings(MealType.LUNCH, enabled = true, sharePercent = 40),
                MealSchedulePreferences.MealSlotSettings(MealType.DINNER, enabled = true, sharePercent = 25),
                MealSchedulePreferences.MealSlotSettings(MealType.SNACK, enabled = true, sharePercent = 10),
                MealSchedulePreferences.MealSlotSettings(MealType.AFTERNOON_SNACK, enabled = false, sharePercent = 0),
                MealSchedulePreferences.MealSlotSettings(MealType.LATE_DINNER, enabled = false, sharePercent = 0)
            )
        )

        val calorieTargets = prefs.buildCalorieTargets(totalCalories = 2600)

        assertEquals(4, prefs.getEnabledMealTypes().size)
        assertEquals(2600, calorieTargets.values.sum())
        assertEquals(650, calorieTargets[MealType.BREAKFAST])
        assertEquals(1040, calorieTargets[MealType.LUNCH])
        assertEquals(650, calorieTargets[MealType.DINNER])
        assertEquals(260, calorieTargets[MealType.SNACK])
    }

    @Test
    fun reminder_and_ui_preferences_persist_user_settings() {
        val reminderPrefs = ReminderPreferences(context)
        val uiPrefs = UiPreferences(context)

        reminderPrefs.setMealReminder(
            MealType.BREAKFAST,
            ReminderPreferences.DailyReminder(enabled = true, hour = 8, minute = 15)
        )
        reminderPrefs.setWaterReminderCount(2)
        reminderPrefs.setWaterReminder(
            1,
            ReminderPreferences.DailyReminder(enabled = true, hour = 16, minute = 45)
        )
        reminderPrefs.setActivityReminder(
            ReminderPreferences.DailyReminder(enabled = true, hour = 19, minute = 0)
        )
        reminderPrefs.setWeightReminder(
            ReminderPreferences.WeeklyReminder(
                enabled = true,
                dayOfWeek = Calendar.FRIDAY,
                hour = 7,
                minute = 30
            )
        )
        uiPrefs.setRecommendationPopupEnabled(false)
        uiPrefs.setRecommendationSectionEnabled(false)

        assertTrue(reminderPrefs.getMealReminder(MealType.BREAKFAST).enabled)
        assertEquals(2, reminderPrefs.getWaterReminderCount())
        assertEquals(16, reminderPrefs.getWaterReminder(1).hour)
        assertTrue(reminderPrefs.getActivityReminder().enabled)
        assertEquals(Calendar.FRIDAY, reminderPrefs.getWeightReminder().dayOfWeek)
        assertFalse(uiPrefs.isRecommendationPopupEnabled())
        assertFalse(uiPrefs.isRecommendationSectionEnabled())
    }
}
