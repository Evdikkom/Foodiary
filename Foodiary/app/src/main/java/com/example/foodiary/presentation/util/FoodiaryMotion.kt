package com.example.foodiary.presentation.util

import androidx.fragment.app.Fragment
import androidx.transition.Transition
import com.google.android.material.transition.MaterialFadeThrough
import com.google.android.material.transition.MaterialSharedAxis

private const val ROOT_FADE_DURATION_MS = 150L
private const val FORWARD_AXIS_DURATION_MS = 180L
private const val MODAL_AXIS_DURATION_MS = 210L

enum class FoodiaryMotionPattern {
    ROOT_FADE_THROUGH,
    FORWARD_AXIS_X,
    MODAL_AXIS_Y
}

fun prepareFoodiaryTransition(
    current: Fragment?,
    target: Fragment,
    pattern: FoodiaryMotionPattern
) {
    current?.applyExitMotion(pattern)
    target.applyEnterMotion(pattern)
}

private fun Fragment.applyExitMotion(pattern: FoodiaryMotionPattern) {
    exitTransition = when (pattern) {
        FoodiaryMotionPattern.ROOT_FADE_THROUGH -> rootFadeThrough()
        FoodiaryMotionPattern.FORWARD_AXIS_X -> sharedAxisX(forward = true)
        FoodiaryMotionPattern.MODAL_AXIS_Y -> sharedAxisY(forward = true)
    }
    reenterTransition = when (pattern) {
        FoodiaryMotionPattern.ROOT_FADE_THROUGH -> rootFadeThrough()
        FoodiaryMotionPattern.FORWARD_AXIS_X -> sharedAxisX(forward = false)
        FoodiaryMotionPattern.MODAL_AXIS_Y -> sharedAxisY(forward = false)
    }
}

private fun Fragment.applyEnterMotion(pattern: FoodiaryMotionPattern) {
    enterTransition = when (pattern) {
        FoodiaryMotionPattern.ROOT_FADE_THROUGH -> rootFadeThrough()
        FoodiaryMotionPattern.FORWARD_AXIS_X -> sharedAxisX(forward = true)
        FoodiaryMotionPattern.MODAL_AXIS_Y -> sharedAxisY(forward = true)
    }
    returnTransition = when (pattern) {
        FoodiaryMotionPattern.ROOT_FADE_THROUGH -> rootFadeThrough()
        FoodiaryMotionPattern.FORWARD_AXIS_X -> sharedAxisX(forward = false)
        FoodiaryMotionPattern.MODAL_AXIS_Y -> sharedAxisY(forward = false)
    }
}

private fun rootFadeThrough(): Transition {
    return MaterialFadeThrough().apply {
        duration = ROOT_FADE_DURATION_MS
    }
}

private fun sharedAxisX(forward: Boolean): Transition {
    return MaterialSharedAxis(MaterialSharedAxis.X, forward).apply {
        duration = FORWARD_AXIS_DURATION_MS
    }
}

private fun sharedAxisY(forward: Boolean): Transition {
    return MaterialSharedAxis(MaterialSharedAxis.Y, forward).apply {
        duration = MODAL_AXIS_DURATION_MS
    }
}
