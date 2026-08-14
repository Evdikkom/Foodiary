package com.example.foodiary.data.local.preferences

import android.content.Context

class ServiceConfigPreferences(context: Context) {

    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun getBackendHost(): String {
        return preferences.getString(KEY_BACKEND_HOST, null)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_BACKEND_HOST
    }

    fun getBackendPort(): Int {
        return runCatching { preferences.getString(KEY_BACKEND_PORT, null) }
            .getOrNull()
            ?.toIntOrNull()
            ?.takeIf { it in 1..65_535 }
            ?: runCatching { preferences.getInt(KEY_BACKEND_PORT, DEFAULT_BACKEND_PORT) }
                .getOrDefault(DEFAULT_BACKEND_PORT)
                .takeIf { it in 1..65_535 }
            ?: DEFAULT_BACKEND_PORT
    }

    fun getBackendApiKey(): String {
        return preferences.getString(KEY_BACKEND_API_KEY, null)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_BACKEND_API_KEY
    }

    fun getBackendBaseUrl(): String {
        val host = getBackendHost()
        val base = when {
            host.startsWith("http://") || host.startsWith("https://") -> host
            ':' in host -> "http://$host"
            else -> "http://$host:${getBackendPort()}"
        }
        return if (base.endsWith("/")) base else "$base/"
    }

    fun saveBackendConfig(
        host: String,
        port: Int,
        apiKey: String
    ) {
        preferences.edit()
            .putString(KEY_BACKEND_HOST, normalizeHost(host))
            .putString(KEY_BACKEND_PORT, port.coerceIn(1, 65_535).toString())
            .putString(KEY_BACKEND_API_KEY, apiKey.trim())
            .apply()
    }

    private fun normalizeHost(raw: String): String {
        return raw.trim()
            .removeSuffix("/")
            .ifBlank { DEFAULT_BACKEND_HOST }
    }

    fun resetBackendConfig() {
        preferences.edit()
            .remove(KEY_BACKEND_HOST)
            .remove(KEY_BACKEND_PORT)
            .remove(KEY_BACKEND_API_KEY)
            .apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "service_config_preferences"
        private const val KEY_BACKEND_HOST = "backend_host"
        private const val KEY_BACKEND_PORT = "backend_port"
        private const val KEY_BACKEND_API_KEY = "backend_api_key"

        const val DEFAULT_BACKEND_HOST = "192.168.0.101"
        const val DEFAULT_BACKEND_PORT = 8080
        const val DEFAULT_BACKEND_API_KEY = "P3njb9c72382cGJL39UU75jhg3904B4HH6J6LS3"
    }
}
