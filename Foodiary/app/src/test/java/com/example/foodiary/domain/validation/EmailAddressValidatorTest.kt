package com.example.foodiary.domain.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailAddressValidatorTest {

    @Test
    fun `valid email is normalized`() {
        assertEquals(
            "user.name+foodiary@example.com",
            EmailAddressValidator.normalizeOrNull("  User.Name+Foodiary@Example.COM  ")
        )
    }

    @Test
    fun `blank and malformed emails are rejected`() {
        assertNull(EmailAddressValidator.normalizeOrNull(""))
        assertNull(EmailAddressValidator.normalizeOrNull("not-an-email"))
        assertNull(EmailAddressValidator.normalizeOrNull("user@example"))
        assertFalse(EmailAddressValidator.isValid("user@example"))
    }

    @Test
    fun `unsafe header characters are rejected`() {
        assertFalse(EmailAddressValidator.isValid("user@example.com\r\nInjected: true"))
        assertFalse(EmailAddressValidator.isValid("user@example.com;token"))
        assertFalse(EmailAddressValidator.isValid("user@example.com,second@example.com"))
    }

    @Test
    fun `regular email is valid`() {
        assertTrue(EmailAddressValidator.isValid("student@example.org"))
    }
}
