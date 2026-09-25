package com.padelle.mapsicle

import android.os.Handler
import android.os.SystemClock
import java.util.concurrent.Executor

internal class Suggestions(
    private val handler: Handler,
    private val executor: Executor,
    private val language: String,
    private val fetchJson: (String) -> String,
) {
    private var pending: Runnable? = null
    private var generation = 0
    private var lastRequestAt = 0L
    private var inFlight = false
    private var latestQuery: String? = null

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
            return
        }
        val wait = THROTTLE_MS - (SystemClock.elapsedRealtime() - lastRequestAt)
        if (wait > 0) {
            val runnable = Runnable { fetch(query, gen) }
            pending = runnable
            handler.postDelayed(runnable, wait)
            return
        }
        inFlight = true
        lastRequestAt = SystemClock.elapsedRealtime()
        onSearching?.invoke()
        executor.execute {
            val result = runCatching {
                parseSuggestions(fetchJson(buildSuggestionUrl(query, language)))
            }
            handler.post {
                inFlight = false
                if (gen != generation) {
                    val next = latestQuery
                    if (next != null && next != query) {
                        request(next, immediate = true)
                    }
                    return@post
                }
                result.onSuccess { onResults?.invoke(it) }
                    .onFailure { onError?.invoke() }
            }
        }
    }

    private fun removePending() {
        pending?.let(handler::removeCallbacks)
        pending = null
    }

    private companion object {
        const val DEBOUNCE_MS = 300L
        const val THROTTLE_MS = 1_100L
        const val MIN_QUERY_LENGTH = 3
    }
}
