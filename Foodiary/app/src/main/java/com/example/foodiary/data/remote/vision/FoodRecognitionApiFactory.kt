package com.example.foodiary.data.remote.vision

import android.content.Context
import com.example.foodiary.data.local.preferences.ServiceConfigPreferences
import com.example.foodiary.data.remote.network.VpnAwareOkHttp
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object FoodRecognitionApiFactory {

    fun create(context: Context): FoodRecognitionApi {
        val serviceConfig = ServiceConfigPreferences(context)
        val apiKey = serviceConfig.getBackendApiKey()
        val baseUrl = ensureTrailingSlash(serviceConfig.getBackendBaseUrl())

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val apiKeyInterceptor = Interceptor { chain ->
            val request = chain.request()
                .newBuilder()
                .addHeader("X-API-Key", apiKey)
                .addHeader("Accept", "application/json")
                .build()

            chain.proceed(request)
        }

        val client = VpnAwareOkHttp.applyTo(
            builder = OkHttpClient.Builder(),
            ipv4Only = true,
            bypassVpnWhenActive = true
        )
            .retryOnConnectionFailure(false)
            .addInterceptor(apiKeyInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(FoodRecognitionApi::class.java)
    }

    private fun ensureTrailingSlash(url: String): String {
        return if (url.endsWith("/")) url else "$url/"
    }
}
