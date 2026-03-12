package com.example.foodiary.data.remote.off

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

object OpenFoodFactsApiFactory {

    private const val BASE_URL = "https://world.openfoodfacts.net/"
    private const val MAX_RETRIES = 1

    fun create(): OpenFoodFactsApi {
        val userAgentInterceptor = Interceptor { chain ->
            val request = chain.request()
                .newBuilder()
                .header("User-Agent", "Foodiary-Android/1.0")
                .header("Accept", "application/json")
                .build()

            chain.proceed(request)
        }

        val retryInterceptor = Interceptor { chain ->
            var attempt = 0
            var lastException: IOException? = null

            while (attempt <= MAX_RETRIES) {
                try {
                    return@Interceptor chain.proceed(chain.request())
                } catch (e: IOException) {
                    lastException = e

                    val shouldRetry = shouldRetryRequest(e)
                    val hasAttemptsLeft = attempt < MAX_RETRIES

                    if (!shouldRetry || !hasAttemptsLeft) {
                        throw e
                    }

                    Thread.sleep(350L)
                    attempt++
                }
            }

            throw lastException ?: IOException("Не удалось выполнить запрос")
        }

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(userAgentInterceptor)
            .addInterceptor(retryInterceptor)
            .addInterceptor(loggingInterceptor)
            .retryOnConnectionFailure(true)
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .callTimeout(6, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(OpenFoodFactsApi::class.java)
    }

    private fun shouldRetryRequest(error: IOException): Boolean {
        return when (error) {
            is UnknownHostException -> false
            is SSLException -> false
            is SocketTimeoutException -> true
            else -> true
        }
    }
}