package com.padelle.mapsicle

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager

/**
 * The permission half of the position: what has been granted, and what the app meant to do
 * when it asked. That second part is the reason this is a class and not four extension
 * functions: the answer to a permission dialog arrives in onRequestPermissionsResult, long
 * after the button that asked for it, and the intent in between has to live somewhere.
 *
 * The other half, the fix and the marker that follows it, stays in the Activity, because
 * lastUserFix is read by the routing, by the card of a POI and by the style reload, and
 * userLocated by the guidance. Those are the couplings that keep the continuous updates
 * there; this file is the part that was genuinely only about permissions.
 */
/** The dialog the app asks for, and the number the answer comes back with. */
internal const val LOCATION_REQUEST_CODE = 1001

internal class LocationAccess(private val activity: Activity) {
    /**
     * Read and cleared by consumePending(), so a second result for the same dialog cannot
     * run the action twice.
     */
    var pending: PendingAction = PendingAction.NONE

    fun hasFine(): Boolean = activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

    fun hasCoarse(): Boolean = activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

    fun hasAny(): Boolean = hasFine() || hasCoarse()

    fun request() {
        activity.requestPermissions(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
            LOCATION_REQUEST_CODE,
        )
    }

    fun consumePending(): PendingAction = pending.also { pending = PendingAction.NONE }

    enum class PendingAction {
        NONE,
        ACCEPT_ROUTE,
        SET_START_LOCATION,
        CENTER_ON_USER,
        ROUTE_TO_PLACE,
    }
}

internal fun Context.locationManager(): LocationManager =
    getSystemService(Context.LOCATION_SERVICE) as LocationManager
