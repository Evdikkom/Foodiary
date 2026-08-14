package com.example.foodiary.presentation.flow

import android.widget.EditText
import androidx.appcompat.widget.SwitchCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.foodiary.R
import com.example.foodiary.data.local.database.AppDatabase
import com.example.foodiary.data.local.preferences.NutritionTargetsPreferences
import com.example.foodiary.data.local.preferences.UiPreferences
import com.example.foodiary.data.model.UserGoal
import com.example.foodiary.presentation.activity.MainActivity
import com.example.foodiary.presentation.fragment.AccountProfileFragment
import com.example.foodiary.presentation.fragment.DailyTargetsSettingsFragment
import com.example.foodiary.presentation.fragment.GoalSettingsFragment
import com.example.foodiary.presentation.fragment.MealScheduleSettingsFragment
import com.example.foodiary.presentation.fragment.NotificationPreferencesFragment
import com.example.foodiary.presentation.fragment.ProfileHubFragment
import com.example.foodiary.presentation.fragment.ProfileSettingsFragment
import com.example.foodiary.presentation.fragment.ServiceSettingsFragment
import com.example.foodiary.presentation.fragment.SupportFragment
import com.example.foodiary.testing.AndroidTestStateHelper
import com.example.foodiary.testing.AndroidUiTestHelper
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileHubScenariosTest {

    @Before
    fun setUp() {
        AndroidTestStateHelper.resetAll()
        AndroidTestStateHelper.saveLocalAccount()
        AndroidTestStateHelper.saveUser()
    }

    @After
    fun tearDown() {
        AndroidTestStateHelper.resetAll()
    }

    @Test
    fun profile_rows_open_expected_screens() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            AndroidUiTestHelper.clickView(scenario, R.id.navProfile)
            AndroidUiTestHelper.waitForFragment<ProfileHubFragment>(scenario)

            openProfileRow(scenario, R.id.layoutPersonalizationRows, 0)
            AndroidUiTestHelper.waitForFragment<AccountProfileFragment>(scenario)
            AndroidUiTestHelper.popBackStack(scenario)
            AndroidUiTestHelper.waitForFragment<ProfileHubFragment>(scenario)

            openProfileRow(scenario, R.id.layoutPersonalizationRows, 1)
            AndroidUiTestHelper.waitForFragment<ProfileSettingsFragment>(scenario)
            AndroidUiTestHelper.popBackStack(scenario)
            AndroidUiTestHelper.waitForFragment<ProfileHubFragment>(scenario)

            openProfileRow(scenario, R.id.layoutPersonalizationRows, 2)
            AndroidUiTestHelper.waitForFragment<DailyTargetsSettingsFragment>(scenario)
            AndroidUiTestHelper.popBackStack(scenario)
            AndroidUiTestHelper.waitForFragment<ProfileHubFragment>(scenario)

            openProfileRow(scenario, R.id.layoutPersonalizationRows, 3)
            AndroidUiTestHelper.waitForFragment<MealScheduleSettingsFragment>(scenario)
            AndroidUiTestHelper.popBackStack(scenario)
            AndroidUiTestHelper.waitForFragment<ProfileHubFragment>(scenario)

            openProfileRow(scenario, R.id.layoutPersonalizationRows, 4)
            AndroidUiTestHelper.waitForFragment<GoalSettingsFragment>(scenario)
            AndroidUiTestHelper.popBackStack(scenario)
            AndroidUiTestHelper.waitForFragment<ProfileHubFragment>(scenario)

            openProfileRow(scenario, R.id.layoutAdditionalRows, 0)
            AndroidUiTestHelper.waitForFragment<NotificationPreferencesFragment>(scenario)
            AndroidUiTestHelper.popBackStack(scenario)
            AndroidUiTestHelper.waitForFragment<ProfileHubFragment>(scenario)

            openProfileRow(scenario, R.id.layoutAdditionalRows, 1)
            AndroidUiTestHelper.waitForFragment<ServiceSettingsFragment>(scenario)
            AndroidUiTestHelper.popBackStack(scenario)
            AndroidUiTestHelper.waitForFragment<ProfileHubFragment>(scenario)

            openProfileRow(scenario, R.id.layoutAdditionalRows, 2)
            AndroidUiTestHelper.waitForFragment<SupportFragment>(scenario)
            AndroidUiTestHelper.withFragment<SupportFragment>(scenario) { fragment ->
                assertEquals(
                    "evdikkom2004@mail.ru",
                    fragment.requireView().findViewById<android.widget.TextView>(R.id.textSupportEmail).text.toString()
                )
            }
        }
    }

    @Test
    fun daily_targets_goal_and_notification_settings_persist() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            AndroidUiTestHelper.clickView(scenario, R.id.navProfile)
            AndroidUiTestHelper.waitForFragment<ProfileHubFragment>(scenario)

            openProfileRow(scenario, R.id.layoutPersonalizationRows, 2)
            AndroidUiTestHelper.waitForFragment<DailyTargetsSettingsFragment>(scenario)
            AndroidUiTestHelper.withFragment<DailyTargetsSettingsFragment>(scenario) { fragment ->
                val root = fragment.requireView()
                root.findViewById<EditText>(R.id.editCalories).setText("2400")
                root.findViewById<EditText>(R.id.editProteinPercent).setText("30")
                root.findViewById<EditText>(R.id.editFatPercent).setText("25")
                root.findViewById<EditText>(R.id.editCarbsPercent).setText("45")
                root.findViewById<android.widget.Button>(R.id.buttonSave).performClick()
            }
            AndroidUiTestHelper.waitForFragment<ProfileHubFragment>(scenario)

            scenario.onActivity { activity ->
                val override = NutritionTargetsPreferences(activity).getOverride()
                assertEquals(2400, override?.calories)
                assertEquals(30, override?.proteinPercent)
                assertEquals(25, override?.fatPercent)
                assertEquals(45, override?.carbsPercent)
            }

            openProfileRow(scenario, R.id.layoutPersonalizationRows, 4)
            AndroidUiTestHelper.waitForFragment<GoalSettingsFragment>(scenario)
            AndroidUiTestHelper.withFragment<GoalSettingsFragment>(scenario) { fragment ->
                fragment.requireView()
                    .findViewById<android.widget.RadioGroup>(R.id.groupGoal)
                    .check(R.id.radioWeightLoss)
                fragment.requireView()
                    .findViewById<android.widget.Button>(R.id.buttonSaveGoal)
                    .performClick()
            }
            AndroidUiTestHelper.waitForFragment<ProfileHubFragment>(scenario)

            scenario.onActivity { activity ->
                val user = runBlocking {
                    AppDatabase.getInstance(activity).userDao().getCurrentUser()
                }
                assertEquals(UserGoal.WEIGHT_LOSS, user?.goal)
            }

            openProfileRow(scenario, R.id.layoutAdditionalRows, 0)
            AndroidUiTestHelper.waitForFragment<NotificationPreferencesFragment>(scenario)
            AndroidUiTestHelper.withFragment<NotificationPreferencesFragment>(scenario) { fragment ->
                val root = fragment.requireView()
                root.findViewById<SwitchCompat>(R.id.switchRecommendationPopup).isChecked = false
                root.findViewById<SwitchCompat>(R.id.switchRecommendationSection).isChecked = false
            }
            AndroidUiTestHelper.popBackStack(scenario)
            AndroidUiTestHelper.waitForFragment<ProfileHubFragment>(scenario)

            scenario.onActivity { activity ->
                val prefs = UiPreferences(activity)
                assertFalse(prefs.isRecommendationPopupEnabled())
                assertFalse(prefs.isRecommendationSectionEnabled())
            }

            openProfileRow(scenario, R.id.layoutAdditionalRows, 0)
            AndroidUiTestHelper.waitForFragment<NotificationPreferencesFragment>(scenario)
            AndroidUiTestHelper.withFragment<NotificationPreferencesFragment>(scenario) { fragment ->
                val root = fragment.requireView()
                assertFalse(root.findViewById<SwitchCompat>(R.id.switchRecommendationPopup).isChecked)
                assertFalse(root.findViewById<SwitchCompat>(R.id.switchRecommendationSection).isChecked)
            }
        }
    }

    private fun openProfileRow(
        scenario: ActivityScenario<MainActivity>,
        containerId: Int,
        index: Int
    ) {
        AndroidUiTestHelper.withFragment<ProfileHubFragment>(scenario) { fragment ->
            val container = fragment.requireView().findViewById<android.widget.LinearLayout>(containerId)
            assertTrue(index in 0 until container.childCount)
            container.getChildAt(index).performClick()
        }
    }
}
