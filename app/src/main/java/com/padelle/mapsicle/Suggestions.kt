package com.padelle.mapsicle

import android.os.Handler
import java.util.concurrent.Executor

// It was a private constant of Suggestions, mirrored as a second copy in the companion of
// MainActivity, and both had drifted into the class that now asks: the panel needs it to tell
// a request worth making from one that would only empty the list.
internal const val MIN_QUERY_LENGTH = 3

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

    // The point Photon ranks around. Null = no bias, that is the behaviour from before:
    // the app does not know the position until the permission is granted.
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
            // Do not lose the request: it is taken up again when the running one completes.
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
                // Show what arrived instead of dropping it: Photon answers in ~1.6s, so
                // throwing the results away and asking again doubles the perceived wait.
                // Then it refines with the more recent query.
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
    }
}
