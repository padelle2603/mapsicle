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

    fun lastKnown(): Location? {
        return try {
            locationManager.getProviders(true)
                .mapNotNull { provider -> locationManager.getLastKnownLocation(provider) }
                .maxByOrNull(Location::getTime)
        } catch (_: SecurityException) {
            null
        }
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

        val provider = try {
            locationManager.getProviders(true)
                .firstOrNull { it == LocationManager.GPS_PROVIDER }
                ?: locationManager.getProviders(true).firstOrNull()
        } catch (_: SecurityException) {
            null
        }
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
        const val LOCATION_TIMEOUT_MS = 12_000L
    }
}
