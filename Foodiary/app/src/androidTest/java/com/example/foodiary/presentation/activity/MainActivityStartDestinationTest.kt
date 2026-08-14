package com.example.foodiary.presentation.activity

import android.os.SystemClock
import androidx.fragment.app.Fragment
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.foodiary.R
import com.example.foodiary.presentation.fragment.AccountProfileFragment
import com.example.foodiary.presentation.fragment.DailyNutritionFragment
import com.example.foodiary.presentation.fragment.OnboardingFragment
import com.example.foodiary.testing.AndroidTestStateHelper
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityStartDestinationTest {

    @Before
    fun setUp() {
        AndroidTestStateHelper.resetAll()
    }

    @After
    fun tearDown() {
        AndroidTestStateHelper.resetAll()
    }

    @Test
    fun launch_without_local_account_opens_account_setup() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForFragment<AccountProfileFragment>(scenario)
        }
    }

    @Test
    fun launch_with_local_account_but_without_user_opens_onboarding() {
        AndroidTestStateHelper.saveLocalAccount()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForFragment<OnboardingFragment>(scenario)
        }
    }

    @Test
    fun launch_with_local_account_and_user_opens_diary() {
        AndroidTestStateHelper.saveLocalAccount()
        AndroidTestStateHelper.saveUser()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForFragment<DailyNutritionFragment>(scenario)
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
}
