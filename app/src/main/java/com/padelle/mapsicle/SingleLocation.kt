package com.padelle.mapsicle

import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executor

internal class SingleLocation(
    private val locationManager: LocationManager,
    private val handler: Handler,
    private val mainExecutor: Executor,
) {
    private var completed = false
    private var cancellationSignal: CancellationSignal? = null
    private var legacyListener: LocationListener? = null
    private var timeout: Runnable? = null

    /**
     * Last position already known to the system, without blocking the UI.
     *
     * Android keeps the last fix in memory long after the GPS has been switched off:
     * handing it back at once is what makes opening the app immediate instead of a
     * waiting screen. The filters throw away fixes that are too old or too imprecise (a
     * position from yesterday is worth nothing, one from 20 minutes ago is just fine).
     */
    fun lastKnown(
        maxAgeMs: Long = LAST_KNOWN_MAX_AGE_MS,
        maxAccuracyMeters: Float = LAST_KNOWN_MAX_ACCURACY_METERS,
    ): Location? {
        val now = System.currentTimeMillis()
        return try {
            locationManager.getProviders(true)
                .mapNotNull { provider -> locationManager.getLastKnownLocation(provider) }
                .filter { location ->
                    val age = now - location.time
                    age in 0..maxAgeMs &&
                        (location.accuracy <= 0f || location.accuracy <= maxAccuracyMeters)
                }
                .maxByOrNull(Location::getTime)
        } catch (_: SecurityException) {
            null
        }
    }

    /**
     * Fused when there is one: it estimates the position by combining GPS and network,
     * so it is fast and accurate at the same time. Network is the second choice because
     * it answers in 1-2 seconds while a cold GPS can take 30. The GPS comes last, only
     * when there is nothing else.
     */
    private fun preferredProvider(): String? {
        val enabled = try {
            locationManager.getProviders(true).toList()
        } catch (_: SecurityException) {
            return null
        }
        return PROVIDER_PREFERENCE.firstOrNull { it in enabled } ?: enabled.firstOrNull()
    }

    fun request(callback: (Location?) -> Unit) {
        cancel()
        completed = false

        fun complete(location: Location?) {
            if (completed) {
                return
            }
            completed = true
            release()
            callback(location)
        }

        val provider = preferredProvider()
        if (provider == null) {
            complete(null)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val signal = CancellationSignal()
            cancellationSignal = signal
            try {
                locationManager.getCurrentLocation(provider, signal, mainExecutor) { location ->
                    complete(location ?: lastKnown())
                }
            } catch (_: SecurityException) {
                complete(lastKnown())
            }
        } else {
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    complete(location)
                }

                override fun onProviderEnabled(provider: String) = Unit

                override fun onProviderDisabled(provider: String) = Unit

                @Suppress("DEPRECATION")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            }
            legacyListener = listener
            try {
                locationManager.requestLocationUpdates(
                    provider,
                    0L,
                    0f,
                    listener,
                    Looper.getMainLooper(),
                )
            } catch (_: SecurityException) {
                complete(lastKnown())
            }
        }

        val timer = Runnable { complete(lastKnown()) }
        timeout = timer
        handler.postDelayed(timer, LOCATION_TIMEOUT_MS)
    }

    fun cancel() {
        completed = true
        release()
    }

    private fun release() {
        legacyListener?.let { listener ->
            try {
                locationManager.removeUpdates(listener)
            } catch (_: SecurityException) {
            }
        }
        legacyListener = null
        cancellationSignal?.cancel()
        cancellationSignal = null
        timeout?.let(handler::removeCallbacks)
        timeout = null
    }

    private companion object {
        // With fused/network the fix arrives in 1-2s: past 4s the last known position
        // is better than a waiting screen.
        const val LOCATION_TIMEOUT_MS = 4_000L
        const val LAST_KNOWN_MAX_AGE_MS = 30L * 60L * 1000L
        const val LAST_KNOWN_MAX_ACCURACY_METERS = 1_000f
        val PROVIDER_PREFERENCE = listOf(
            LocationManager.FUSED_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER,
        )
    }
}
