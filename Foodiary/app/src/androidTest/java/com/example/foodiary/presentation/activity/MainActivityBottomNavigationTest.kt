package com.example.foodiary.presentation.activity

import android.os.SystemClock
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.foodiary.R
import com.example.foodiary.presentation.fragment.AddMealFragment
import com.example.foodiary.presentation.fragment.DailyNutritionFragment
import com.example.foodiary.presentation.fragment.NutritionAnalyticsFragment
import com.example.foodiary.presentation.fragment.ProfileHubFragment
import com.example.foodiary.presentation.fragment.RecipesHubFragment
import com.example.foodiary.presentation.fragment.ServiceSettingsFragment
import com.example.foodiary.testing.AndroidTestStateHelper
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityBottomNavigationTest {

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
    fun bottom_navigation_switches_root_screens_and_opens_global_add() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForFragment<DailyNutritionFragment>(scenario)

            scenario.onActivity { activity ->
                activity.findViewById<android.view.View>(R.id.navAnalytics).performClick()
            }
            waitForFragment<NutritionAnalyticsFragment>(scenario)

            scenario.onActivity { activity ->
                activity.findViewById<android.view.View>(R.id.navRecipes).performClick()
            }
            waitForFragment<RecipesHubFragment>(scenario)

            scenario.onActivity { activity ->
                activity.findViewById<android.view.View>(R.id.navProfile).performClick()
            }
            waitForFragment<ProfileHubFragment>(scenario)

            scenario.onActivity { activity ->
                activity.findViewById<android.view.View>(R.id.navDiary).performClick()
            }
            waitForFragment<DailyNutritionFragment>(scenario)

            scenario.onActivity { activity ->
                activity.findViewById<android.view.View>(R.id.buttonGlobalAdd).performClick()
            }
            waitForDialog(scenario, "meal_type_picker")
        }
    }

    @Test
    fun diary_breakfast_plus_opens_after_visiting_service_settings() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForFragment<DailyNutritionFragment>(scenario)

            scenario.onActivity { activity ->
                activity.findViewById<android.view.View>(R.id.navProfile).performClick()
            }
            waitForFragment<ProfileHubFragment>(scenario)

            scenario.onActivity { activity ->
                val profile = activity.supportFragmentManager.findFragmentById(R.id.fragmentContainer)
                    as ProfileHubFragment
                val additionalRows =
                    profile.requireView().findViewById<android.widget.LinearLayout>(R.id.layoutAdditionalRows)
                additionalRows.getChildAt(1).performClick()
            }
            waitForFragment<ServiceSettingsFragment>(scenario)

            scenario.onActivity { activity ->
                activity.findViewById<android.view.View>(R.id.navDiary).performClick()
            }
            waitForFragment<DailyNutritionFragment>(scenario)

            scenario.onActivity { activity ->
                val diary = activity.supportFragmentManager.findFragmentById(R.id.fragmentContainer)
                    as DailyNutritionFragment
                diary.requireView().findViewById<android.view.View>(R.id.buttonAddBreakfast).performClick()
            }
            waitForFragment<AddMealFragment>(scenario)
        }
    }

    private inline fun <reified T : Fragment> waitForFragment(
        scenario: ActivityScenario<MainActivity>,
        timeoutMs: Long = 5_000L
    ) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        var matched = false
        while (!matched && SystemClock.elapsedRealtime() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                matched = activity.supportFragmentManager.findFragmentById(R.id.fragmentContainer) is T
            }
            if (!matched) SystemClock.sleep(50)
        }
        assertTrue("Expected fragment ${T::class.java.simpleName} was not shown", matched)
    }

    private fun waitForDialog(
        scenario: ActivityScenario<MainActivity>,
        tag: String,
        timeoutMs: Long = 5_000L
    ) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        var matched = false
        while (!matched && SystemClock.elapsedRealtime() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                matched = activity.supportFragmentManager.findFragmentByTag(tag) is DialogFragment
            }
            if (!matched) SystemClock.sleep(50)
        }
        assertTrue("Expected dialog with tag $tag was not shown", matched)
    }
}
