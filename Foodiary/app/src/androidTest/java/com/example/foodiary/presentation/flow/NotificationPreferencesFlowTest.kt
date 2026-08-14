package com.example.foodiary.presentation.flow

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.foodiary.R
import com.example.foodiary.data.local.preferences.ReminderPreferences
import com.example.foodiary.domain.model.MealType
import com.example.foodiary.presentation.activity.MainActivity
import com.example.foodiary.presentation.fragment.NotificationPreferencesFragment
import com.example.foodiary.presentation.fragment.ProfileHubFragment
import com.example.foodiary.testing.AndroidTestStateHelper
import com.example.foodiary.testing.AndroidUiTestHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationPreferencesFlowTest {

    @Before
    fun setUp() {
        AndroidTestStateHelper.resetAll()
        AndroidTestStateHelper.saveLocalAccount()
        AndroidTestStateHelper.saveUser()
        grantPostNotificationsIfPossible()
    }

    @After
    fun tearDown() {
        AndroidTestStateHelper.resetAll()
    }

    @Test
    fun notification_rows_toggle_and_persist_reminder_preferences() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            openNotificationSettings(scenario)

            AndroidUiTestHelper.withFragment<NotificationPreferencesFragment>(scenario) { fragment ->
                val root = fragment.requireView()
                val mealRows = root.findViewById<LinearLayout>(R.id.layoutMealReminderRows)
                val waterRows = root.findViewById<LinearLayout>(R.id.layoutWaterReminderRows)
                assertTrue(mealRows.childCount >= 4)
                assertEquals(1, waterRows.childCount)

                root.findViewById<TextView>(R.id.buttonAddWaterReminder).performClick()
            }

            AndroidUiTestHelper.withFragment<NotificationPreferencesFragment>(scenario) { fragment ->
                val root = fragment.requireView()
                val waterRows = root.findViewById<LinearLayout>(R.id.layoutWaterReminderRows)
                assertEquals(2, waterRows.childCount)
                SystemClock.sleep(550L)
                root.findViewById<TextView>(R.id.buttonAddWaterReminder).performClick()
            }

            AndroidUiTestHelper.withFragment<NotificationPreferencesFragment>(scenario) { fragment ->
                val root = fragment.requireView()
                val mealRows = root.findViewById<LinearLayout>(R.id.layoutMealReminderRows)
                val waterRows = root.findViewById<LinearLayout>(R.id.layoutWaterReminderRows)
                assertEquals(3, waterRows.childCount)
                assertEquals(View.GONE, root.findViewById<TextView>(R.id.buttonAddWaterReminder).visibility)

                setSwitchChecked(mealRows.getChildAt(0), true)
                setSwitchChecked(waterRows.getChildAt(0), true)
            }

            scenario.onActivity { activity ->
                val prefs = ReminderPreferences(activity)
                assertEquals(3, prefs.getWaterReminderCount())
                assertTrue(prefs.getMealReminder(MealType.BREAKFAST).enabled)
                assertTrue(prefs.getWaterReminder(0).enabled)
            }

            AndroidUiTestHelper.popBackStack(scenario)
            AndroidUiTestHelper.waitForFragment<ProfileHubFragment>(scenario)
            openNotificationSettings(scenario)

            AndroidUiTestHelper.withFragment<NotificationPreferencesFragment>(scenario) { fragment ->
                val root = fragment.requireView()
                val mealRows = root.findViewById<LinearLayout>(R.id.layoutMealReminderRows)
                val waterRows = root.findViewById<LinearLayout>(R.id.layoutWaterReminderRows)
                assertEquals(3, waterRows.childCount)
                assertTrue(mealRows.getChildAt(0).findViewById<SwitchCompat>(R.id.switchReminder).isChecked)
                assertTrue(waterRows.getChildAt(0).findViewById<SwitchCompat>(R.id.switchReminder).isChecked)
            }
        }
    }

    @Test
    fun water_reminder_count_is_clamped_to_supported_range() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = ReminderPreferences(context)

        prefs.setWaterReminderCount(99)
        assertEquals(3, prefs.getWaterReminderCount())

        prefs.setWaterReminderCount(-5)
        assertEquals(1, prefs.getWaterReminderCount())

        prefs.setWaterReminder(-1, ReminderPreferences.DailyReminder(enabled = true, hour = 9, minute = 15))
        prefs.setWaterReminder(99, ReminderPreferences.DailyReminder(enabled = true, hour = 21, minute = 45))

        assertTrue(prefs.getWaterReminder(0).enabled)
        assertEquals(9, prefs.getWaterReminder(0).hour)
        assertTrue(prefs.getWaterReminder(2).enabled)
        assertEquals(21, prefs.getWaterReminder(2).hour)
    }

    private fun openNotificationSettings(scenario: ActivityScenario<MainActivity>) {
        AndroidUiTestHelper.clickView(scenario, R.id.navProfile)
        AndroidUiTestHelper.waitForFragment<ProfileHubFragment>(scenario)
        AndroidUiTestHelper.withFragment<ProfileHubFragment>(scenario) { fragment ->
            val rows = fragment.requireView().findViewById<LinearLayout>(R.id.layoutAdditionalRows)
            rows.getChildAt(0).performClick()
        }
        AndroidUiTestHelper.waitForFragment<NotificationPreferencesFragment>(scenario)
    }

    private fun setSwitchChecked(row: View, checked: Boolean) {
        val switch = row.findViewById<SwitchCompat>(R.id.switchReminder)
        if (switch.isChecked != checked) {
            switch.performClick()
        }
        assertEquals(checked, switch.isChecked)
    }

    private fun grantPostNotificationsIfPossible() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val context = ApplicationProvider.getApplicationContext<Context>()
        runCatching {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
                context.packageName,
                Manifest.permission.POST_NOTIFICATIONS
            )
        }

        assertEquals(
            PackageManager.PERMISSION_GRANTED,
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        )
    }
}
