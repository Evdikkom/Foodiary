package com.example.foodiary.presentation.flow

import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.foodiary.R
import com.example.foodiary.presentation.activity.MainActivity
import com.example.foodiary.presentation.fragment.DailyNutritionFragment
import com.example.foodiary.presentation.fragment.NutritionAnalyticsFragment
import com.example.foodiary.testing.AndroidTestStateHelper
import com.example.foodiary.testing.AndroidUiTestHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiarySelectedDayEdgeCaseTest {

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
    fun selected_day_is_preserved_when_switching_between_root_tabs() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            AndroidUiTestHelper.waitForFragment<DailyNutritionFragment>(scenario)
            AndroidUiTestHelper.clickView(scenario, R.id.buttonPrevDay)

            val selectedDayLabel = readSelectedDayLabel(scenario)

            AndroidUiTestHelper.clickView(scenario, R.id.navAnalytics)
            AndroidUiTestHelper.waitForFragment<NutritionAnalyticsFragment>(scenario)

            AndroidUiTestHelper.clickView(scenario, R.id.navDiary)
            AndroidUiTestHelper.waitForFragment<DailyNutritionFragment>(scenario)

            assertEquals(selectedDayLabel, readSelectedDayLabel(scenario))
        }
    }

    private fun readSelectedDayLabel(scenario: ActivityScenario<MainActivity>): String {
        var value = ""
        AndroidUiTestHelper.withFragment<DailyNutritionFragment>(scenario) { fragment ->
            value = fragment.requireView()
                .findViewById<TextView>(R.id.buttonSelectedDay)
                .text
                .toString()
        }
        return value
    }
}
