package com.example.foodiary.data.remote.off

import com.example.foodiary.domain.validation.EmailAddressValidator

object OpenFoodFactsUserAgent {

    const val DEFAULT_CONTACT_EMAIL = "evdikkom2004@mail.ru"

    private const val APP_NAME = "FoodiaryApp"
    private const val FALLBACK_VERSION = "1.0"
    private val versionUnsafeCharacters = Regex("[^A-Za-z0-9._-]")
    private val contactTokenUnsafeCharacters = Regex("[^a-z0-9._+-]")

    @Suppress("UNUSED_PARAMETER")
    fun build(
        appVersion: String,
        contactEmail: String?
    ): String {
        val safeVersion = appVersion
            .trim()
            .replace(versionUnsafeCharacters, "")
            .ifBlank { FALLBACK_VERSION }

        return "$APP_NAME/$safeVersion ($DEFAULT_CONTACT_EMAIL)"
    }

    fun contactToken(rawEmail: String?): String {
        val email = EmailAddressValidator.normalizeOrNull(rawEmail)
            ?: DEFAULT_CONTACT_EMAIL

        return email
            .replace("@", "-at-")
            .replace(".", "-dot-")
            .replace(contactTokenUnsafeCharacters, "-")
            .trim('-')
            .ifBlank { DEFAULT_CONTACT_EMAIL.replace("@", "-at-").replace(".", "-dot-") }
    }
}
