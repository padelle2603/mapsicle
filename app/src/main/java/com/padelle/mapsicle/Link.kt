package com.padelle.mapsicle

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * Opens a link in whatever app is willing to take it. A VIEW Intent with nothing able to
 * handle it throws ActivityNotFoundException, and a Toast is better than a crash. It serves
 * the link to a POI on Google Maps and the download of an update, which is why it lives on
 * Context and not in the Activity that happens to call it today.
 */
internal fun Context.openExternalUrl(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, R.string.error_no_app_for_link, Toast.LENGTH_SHORT).show()
    }
}

/** The Activity is already gone, so anything that posts back to it has to stop here. */
internal fun Activity.isGone(): Boolean = isFinishing || isDestroyed
