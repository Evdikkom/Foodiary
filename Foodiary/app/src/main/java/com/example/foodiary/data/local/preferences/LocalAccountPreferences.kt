package com.example.foodiary.data.local.preferences

import android.content.Context

class LocalAccountPreferences(context: Context) {

    data class LocalAccount(
        val email: String,
        val displayName: String
    )

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isAccountReady(): Boolean {
        return prefs.getBoolean(KEY_SETUP_COMPLETE, false)
    }

    fun getAccount(): LocalAccount? {
        val isReady = isAccountReady()
        val email = getEmail()
        val displayName = prefs.getString(KEY_DISPLAY_NAME, "")?.trim().orEmpty()
        if (!isReady && email.isBlank() && displayName.isBlank()) return null
        return LocalAccount(
            email = email,
            displayName = displayName
        )
    }

    fun getEmail(): String {
        return prefs.getString(KEY_EMAIL, "")?.trim().orEmpty()
    }

    fun getDisplayName(): String {
        return prefs.getString(KEY_DISPLAY_NAME, "")?.trim().orEmpty()
    }

    fun saveAccount(email: String, displayName: String) {
        prefs.edit()
            .putString(KEY_EMAIL, email.trim())
            .putString(KEY_DISPLAY_NAME, displayName.trim())
            .putBoolean(KEY_SETUP_COMPLETE, true)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "foodiary_local_account"
        private const val KEY_EMAIL = "email"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_SETUP_COMPLETE = "setup_complete"
    }
}
