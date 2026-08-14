package com.example.foodiary.presentation.flow

import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.foodiary.R
import com.example.foodiary.data.local.preferences.LocalAccountPreferences
import com.example.foodiary.presentation.activity.MainActivity
import com.example.foodiary.presentation.fragment.AccountProfileFragment
import com.example.foodiary.presentation.fragment.DailyNutritionFragment
import com.example.foodiary.presentation.fragment.OnboardingFragment
import com.example.foodiary.testing.AndroidTestStateHelper
import com.example.foodiary.testing.AndroidUiTestHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingAndAccountFlowTest {

    @Before
    fun setUp() {
        AndroidTestStateHelper.resetAll()
    }

    @After
    fun tearDown() {
        AndroidTestStateHelper.resetAll()
    }

    @Test
    fun first_launch_allows_account_setup_and_onboarding_until_diary() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            AndroidUiTestHelper.waitForFragment<AccountProfileFragment>(scenario)

            AndroidUiTestHelper.withFragment<AccountProfileFragment>(scenario) { fragment ->
                val root = fragment.requireView()
                root.findViewById<EditText>(R.id.editDisplayName).setText("Анна")
                root.findViewById<EditText>(R.id.editEmail).setText("anna@example.com")
                root.findViewById<Button>(R.id.buttonSave).performClick()
            }

            AndroidUiTestHelper.waitForFragment<OnboardingFragment>(scenario)

            AndroidUiTestHelper.withFragment<OnboardingFragment>(scenario) { fragment ->
                val root = fragment.requireView()
                root.findViewById<RadioGroup>(R.id.groupSex).check(R.id.radioFemale)
                root.findViewById<EditText>(R.id.editAge).setText("27")
                root.findViewById<EditText>(R.id.editHeight).setText("168")
                root.findViewById<EditText>(R.id.editWeight).setText("62")
                root.findViewById<RadioGroup>(R.id.groupActivity).check(R.id.radioActive)
                root.findViewById<RadioGroup>(R.id.groupGoal).check(R.id.radioMaintainWeight)
                root.findViewById<Button>(R.id.buttonSaveProfile).performClick()
            }

            AndroidUiTestHelper.waitForFragment<DailyNutritionFragment>(scenario)

            scenario.onActivity { activity ->
                val diary = activity.supportFragmentManager.findFragmentById(R.id.fragmentContainer)
                    as DailyNutritionFragment
                val selectedDayLabel = diary.requireView()
                    .findViewById<TextView>(R.id.buttonSelectedDay)
                    .text
                    .toString()
                val account = LocalAccountPreferences(activity).getAccount()

                assertTrue(selectedDayLabel.contains("Сегодня"))
                assertEquals("anna@example.com", account?.email)
                assertEquals("Анна", account?.displayName)
            }
        }
    }
}
