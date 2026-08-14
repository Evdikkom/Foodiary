package com.example.foodiary.domain.validation

object EmailAddressValidator {

    private const val MAX_EMAIL_LENGTH = 254
    private val emailPattern = Regex(
        pattern = "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$",
        option = RegexOption.IGNORE_CASE
    )

    fun isValid(rawEmail: String?): Boolean {
        return normalizeOrNull(rawEmail) != null
    }

    fun normalizeOrNull(rawEmail: String?): String? {
        val email = rawEmail
            ?.trim()
            ?.takeIf { it.isNotBlank() && it.length <= MAX_EMAIL_LENGTH }
            ?: return null

        if (email.any(::isUnsafeHeaderCharacter)) return null
        if (!emailPattern.matches(email)) return null

        val parts = email.split("@", limit = 2)
        if (parts.size != 2) return null

        val localPart = parts[0]
        val domain = parts[1]
        if (localPart.isBlank() || domain.isBlank()) return null
        if (domain.startsWith(".") || domain.endsWith(".")) return null
        if (domain.contains("..")) return null

        return email.lowercase()
    }

    private fun isUnsafeHeaderCharacter(char: Char): Boolean {
        return char == '\r' ||
            char == '\n' ||
            char == '(' ||
            char == ')' ||
            char == '<' ||
            char == '>' ||
            char == '"' ||
            char == '\\' ||
            char == ';' ||
            char == ','
    }
}
