package com.example.foodiary.presentation.flow

import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.foodiary.R
import com.example.foodiary.data.local.entity.MealEntity
import com.example.foodiary.domain.model.MealType
import com.example.foodiary.presentation.activity.MainActivity
import com.example.foodiary.presentation.fragment.AddMealFragment
import com.example.foodiary.presentation.fragment.MealDetailsFragment
import com.example.foodiary.testing.AndroidTestStateHelper
import com.example.foodiary.testing.AndroidUiTestHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryTemplatesFlowTest {

    @Before
    fun setUp() {
        AndroidTestStateHelper.resetAll()
        AndroidTestStateHelper.saveLocalAccount()
        AndroidTestStateHelper.saveUser()
        seedRepeatedLunchHistory()
    }

    @After
    fun tearDown() {
        AndroidTestStateHelper.resetAll()
    }

    @Test
    fun repeated_meals_build_history_template_and_apply_to_current_day() {
        val today = AndroidTestStateHelper.dayStart()
        assertEquals(0, AndroidTestStateHelper.getMealsCountForDay(today, MealType.LUNCH))
        Log.d("HistoryTemplatesTest", "start test with today=$today")

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                Log.d("HistoryTemplatesTest", "replace root with AddMealFragment")
                activity.supportFragmentManager.beginTransaction()
                    .replace(
                        R.id.fragmentContainer,
                        AddMealFragment.newInstance(MealType.LUNCH, today)
                    )
                    .commitNow()
            }
            AndroidUiTestHelper.waitForFragment<AddMealFragment>(scenario)
            Log.d("HistoryTemplatesTest", "AddMealFragment shown")

            AndroidUiTestHelper.withFragment<AddMealFragment>(scenario) { fragment ->
                Log.d("HistoryTemplatesTest", "click history templates button")
                fragment.requireView().findViewById<Button>(R.id.buttonHistoryTemplates).performClick()
            }
            AndroidUiTestHelper.waitForChildDialog<AddMealFragment>(scenario, "history_meal_templates")
            Log.d("HistoryTemplatesTest", "history templates dialog shown")

            scenario.onActivity { activity ->
                val parent = activity.supportFragmentManager.findFragmentById(R.id.fragmentContainer)
                    as AddMealFragment
                val dialog = parent.childFragmentManager.findFragmentByTag("history_meal_templates")
                    as androidx.fragment.app.DialogFragment
                Log.d("HistoryTemplatesTest", "apply first history template")
                val cards = dialog.requireView().findViewById<LinearLayout>(R.id.layoutTemplatesContainer)
                cards.getChildAt(0)
                    .findViewById<Button>(R.id.buttonApplyTemplate)
                    .performClick()
            }

            AndroidUiTestHelper.waitForFragment<MealDetailsFragment>(scenario)
            Log.d("HistoryTemplatesTest", "MealDetailsFragment shown after applying template")
            assertEquals(2, AndroidTestStateHelper.getMealsCountForDay(today, MealType.LUNCH))
        }
    }

    private fun seedRepeatedLunchHistory() {
        val yesterday = AndroidTestStateHelper.dayStart(-1)
        val twoDaysAgo = AndroidTestStateHelper.dayStart(-2)

        AndroidTestStateHelper.saveMeals(
            MealEntity(
                foodId = "chicken_breast",
                quantityInGrams = 180.0,
                mealType = MealType.LUNCH,
                timestamp = yesterday + 13 * 60 * 60 * 1000L
            ),
            MealEntity(
                foodId = "rice",
                quantityInGrams = 150.0,
                mealType = MealType.LUNCH,
                timestamp = yesterday + 13 * 60 * 60 * 1000L + 60_000L
            ),
            MealEntity(
                foodId = "chicken_breast",
                quantityInGrams = 180.0,
                mealType = MealType.LUNCH,
                timestamp = twoDaysAgo + 13 * 60 * 60 * 1000L
            ),
            MealEntity(
                foodId = "rice",
                quantityInGrams = 150.0,
                mealType = MealType.LUNCH,
                timestamp = twoDaysAgo + 13 * 60 * 60 * 1000L + 60_000L
            )
        )
    }
}
