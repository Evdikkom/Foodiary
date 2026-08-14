package com.example.foodiary.presentation.flow

import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.foodiary.R
import com.example.foodiary.domain.model.FoodSearchItem
import com.example.foodiary.domain.model.MealType
import com.example.foodiary.presentation.activity.MainActivity
import com.example.foodiary.presentation.fragment.AddMealFragment
import com.example.foodiary.testing.AndroidTestStateHelper
import com.example.foodiary.testing.AndroidUiTestHelper
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OpenFoodFactsAddMealSearchSmokeTest {

    private val chipsRu = "\u0447\u0438\u043f\u0441\u044b"

    @Before
    fun setUp() {
        assumeTrue(isLiveOffUiSmokeEnabled())
        AndroidTestStateHelper.resetAll()
        AndroidTestStateHelper.saveLocalAccount()
        AndroidTestStateHelper.saveUser()
    }

    @After
    fun tearDown() {
        if (isLiveOffUiSmokeEnabled()) {
            AndroidTestStateHelper.resetAll()
        }
    }

    @Test
    fun add_meal_search_populates_recycler_from_open_food_facts() {
        val today = AndroidTestStateHelper.dayStart()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.supportFragmentManager.beginTransaction()
                    .replace(
                        R.id.fragmentContainer,
                        AddMealFragment.newInstance(MealType.BREAKFAST, today)
                    )
                    .commitNow()
            }
            AndroidUiTestHelper.waitForFragment<AddMealFragment>(scenario)

            AndroidUiTestHelper.withFragment<AddMealFragment>(scenario) { fragment ->
                fragment.requireView()
                    .findViewById<EditText>(R.id.editSearchFood)
                    .setText(chipsRu)
            }

            AndroidUiTestHelper.waitUntilCondition(
                timeoutMs = 18_000L,
                message = "Expected AddMealFragment to receive Open Food Facts results for '$chipsRu'"
            ) {
                hasRemoteResultsAndVisibleRows(scenario)
            }

            AndroidUiTestHelper.withFragment<AddMealFragment>(scenario) { fragment ->
                val error = fragment.requireView().findViewById<TextView>(R.id.textError)
                assertTrue(
                    "Expected search screen to stay without visible error",
                    error.visibility != View.VISIBLE || error.text.isNullOrBlank()
                )
            }
        }
    }

    private fun hasRemoteResultsAndVisibleRows(
        scenario: ActivityScenario<MainActivity>
    ): Boolean {
        var hasRemoteResults = false
        var hasVisibleRows = false

        scenario.onActivity { activity ->
            val fragment = activity.supportFragmentManager
                .findFragmentById(R.id.fragmentContainer) as? AddMealFragment
                ?: return@onActivity

            hasRemoteResults = readCurrentRemoteFoods(fragment).isNotEmpty()
            val recycler = fragment.requireView().findViewById<RecyclerView>(R.id.recyclerFoods)
            hasVisibleRows = recycler.visibility == View.VISIBLE &&
                (recycler.adapter?.itemCount ?: 0) > 0
        }

        return hasRemoteResults && hasVisibleRows
    }

    @Suppress("UNCHECKED_CAST")
    private fun readCurrentRemoteFoods(fragment: AddMealFragment): List<FoodSearchItem> {
        val field = AddMealFragment::class.java.getDeclaredField("currentRemoteFoods")
        field.isAccessible = true
        return field.get(fragment) as? List<FoodSearchItem> ?: emptyList()
    }

    private fun isLiveOffUiSmokeEnabled(): Boolean {
        val args = InstrumentationRegistry.getArguments()
        return args.getString("foodiary.off.live") == "true" ||
            args.getString("foodiary_off_live") == "true"
    }
}
