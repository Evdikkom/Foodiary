package com.example.foodiary.data.remote.off

import com.example.foodiary.data.local.preferences.LocalAccountPreferences

class OpenFoodFactsUserAgentProvider(
    private val accountPreferences: LocalAccountPreferences?,
    private val appVersion: String
) {

    fun currentUserAgent(): String {
        return OpenFoodFactsUserAgent.build(
            appVersion = appVersion,
            contactEmail = accountPreferences?.getEmail()
        )
    }
}
