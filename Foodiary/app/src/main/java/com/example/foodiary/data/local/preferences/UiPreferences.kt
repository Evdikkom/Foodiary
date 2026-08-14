package com.example.foodiary.data.local.preferences

import android.content.Context

class UiPreferences(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isRecommendationPopupEnabled(): Boolean {
        return prefs.getBoolean(KEY_RECOMMENDATION_POPUP, true)
    }

    fun setRecommendationPopupEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_RECOMMENDATION_POPUP, enabled).apply()
    }

    fun isRecommendationSectionEnabled(): Boolean {
        return prefs.getBoolean(KEY_RECOMMENDATION_SECTION, true)
    }

    fun setRecommendationSectionEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_RECOMMENDATION_SECTION, enabled).apply()
    }

    companion object {
        private const val PREFS_NAME = "foodiary_ui_preferences"
        private const val KEY_RECOMMENDATION_POPUP = "recommendation_popup_enabled"
        private const val KEY_RECOMMENDATION_SECTION = "recommendation_section_enabled"
    }
}
