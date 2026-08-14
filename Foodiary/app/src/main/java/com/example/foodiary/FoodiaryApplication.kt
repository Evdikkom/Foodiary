package com.example.foodiary

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.example.foodiary.data.remote.off.OffNetworkDebugLogger
import com.example.foodiary.data.remote.network.FoodiaryNetworkContext
import com.example.foodiary.data.remote.network.VpnAwareOkHttp
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.util.concurrent.TimeUnit

class FoodiaryApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        FoodiaryNetworkContext.initialize(this)
        OffNetworkDebugLogger.cleanupIfNeeded(this)
    }

    override fun newImageLoader(): ImageLoader {
        val imageClient = VpnAwareOkHttp.applyTo(OkHttpClient.Builder())
            .addInterceptor { chain ->
                val request = chain.request()
                    .newBuilder()
                    .header("User-Agent", "FoodiaryApp/${BuildConfig.VERSION_NAME} (evdikkom2004@mail.ru)")
                    .header("Accept-Encoding", "identity")
                    .header("Connection", "close")
                    .build()

                chain.proceed(request)
            }
            .protocols(listOf(Protocol.HTTP_1_1))
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(imageClient)
            .crossfade(true)
            .build()
    }
}
