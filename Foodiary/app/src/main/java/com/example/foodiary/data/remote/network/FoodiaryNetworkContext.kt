package com.example.foodiary.data.remote.network

import android.content.Context

object FoodiaryNetworkContext {

    @Volatile
    private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    fun context(): Context? = appContext
}
