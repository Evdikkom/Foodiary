package com.example.foodiary.presentation.util

import android.os.SystemClock
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import com.example.foodiary.R

private const val DEFAULT_CLICK_DEBOUNCE_MS = 500L
private const val PRESS_SCALE = 0.965f

fun View.setDebouncedClickListener(
    debounceMs: Long = DEFAULT_CLICK_DEBOUNCE_MS,
    onClick: (View) -> Unit
) {
    var lastClickAt = 0L
    setOnClickListener { view ->
        val now = SystemClock.elapsedRealtime()
        if (now - lastClickAt < debounceMs) return@setOnClickListener
        lastClickAt = now
        view.playSoftPressAnimation()
        onClick(view)
    }
}

fun View.playSoftPressAnimation() {
    if (!isShown || !isEnabled) return
    animate().cancel()
    scaleX = 1f
    scaleY = 1f
    animate()
        .scaleX(PRESS_SCALE)
        .scaleY(PRESS_SCALE)
        .setDuration(70L)
        .setInterpolator(DecelerateInterpolator())
        .withEndAction {
            animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(120L)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
        .start()
}

fun Fragment.popBackStackSafely() {
    if (!isAdded || parentFragmentManager.isStateSaved || view == null) return
    parentFragmentManager.popBackStack()
}

fun Fragment.replaceFragmentSafely(
    fragment: Fragment,
    addToBackStack: Boolean = true,
    motionPattern: FoodiaryMotionPattern = FoodiaryMotionPattern.FORWARD_AXIS_X
): Boolean {
    val rootView = view ?: return false
    if (!isAdded || parentFragmentManager.isStateSaved) return false
    if (!viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return false
    if (parentFragmentManager.findFragmentById(R.id.fragmentContainer) !== this) return false

    prepareFoodiaryTransition(this, fragment, motionPattern)
    parentFragmentManager.beginTransaction().apply {
        setReorderingAllowed(true)
        replace(R.id.fragmentContainer, fragment)
        if (addToBackStack) addToBackStack(null)
    }.commit()

    rootView.isEnabled = false
    return true
}
