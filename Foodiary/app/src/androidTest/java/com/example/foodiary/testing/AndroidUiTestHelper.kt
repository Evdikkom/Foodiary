package com.example.foodiary.testing

import android.os.SystemClock
import android.view.View
import androidx.annotation.IdRes
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import com.example.foodiary.R
import com.example.foodiary.presentation.activity.MainActivity
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

object AndroidUiTestHelper {

    inline fun <reified T : Fragment> waitForFragment(
        scenario: ActivityScenario<MainActivity>,
        timeoutMs: Long = 5_000L
    ): T {
        dismissSystemCompatibilityDialogIfPresent()
        val fragment = waitUntil(timeoutMs) {
            var matched: T? = null
            scenario.onActivity { activity ->
                matched = activity.supportFragmentManager.findFragmentById(R.id.fragmentContainer) as? T
            }
            matched
        }
        assertNotNull("Expected fragment ${T::class.java.simpleName} was not shown", fragment)
        return fragment as T
    }

    fun waitForDialog(
        scenario: ActivityScenario<MainActivity>,
        tag: String,
        timeoutMs: Long = 5_000L
    ): DialogFragment {
        dismissSystemCompatibilityDialogIfPresent()
        val dialog = waitUntil(timeoutMs) {
            var matched: DialogFragment? = null
            scenario.onActivity { activity ->
                matched = activity.supportFragmentManager.findFragmentByTag(tag) as? DialogFragment
            }
            matched
        }
        assertNotNull("Expected dialog with tag $tag was not shown", dialog)
        return dialog as DialogFragment
    }

    inline fun <reified T : Fragment> waitForChildDialog(
        scenario: ActivityScenario<MainActivity>,
        tag: String,
        timeoutMs: Long = 5_000L
    ): DialogFragment {
        dismissSystemCompatibilityDialogIfPresent()
        val dialog = waitUntil(timeoutMs) {
            var matched: DialogFragment? = null
            scenario.onActivity { activity ->
                val parent = activity.supportFragmentManager.findFragmentById(R.id.fragmentContainer) as? T
                matched = parent?.childFragmentManager?.findFragmentByTag(tag) as? DialogFragment
            }
            matched
        }
        assertNotNull(
            "Expected child dialog with tag $tag for ${T::class.java.simpleName} was not shown",
            dialog
        )
        return dialog as DialogFragment
    }

    inline fun <reified T : Fragment> withFragment(
        scenario: ActivityScenario<MainActivity>,
        crossinline block: (T) -> Unit
    ) {
        scenario.onActivity { activity ->
            val fragment = activity.supportFragmentManager.findFragmentById(R.id.fragmentContainer) as? T
            assertNotNull("Expected fragment ${T::class.java.simpleName} was not found", fragment)
            block(fragment as T)
        }
        waitForIdle()
    }

    fun clickView(
        scenario: ActivityScenario<MainActivity>,
        @IdRes viewId: Int
    ) {
        scenario.onActivity { activity ->
            activity.findViewById<View>(viewId).performClick()
        }
        waitForIdle()
    }

    fun popBackStack(scenario: ActivityScenario<MainActivity>) {
        scenario.onActivity { activity ->
            activity.supportFragmentManager.popBackStackImmediate()
        }
        waitForIdle()
    }

    fun waitForIdle() {
        dismissSystemCompatibilityDialogIfPresent()
        SystemClock.sleep(180L)
    }

    fun waitUntilCondition(
        timeoutMs: Long = 5_000L,
        message: String = "Condition was not met in time",
        condition: () -> Boolean
    ) {
        val matched = waitUntil(timeoutMs) {
            if (condition()) true else null
        }
        assertTrue(message, matched == true)
    }

    fun dismissSystemCompatibilityDialogIfPresent() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val title = device.findObject(By.text("Android App Compatibility"))
        if (title != null) {
            device.findObject(By.text("Don't Show Again"))?.click()
                ?: device.findObject(By.text("OK"))?.click()
            SystemClock.sleep(300L)
        }
    }

    @PublishedApi
    internal fun <T> waitUntil(
        timeoutMs: Long,
        block: () -> T?
    ): T? {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        var value: T? = null
        while (value == null && SystemClock.elapsedRealtime() < deadline) {
            waitForIdle()
            value = block()
        }
        return value
    }
}
