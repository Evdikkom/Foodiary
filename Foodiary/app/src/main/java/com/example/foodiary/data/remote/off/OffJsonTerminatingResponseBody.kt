package com.example.foodiary.data.remote.off

import okhttp3.MediaType
import okhttp3.ResponseBody
import okio.BufferedSource
import okio.Buffer
import okio.Source
import okio.Timeout
import okio.buffer
import java.io.EOFException

internal class OffJsonTerminatingResponseBody(
    private val delegate: ResponseBody,
    private val requestId: String,
    private val endpointName: String,
    private val startedAtNanos: Long
) : ResponseBody() {

    private val terminatingSource: BufferedSource by lazy {
        JsonTerminatingSource(
            upstream = delegate.source(),
            requestId = requestId,
            endpointName = endpointName,
            startedAtNanos = startedAtNanos
        ).buffer()
    }

    override fun contentType(): MediaType? = delegate.contentType()

    override fun contentLength(): Long = -1L

    override fun source(): BufferedSource = terminatingSource

    override fun close() {
        delegate.close()
    }

    private class JsonTerminatingSource(
        private val upstream: BufferedSource,
        private val requestId: String,
        private val endpointName: String,
        private val startedAtNanos: Long
    ) : Source {

        private var rootStarted = false
        private var depth = 0
        private var inString = false
        private var escaping = false
        private var complete = false
        private var loggedCompletion = false
        private var bytesRead = 0L

        override fun read(sink: Buffer, byteCount: Long): Long {
            if (byteCount == 0L) return 0L
            if (complete) return -1L

            var emitted = 0L
            while (emitted < byteCount && !complete) {
                val value = try {
                    upstream.readByte().toInt() and 0xff
                } catch (_: EOFException) {
                    complete = true
                    logCompletion("upstreamEof")
                    break
                }

                sink.writeByte(value)
                emitted++
                bytesRead++
                updateJsonState(value)
            }

            return if (emitted > 0L) emitted else -1L
        }

        override fun timeout(): Timeout = upstream.timeout()

        override fun close() {
            upstream.close()
        }

        private fun updateJsonState(value: Int) {
            if (!rootStarted) {
                when (value) {
                    OPEN_OBJECT, OPEN_ARRAY -> {
                        rootStarted = true
                        depth = 1
                    }
                }
                return
            }

            if (inString) {
                when {
                    escaping -> escaping = false
                    value == BACKSLASH -> escaping = true
                    value == QUOTE -> inString = false
                }
                return
            }

            when (value) {
                QUOTE -> inString = true
                OPEN_OBJECT, OPEN_ARRAY -> depth++
                CLOSE_OBJECT, CLOSE_ARRAY -> {
                    depth--
                    if (depth <= 0) {
                        complete = true
                        logCompletion("balancedJson")
                    }
                }
            }
        }

        private fun logCompletion(reason: String) {
            if (loggedCompletion) return
            loggedCompletion = true
            val elapsedMs = (System.nanoTime() - startedAtNanos) / 1_000_000
            OffNetworkDebugLogger.log(
                "OFF response body completed id=$requestId endpoint=$endpointName " +
                    "reason=$reason bytes=$bytesRead elapsedMs=$elapsedMs"
            )
        }

        private companion object {
            private const val QUOTE = 34
            private const val BACKSLASH = 92
            private const val OPEN_OBJECT = 123
            private const val CLOSE_OBJECT = 125
            private const val OPEN_ARRAY = 91
            private const val CLOSE_ARRAY = 93
        }
    }
}
