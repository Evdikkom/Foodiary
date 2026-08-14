package com.example.foodiary.presentation.flow

import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.foodiary.R
import com.example.foodiary.data.local.entity.FoodEntity
import com.example.foodiary.data.local.entity.MealEntity
import com.example.foodiary.domain.model.MealType
import com.example.foodiary.presentation.activity.MainActivity
import com.example.foodiary.presentation.fragment.NutritionAnalyticsFragment
import com.example.foodiary.testing.AndroidTestStateHelper
import com.example.foodiary.testing.AndroidUiTestHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnalyticsFlowTest {

    @Before
    fun setUp() {
        AndroidTestStateHelper.resetAll()
        AndroidTestStateHelper.waitForSeedFoods()
        AndroidTestStateHelper.clearMeals()
        AndroidTestStateHelper.saveLocalAccount()
        AndroidTestStateHelper.saveUser()
    }

    @After
    fun tearDown() {
        AndroidTestStateHelper.resetAll()
    }

    @Test
    fun analytics_periods_render_real_history_without_errors() {
        seedAnalyticsHistory()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            AndroidUiTestHelper.clickView(scenario, R.id.navAnalytics)
            AndroidUiTestHelper.waitForFragment<NutritionAnalyticsFragment>(scenario)
            waitForAnalyticsLoaded(scenario)

            AndroidUiTestHelper.withFragment<NutritionAnalyticsFragment>(scenario) { fragment ->
                val root = fragment.requireView()
                assertEquals(View.GONE, root.findViewById<TextView>(R.id.textAnalyticsError).visibility)
                assertTrue(root.findViewById<TextView>(R.id.textSelectedAverageCalories).text.isNotBlank())
                assertTrue(root.findViewById<TextView>(R.id.textMacroProteinValue).text.toString() != "-")
                assertTrue(root.findViewById<TextView>(R.id.textTargetCaloriesPercent).text.toString().endsWith("%"))
                assertTrue(root.findViewById<LinearLayout>(R.id.layoutPeriodDetails).childCount > 0)
                root.findViewById<TextView>(R.id.buttonActiveDayHint).performClick()
            }

            AndroidUiTestHelper.clickView(scenario, R.id.chipPeriodMonth)
            waitForAnalyticsLoaded(scenario)
            AndroidUiTestHelper.withFragment<NutritionAnalyticsFragment>(scenario) { fragment ->
                val root = fragment.requireView()
                assertTrue(root.findViewById<LinearLayout>(R.id.layoutPeriodDetails).childCount > 0)
                assertTrue(root.findViewById<TextView>(R.id.textSelectedPeriodLabel).text.isNotBlank())
            }

            AndroidUiTestHelper.clickView(scenario, R.id.chipPeriodYear)
            waitForAnalyticsLoaded(scenario)
            AndroidUiTestHelper.withFragment<NutritionAnalyticsFragment>(scenario) { fragment ->
                val root = fragment.requireView()
                assertEquals(View.GONE, root.findViewById<TextView>(R.id.textAnalyticsError).visibility)
                assertTrue(root.findViewById<LinearLayout>(R.id.layoutPeriodDetails).childCount > 0)
            }
        }
    }

    @Test
    fun analytics_empty_history_keeps_all_periods_safe() {
        AndroidTestStateHelper.clearMeals()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            AndroidUiTestHelper.clickView(scenario, R.id.navAnalytics)
            AndroidUiTestHelper.waitForFragment<NutritionAnalyticsFragment>(scenario)
            waitForAnalyticsLoaded(scenario)

            AndroidUiTestHelper.withFragment<NutritionAnalyticsFragment>(scenario) { fragment ->
                val root = fragment.requireView()
                assertEquals(View.VISIBLE, root.findViewById<TextView>(R.id.textAnalyticsEmpty).visibility)
                assertEquals("-", root.findViewById<TextView>(R.id.textMacroProteinValue).text.toString())
                assertEquals("-", root.findViewById<TextView>(R.id.textTargetCaloriesPercent).text.toString())
            }

            AndroidUiTestHelper.clickView(scenario, R.id.chipPeriodMonth)
            waitForAnalyticsLoaded(scenario)
            AndroidUiTestHelper.withFragment<NutritionAnalyticsFragment>(scenario) { fragment ->
                assertTrue(
                    fragment.requireView()
                        .findViewById<LinearLayout>(R.id.layoutPeriodDetails)
                        .childCount > 0
                )
            }

            AndroidUiTestHelper.clickView(scenario, R.id.chipPeriodYear)
            waitForAnalyticsLoaded(scenario)
            AndroidUiTestHelper.withFragment<NutritionAnalyticsFragment>(scenario) { fragment ->
                val root = fragment.requireView()
                assertEquals(View.VISIBLE, root.findViewById<TextView>(R.id.textAnalyticsEmpty).visibility)
                assertEquals(View.GONE, root.findViewById<TextView>(R.id.textAnalyticsError).visibility)
            }
        }
    }

    private fun seedAnalyticsHistory() {
        AndroidTestStateHelper.saveFoods(
            FoodEntity(
                id = "analytics_oats",
                name = "Analytics oats",
                caloriesPer100g = 360.0,
                proteinPer100g = 10.0,
                fatPer100g = 5.0,
                carbsPer100g = 60.0,
                category = "grain"
            ),
            FoodEntity(
                id = "analytics_chicken",
                name = "Analytics chicken",
                caloriesPer100g = 200.0,
                proteinPer100g = 30.0,
                fatPer100g = 5.0,
                carbsPer100g = 0.0,
                category = "protein"
            ),
            FoodEntity(
                id = "analytics_rice",
                name = "Analytics rice",
                caloriesPer100g = 100.0,
                proteinPer100g = 2.0,
                fatPer100g = 0.0,
                carbsPer100g = 20.0,
                category = "grain"
            )
        )
        AndroidTestStateHelper.clearMeals()
        AndroidTestStateHelper.saveMeals(
            MealEntity(
                foodId = "analytics_oats",
                quantityInGrams = 100.0,
                mealType = MealType.BREAKFAST,
                timestamp = AndroidTestStateHelper.dayStart(0) + hour(8)
            ),
            MealEntity(
                foodId = "analytics_chicken",
                quantityInGrams = 150.0,
                mealType = MealType.LUNCH,
                timestamp = AndroidTestStateHelper.dayStart(-1) + hour(13)
            ),
            MealEntity(
                foodId = "analytics_rice",
                quantityInGrams = 200.0,
                mealType = MealType.DINNER,
                timestamp = AndroidTestStateHelper.dayStart(-2) + hour(19)
            )
        )
    }

    private fun waitForAnalyticsLoaded(scenario: ActivityScenario<MainActivity>) {
        AndroidUiTestHelper.waitUntilCondition(
            timeoutMs = 6_000L,
            message = "Analytics screen did not finish loading"
        ) {
            var loaded = false
            scenario.onActivity { activity ->
                val fragment = activity.supportFragmentManager
                    .findFragmentById(R.id.fragmentContainer) as? NutritionAnalyticsFragment
                val root = fragment?.view
                loaded = root != null &&
                    root.findViewById<View>(R.id.progressAnalytics).visibility != View.VISIBLE
            }
            loaded
        }
    }

    private fun hour(value: Int): Long = value * 60L * 60L * 1000L
}
