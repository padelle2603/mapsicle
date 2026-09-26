package com.padelle.mapsicle

import android.content.Context
import android.util.TypedValue

/**
 * dp to pixels. It was a private method of the Activity, where the rows of the suggestion
 * list could not reach it, and it is two lines that every screen of this app needs.
 */
internal fun Context.dp(value: Int): Int = TypedValue.applyDimension(
    TypedValue.COMPLEX_UNIT_DIP,
    value.toFloat(),
    resources.displayMetrics,
).toInt()
