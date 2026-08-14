package com.example.foodiary.data.remote.off

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.foodiary.BuildConfig
import com.example.foodiary.data.local.preferences.LocalAccountPreferences
import com.example.foodiary.data.remote.network.FoodiaryNetworkContext
import com.example.foodiary.data.remote.network.VpnAwareOkHttp
import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

object OpenFoodFactsApiFactory {

    private const val ENDPOINT_PRODUCTION = "production"
    private const val ENDPOINT_STAGING_FALLBACK = "staging-fallback"
    internal const val BASE_URL = "https://world.openfoodfacts.org/"
    internal const val FALLBACK_BASE_URL = "https://world.openfoodfacts.net/"
    private const val MAX_RETRIES = 1
    private const val HTTP_RETRY_DELAY_MS = 900L
    private val RETRYABLE_HTTP_CODES = setOf(429, 502, 503, 504)

    fun create(context: Context? = null): OpenFoodFactsApi {
        val productionApi = createApi(
            baseUrl = BASE_URL,
            endpointName = ENDPOINT_PRODUCTION,
            context = context,
            maxRetries = MAX_RETRIES,
            connectTimeoutMillis = 4_000L,
            readTimeoutMillis = 7_000L,
            writeTimeoutMillis = 8_000L,
            callTimeoutMillis = 9_000L,
            useStagingAuth = false
        )
        val productionBarcodeApi = createApi(
            baseUrl = BASE_URL,
            endpointName = "$ENDPOINT_PRODUCTION-barcode-fast",
            context = context,
            maxRetries = 0,
            connectTimeoutMillis = 900L,
            readTimeoutMillis = 1_500L,
            writeTimeoutMillis = 1_500L,
            callTimeoutMillis = 1_800L,
            useStagingAuth = false
        )
        val stagingApi = createApi(
            baseUrl = FALLBACK_BASE_URL,
            endpointName = ENDPOINT_STAGING_FALLBACK,
            context = context,
            maxRetries = MAX_RETRIES,
            connectTimeoutMillis = 6_000L,
            readTimeoutMillis = 30_000L,
            writeTimeoutMillis = 12_000L,
            callTimeoutMillis = 35_000L,
            useStagingAuth = true
        )
        val stagingBarcodeApi = createApi(
            baseUrl = FALLBACK_BASE_URL,
            endpointName = "$ENDPOINT_STAGING_FALLBACK-barcode",
            context = context,
            maxRetries = 0,
            connectTimeoutMillis = 2_000L,
            readTimeoutMillis = 5_000L,
            writeTimeoutMillis = 3_000L,
            callTimeoutMillis = 6_000L,
            useStagingAuth = true
        )

        val endpoints = listOf(
            OpenFoodFactsFallbackApi.Endpoint(
                name = ENDPOINT_PRODUCTION,
                failureCooldownMillis = 120_000L,
                api = productionApi,
                barcodeApi = productionBarcodeApi
            ),
            OpenFoodFactsFallbackApi.Endpoint(
                name = ENDPOINT_STAGING_FALLBACK,
                failureCooldownMillis = 0L,
                api = stagingApi,
                barcodeApi = stagingBarcodeApi
            )
        )

        val appContext = context?.applicationContext ?: FoodiaryNetworkContext.context()

        return OpenFoodFactsFallbackApi(
            endpoints = endpoints,
            searchEndpointPreference = {
                preferredSearchEndpoint(appContext)
            }
        )
    }

    private fun createApi(
        baseUrl: String,
        endpointName: String,
        context: Context?,
        maxRetries: Int,
        connectTimeoutMillis: Long,
        readTimeoutMillis: Long,
        writeTimeoutMillis: Long,
        callTimeoutMillis: Long,
        useStagingAuth: Boolean
    ): OpenFoodFactsApi {
        return createRetrofit(
            baseUrl = baseUrl,
            endpointName = endpointName,
            context = context,
            maxRetries = maxRetries,
            connectTimeoutMillis = connectTimeoutMillis,
            readTimeoutMillis = readTimeoutMillis,
            writeTimeoutMillis = writeTimeoutMillis,
            callTimeoutMillis = callTimeoutMillis,
            useStagingAuth = useStagingAuth
        ).create(OpenFoodFactsApi::class.java)
    }

    private fun createRetrofit(
        baseUrl: String,
        endpointName: String,
        context: Context?,
        maxRetries: Int,
        connectTimeoutMillis: Long,
        readTimeoutMillis: Long,
        writeTimeoutMillis: Long,
        callTimeoutMillis: Long,
        useStagingAuth: Boolean
    ): Retrofit {
        val userAgentProvider = OpenFoodFactsUserAgentProvider(
            accountPreferences = context?.applicationContext?.let(::LocalAccountPreferences),
            appVersion = BuildConfig.VERSION_NAME
        )
        val appContext = context?.applicationContext ?: FoodiaryNetworkContext.context()

        val userAgentInterceptor = Interceptor { chain ->
            val requestBuilder = chain.request()
                .newBuilder()
                .header("User-Agent", userAgentProvider.currentUserAgent())
                .header("Accept", "application/json")
                .header("Accept-Encoding", "identity")
                .header("Connection", "close")

            if (useStagingAuth) {
                requestBuilder.header("Authorization", Credentials.basic("off", "off"))
            }

            val request = requestBuilder
                .build()

            chain.proceed(request)
        }

        val debugInterceptor = Interceptor { chain ->
            val request = chain.request()
            val startedAtNanos = System.nanoTime()
            val requestId = "${endpointName}-${startedAtNanos.toString(36)}"
            OffNetworkDebugLogger.log(
                buildString {
                    appendLine("OFF request start id=$requestId endpoint=$endpointName")
                    appendLine("method=${request.method} url=${request.url}")
                    append(OffNetworkDiagnostics.snapshot(appContext, request.url.host))
                }
            )

            try {
                val response = chain.proceed(request)
                val elapsedMs = (System.nanoTime() - startedAtNanos) / 1_000_000
                OffNetworkDebugLogger.log(
                    "OFF response id=$requestId endpoint=$endpointName code=${response.code} " +
                        "message=${response.message} protocol=${response.protocol} " +
                        "contentType=${response.header("Content-Type").orEmpty()} " +
                        "contentEncoding=${response.header("Content-Encoding").orEmpty()} " +
                        "contentLength=${response.header("Content-Length").orEmpty()} " +
                        "elapsedMs=$elapsedMs"
                )
                response.withEarlyTerminatingSearchBody(
                    request = request,
                    requestId = requestId,
                    endpointName = endpointName,
                    startedAtNanos = startedAtNanos
                )
            } catch (error: Exception) {
                val elapsedMs = (System.nanoTime() - startedAtNanos) / 1_000_000
                OffNetworkDebugLogger.log(
                    "OFF request failed id=$requestId endpoint=$endpointName elapsedMs=$elapsedMs",
                    error
                )
                throw error
            }
        }

        val retryInterceptor = Interceptor { chain ->
            var attempt = 0
            var lastException: IOException? = null

            while (attempt <= maxRetries) {
                try {
                    val response = chain.proceed(chain.request())
                    if (!shouldRetryResponse(response) || attempt >= maxRetries) {
                        return@Interceptor response
                    }

                    val retryDelayMillis = retryDelayMillis(response, attempt)
                    response.close()
                    Thread.sleep(retryDelayMillis)
                    attempt++
                } catch (e: IOException) {
                    lastException = e

                    val shouldRetry = shouldRetryRequest(e)
                    val hasAttemptsLeft = attempt < maxRetries

                    if (!shouldRetry || !hasAttemptsLeft) {
                        throw e
                    }

                    Thread.sleep(350L)
                    attempt++
                }
            }

            throw lastException ?: IOException("\u041d\u0435 \u0443\u0434\u0430\u043b\u043e\u0441\u044c \u0432\u044b\u043f\u043e\u043b\u043d\u0438\u0442\u044c \u0437\u0430\u043f\u0440\u043e\u0441")
        }

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val client = VpnAwareOkHttp.applyTo(OkHttpClient.Builder())
            .addInterceptor(debugInterceptor)
            .addInterceptor(userAgentInterceptor)
            .addInterceptor(retryInterceptor)
            .addInterceptor(loggingInterceptor)
            .retryOnConnectionFailure(true)
            .protocols(listOf(Protocol.HTTP_1_1))
            .connectTimeout(connectTimeoutMillis, TimeUnit.MILLISECONDS)
            .readTimeout(readTimeoutMillis, TimeUnit.MILLISECONDS)
            .writeTimeout(writeTimeoutMillis, TimeUnit.MILLISECONDS)
            .callTimeout(callTimeoutMillis, TimeUnit.MILLISECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private fun shouldRetryRequest(error: IOException): Boolean {
        return when (error) {
            is UnknownHostException -> false
            is SSLException -> false
            is SocketTimeoutException -> true
            else -> true
        }
    }

    private fun shouldRetryResponse(response: Response): Boolean {
        return response.code in RETRYABLE_HTTP_CODES
    }

    private fun retryDelayMillis(response: Response, attempt: Int): Long {
        val retryAfterSeconds = response.header("Retry-After")
            ?.toLongOrNull()
            ?.times(1000L)

        return retryAfterSeconds
            ?.coerceIn(HTTP_RETRY_DELAY_MS, 3_000L)
            ?: HTTP_RETRY_DELAY_MS * (attempt + 1)
    }

    private fun Response.withEarlyTerminatingSearchBody(
        request: Request,
        requestId: String,
        endpointName: String,
        startedAtNanos: Long
    ): Response {
        val originalBody = body ?: return this
        if (!shouldTerminateSearchJsonEarly(request, this)) return this

        OffNetworkDebugLogger.log(
            "OFF response body early termination enabled id=$requestId endpoint=$endpointName"
        )
        return newBuilder()
            .body(
                OffJsonTerminatingResponseBody(
                    delegate = originalBody,
                    requestId = requestId,
                    endpointName = endpointName,
                    startedAtNanos = startedAtNanos
                )
            )
            .build()
    }

    private fun shouldTerminateSearchJsonEarly(request: Request, response: Response): Boolean {
        if (request.url.encodedPath != "/cgi/search.pl") return false
        if (!response.isSuccessful) return false
        if (response.header("Content-Encoding").orEmpty().isNotBlank()) return false

        return response.header("Content-Type")
            .orEmpty()
            .contains("json", ignoreCase = true)
    }

    private fun preferredSearchEndpoint(context: Context?): String? {
        if (context == null) return null

        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return null
        val activeNetwork = manager.activeNetwork ?: return null
        val activeCapabilities = manager.getNetworkCapabilities(activeNetwork) ?: return null

        return if (activeCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
            ENDPOINT_PRODUCTION
        } else {
            ENDPOINT_STAGING_FALLBACK
        }
    }
}
