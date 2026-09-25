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
     * Ultima posizione gia' nota dal sistema, senza bloccare l'UI.
     *
     * Android mantiene l'ultimo fix in memoria anche a lungo dopo che il GPS si e' spento:
     * restituirlo subito e' quello che rende l'apertura dell'app immediata invece che
     * una schermata di attesa. I filtri buttano via i fix troppo vecchi o troppo imprecisi
     * (una posizione di ieri non serve a nulla, ma una di 20 minuti fa va benissimo).
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
     * Fused quando c'': stima la posizione combinando GPS e rete, quindi e' veloce
     * e precisa allo stesso tempo. La rete e' il secondo preferito perche' risponde in
     * 1-2 secondi mentre il GPS a freddo puo' mettercene 30. Il GPS arriva per ultimo,
     * solo quando non c'e' altro.
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
        // Con fused/network il fix arriva in 1-2s: oltre i 4s meglio l'ultimo noto
        // che una schermata di attesa.
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
