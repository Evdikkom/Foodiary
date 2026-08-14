package com.example.foodiary.data.remote.off

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test

class OffJsonTerminatingResponseBodyTest {

    @Test
    fun `stops reading after balanced root object`() {
        val json = """{"text":"brace } inside string","nested":{"ok":true}}"""
        val body = (json + "trailing bytes that must not be exposed")
            .toResponseBody("application/json".toMediaType())

        val terminatingBody = OffJsonTerminatingResponseBody(
            delegate = body,
            requestId = "test",
            endpointName = "staging-fallback",
            startedAtNanos = System.nanoTime()
        )

        assertEquals(json, terminatingBody.string())
    }

    @Test
    fun `stops reading after balanced root array`() {
        val json = """[{"name":"chips"},{"name":"soup"}]"""
        val body = (json + "\n\nignored")
            .toResponseBody("application/json".toMediaType())

        val terminatingBody = OffJsonTerminatingResponseBody(
            delegate = body,
            requestId = "test",
            endpointName = "staging-fallback",
            startedAtNanos = System.nanoTime()
        )

        assertEquals(json, terminatingBody.string())
    }
}
