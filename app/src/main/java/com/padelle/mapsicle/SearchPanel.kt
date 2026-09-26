package com.padelle.mapsicle

import android.app.Activity
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView

/**
 * The two route fields, the list of suggestions under them and the line that says what that
 * list is doing. It owns the three pieces of state nothing else reads: which field has the
 * focus, what came back last, and the flag that stops the watcher while the Activity writes
 * a chosen place into a field.
 *
 * What it does not own is what a place means for the route. The caller is told that the
 * departure or the destination changed and deals with the marker, the camera and the
 * invalidation itself, because those three are still tangled with the state of the routing
 * and that is the part of MainActivity this split deliberately leaves alone.
 */
internal class SearchPanel(
    private val activity: Activity,
    private val startInput: EditText,
    private val destinationInput: EditText,
    private val resultsContainer: ViewGroup,
    private val resultsScroll: View,
    private val statusLabel: TextView,
    private val suggestions: Suggestions,
    private val onDepartureChanged: (SearchPlace?) -> Unit,
    private val onDestinationChanged: (SearchPlace?) -> Unit,
) {
    private var activeInput: EditText? = null
    private var latest: List<SearchPlace> = emptyList()
    private var writing = false

    /** Call once, when the views exist: it wires the fields and the callbacks of the fetch. */
    fun attach() {
        configureField(startInput)
        configureField(destinationInput)
        suggestions.onSearching = {
            // Only if there is nothing to show yet: otherwise the "searching" text
            // blinks on every key pressed.
            if (latest.isEmpty()) {
                setStatus(activity.getString(R.string.searching_suggestions))
            }
        }
        suggestions.onResults = { results -> showResults(results) }
        suggestions.onError = { setStatus(activity.getString(R.string.suggestions_error)) }
    }

    private fun configureField(field: EditText) {
        field.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                activeInput = field
                if (field.text.toString().trim().length >= MIN_QUERY_LENGTH) {
                    suggestions.request(field.text.toString(), immediate = true)
                } else {
                    clear()
                }
            }
        }
        field.setOnClickListener { activeInput = field }
        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(text: Editable?) {
                if (writing) {
                    return
                }
                // Typing over a chosen place means it is no longer chosen. The caller drops
                // the marker and invalidates the route; the panel only knows the text.
                if (field === startInput) {
                    onDepartureChanged(null)
                } else {
                    onDestinationChanged(null)
                }
                if (activeInput === field) {
                    val value = text?.toString().orEmpty()
                    if (value.trim().length >= MIN_QUERY_LENGTH) {
                        suggestions.request(value)
                    } else {
                        clear()
                    }
                }
            }
        })
        field.setOnEditorActionListener { _, actionId, event ->
            val submitted = actionId == EditorInfo.IME_ACTION_SEARCH ||
                event?.keyCode == KeyEvent.KEYCODE_ENTER
            if (submitted) {
                if (latest.isNotEmpty()) {
                    pick(latest.first())
                } else {
                    suggestions.request(field.text.toString(), immediate = true)
                }
                true
            } else {
                false
            }
        }
    }

    private fun showResults(results: List<SearchPlace>) {
        latest = results
        resultsContainer.removeAllViews()
        if (results.isEmpty()) {
            resultsScroll.visibility = View.GONE
            setStatus(activity.getString(R.string.suggestions_empty))
            return
        }
        resultsScroll.visibility = View.VISIBLE
        setStatus("")
        results.forEach { place -> resultsContainer.addView(suggestionRow(place)) }
    }

    private fun suggestionRow(place: SearchPlace): TextView = TextView(activity).apply {
        text = place.displayName
        textSize = 15f
        setTextColor(activity.getColor(R.color.text_primary))
        setPadding(activity.dp(12), activity.dp(10), activity.dp(12), activity.dp(10))
        minHeight = activity.dp(52)
        isClickable = true
        contentDescription = place.displayName
        setOnClickListener { pick(place) }
    }

    private fun pick(place: SearchPlace) {
        val field = activeInput ?: return
        if (field === startInput) {
            onDepartureChanged(place)
        } else {
            onDestinationChanged(place)
        }
        clear()
        field.clearFocus()
    }

    fun clear() {
        suggestions.clear()
        resultsContainer.removeAllViews()
        resultsScroll.visibility = View.GONE
        latest = emptyList()
        setStatus("")
    }

    /**
     * Writes a chosen place into a field. The watcher is held off, because the text it would
     * see is the name of the place that was just chosen and it would read that as the user
     * having typed it, clearing the place again.
     */
    fun writeStart(text: String) {
        writing = true
        startInput.setText(text)
        writing = false
    }

    fun writeDestination(text: String) {
        writing = true
        destinationInput.setText(text)
        writing = false
    }

    /**
     * Empties both fields, the way clearItinerary did it: without holding the watcher off.
     * The watcher does run, and it clears the places, which is what clearing the itinerary
     * wants anyway. Same as before, not an accident worth fixing in a commit that only moves
     * code.
     */
    fun clearFields() {
        startInput.setText("")
        destinationInput.setText("")
    }

    private fun setStatus(message: String) {
        statusLabel.text = message
        statusLabel.visibility = if (message.isBlank()) View.GONE else View.VISIBLE
    }
}
