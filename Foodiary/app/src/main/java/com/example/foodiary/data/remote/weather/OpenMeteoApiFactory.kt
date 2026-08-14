package com.example.foodiary.data.remote.weather

import com.example.foodiary.data.remote.network.VpnAwareOkHttp
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object OpenMeteoApiFactory {

    private const val BASE_URL = "https://api.open-meteo.com/"

    fun create(): OpenMeteoApi {
        val userAgentInterceptor = Interceptor { chain ->
            val request = chain.request()
                .newBuilder()
                .header("User-Agent", "Foodiary-Android/1.0")
                .header("Accept", "application/json")
                .build()
            chain.proceed(request)
        }

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val client = VpnAwareOkHttp.applyTo(OkHttpClient.Builder())
            .addInterceptor(userAgentInterceptor)
            .addInterceptor(loggingInterceptor)
            .retryOnConnectionFailure(true)
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .callTimeout(7, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenMeteoApi::class.java)
    }
}
