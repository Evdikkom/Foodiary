package com.example.foodiary.presentation.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class DeviceLocationProvider(context: Context) {

    private val appContext = context.applicationContext
    private val locationManager = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    fun hasLocationPermission(): Boolean {
        return hasFineLocationPermission() || hasCoarseLocationPermission()
    }

    suspend fun getCurrentOrLastKnownLocation(): Location? {
        if (!hasLocationPermission()) return null

        val lastKnown = getBestLastKnownLocation()
        if (lastKnown != null) return lastKnown

        return requestFreshLocation()
    }

    @SuppressLint("MissingPermission")
    private fun getBestLastKnownLocation(): Location? {
        return runCatching {
            locationManager.getProviders(true)
                .mapNotNull { provider ->
                    runCatching {
                        if (canReadProvider(provider)) {
                            locationManager.getLastKnownLocation(provider)
                        } else {
                            null
                        }
                    }.getOrNull()
                }
                .maxByOrNull { it.time }
        }.getOrNull()
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private suspend fun requestFreshLocation(): Location? {
        val provider = chooseProviderForFreshLocation() ?: return null
        return withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                var resumed = false
                lateinit var listener: LocationListener

                fun finish(location: Location?) {
                    if (resumed) return
                    resumed = true
                    runCatching { locationManager.removeUpdates(listener) }
                    if (continuation.isActive) {
                        continuation.resume(location)
                    }
                }

                listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        finish(location)
                    }

                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                    override fun onProviderEnabled(provider: String) = Unit
                    override fun onProviderDisabled(provider: String) {
                        finish(null)
                    }
                }

                continuation.invokeOnCancellation {
                    runCatching { locationManager.removeUpdates(listener) }
                }

                runCatching {
                    locationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                }.onFailure {
                    finish(null)
                }
            }
        }
    }

    private fun chooseProviderForFreshLocation(): String? {
        val providers = runCatching { locationManager.getProviders(true) }.getOrDefault(emptyList())
        return when {
            LocationManager.NETWORK_PROVIDER in providers && canReadProvider(LocationManager.NETWORK_PROVIDER) ->
                LocationManager.NETWORK_PROVIDER

            LocationManager.GPS_PROVIDER in providers && canReadProvider(LocationManager.GPS_PROVIDER) ->
                LocationManager.GPS_PROVIDER

            else -> providers.firstOrNull(::canReadProvider)
        }
    }

    private fun canReadProvider(provider: String): Boolean {
        return when (provider) {
            LocationManager.GPS_PROVIDER -> hasFineLocationPermission()
            else -> hasLocationPermission()
        }
    }

    private fun hasFineLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasCoarseLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private companion object {
        const val LOCATION_TIMEOUT_MS = 5_000L
    }
}
