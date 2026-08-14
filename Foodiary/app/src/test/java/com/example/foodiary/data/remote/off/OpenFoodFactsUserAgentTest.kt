package com.example.foodiary.data.remote.off

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenFoodFactsUserAgentTest {

    @Test
    fun `uses stable app contact email even when user email is valid`() {
        val userAgent = OpenFoodFactsUserAgent.build(
            appVersion = "1.0",
            contactEmail = "  User@Example.COM "
        )

        assertEquals("FoodiaryApp/1.0 (evdikkom2004@mail.ru)", userAgent)
    }

    @Test
    fun `uses stable app contact email when user email is blank or invalid`() {
        assertEquals(
            "FoodiaryApp/1.0 (evdikkom2004@mail.ru)",
            OpenFoodFactsUserAgent.build(appVersion = "1.0", contactEmail = "")
        )
        assertEquals(
            "FoodiaryApp/1.0 (evdikkom2004@mail.ru)",
            OpenFoodFactsUserAgent.build(appVersion = "1.0", contactEmail = "wrong")
        )
    }

    @Test
    fun `sanitizes app version for http header safety`() {
        val userAgent = OpenFoodFactsUserAgent.build(
            appVersion = " 1.0 beta\r\nX-Test ",
            contactEmail = "student@example.org"
        )

        assertEquals("FoodiaryApp/1.0betaX-Test (evdikkom2004@mail.ru)", userAgent)
        assertFalse(userAgent.contains("\r"))
        assertFalse(userAgent.contains("\n"))
        assertTrue(userAgent.contains("@"))
    }

    @Test
    fun `contains product name and contact for Open Food Facts identification`() {
        val userAgent = OpenFoodFactsUserAgent.build(
            appVersion = "2.3",
            contactEmail = "student@example.org"
        )

        assertTrue(userAgent.startsWith("FoodiaryApp/2.3"))
        assertTrue(userAgent.contains("evdikkom2004@mail.ru"))
    }

    @Test
    fun `contact token keeps email information without raw email header characters`() {
        assertEquals(
            "student-at-example-dot-org",
            OpenFoodFactsUserAgent.contactToken("student@example.org")
        )
    }
}
