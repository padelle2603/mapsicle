package com.padelle.mapsicle

import android.os.Handler
import java.util.concurrent.Executor

internal class Suggestions(
    private val handler: Handler,
    private val executor: Executor,
    private val language: String,
    private val fetchJson: (String) -> String,
) {
    private var pending: Runnable? = null
    private var generation = 0
    private var inFlight = false
    private var droppedWhileBusy = false
    private var latestQuery: String? = null

    // Punto su cui Photon basa il ranking. Null = nessun bias, cioe' il comportamento
    // di prima: la app non conosce la posizione finche' il permesso non e' concesso.
    var center: SearchCenter? = null

    var onSearching: (() -> Unit)? = null
    var onResults: ((List<SearchPlace>) -> Unit)? = null
    var onError: (() -> Unit)? = null

    fun request(rawQuery: String, immediate: Boolean = false) {
        val query = rawQuery.trim()
        latestQuery = query
        generation += 1
        val gen = generation
        removePending()
        if (query.length < MIN_QUERY_LENGTH) {
            return
        }
        val runnable = Runnable { fetch(query, gen) }
        pending = runnable
        handler.postDelayed(runnable, if (immediate) 0L else DEBOUNCE_MS)
    }

    fun clear() {
        generation += 1
        latestQuery = null
        removePending()
    }

    fun cancel() {
        clear()
        onSearching = null
        onResults = null
        onError = null
    }

    private fun fetch(query: String, gen: Int) {
        if (gen != generation) {
            return
        }
        if (inFlight) {
            // Non perdere la richiesta: verrà recuperata al completamento di quella in corso.
            droppedWhileBusy = true
            return
        }
        inFlight = true
        onSearching?.invoke()
        executor.execute {
            val result = runCatching {
                parseSuggestions(fetchJson(buildSuggestionUrl(query, language, center)))
            }
            handler.post {
                inFlight = false
                // Mostra quello che e' arrivato invece di scartarlo: Photon risponde in
                // ~1.6s, buttare i risultati e rifare la richiesta raddoppia l'attesa
                // percepita. Poi si affina con la query piu' recente.
                result.onSuccess { onResults?.invoke(it) }
                    .onFailure { if (gen == generation) onError?.invoke() }
                if (gen != generation || droppedWhileBusy) {
                    droppedWhileBusy = false
                    latestQuery?.let { request(it, immediate = true) }
                }
            }
        }
    }

    private fun removePending() {
        pending?.let(handler::removeCallbacks)
        pending = null
    }

    private companion object {
        const val DEBOUNCE_MS = 180L
        const val MIN_QUERY_LENGTH = 3
    }
}
