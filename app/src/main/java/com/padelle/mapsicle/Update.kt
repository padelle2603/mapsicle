package com.padelle.mapsicle

import android.app.Activity
import android.app.AlertDialog
import android.os.Handler
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executor

internal const val LATEST_RELEASE_URL =
    "https://api.github.com/repos/padelle2603/mapsicle/releases/latest"

internal data class AppRelease(
    val version: String,
    val apkUrl: String?,
    val notesUrl: String,
)

/**
 * GitHub returns the last published release (never a draft or a prerelease). No
 * authentication is needed: the User-Agent the app already sends on every request is
 * enough. That is 60 requests an hour per IP, one per app start: once the quota runs out
 * the call fails and nothing happens, no error on screen.
 */
internal fun parseLatestRelease(json: String): AppRelease? {
    val release = runCatching { JSONObject(json) }.getOrNull() ?: return null
    val version = release.optString("tag_name").removePrefix("v")
    if (version.isBlank()) {
        return null
    }
    // With --generate-notes the body holds nothing but "Full Changelog": useless in a
    // dialog, so only the link to the notes is used.
    val assets = release.optJSONArray("assets") ?: JSONArray()
    val apkUrl = (0 until assets.length())
        .mapNotNull { assets.optJSONObject(it) }
        .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
        ?.optString("browser_download_url")
        ?.takeIf { it.isNotBlank() }
    val notesUrl = release.optString("html_url")
    // With no link at all there is nothing to open: no popup is better than an Intent
    // carrying an empty string.
    if (apkUrl == null && notesUrl.isBlank()) {
        return null
    }
    return AppRelease(
        version = version,
        apkUrl = apkUrl,
        notesUrl = notesUrl,
    )
}

/**
 * Numeric per component, not by string: with a lexicographic comparison "1.10.0" >
 * "1.9.0" would come out the other way round. A component that is not a number (a debug
 * build with versionName "0.1", "-rc1") counts as 0 instead of breaking the comparison.
 */
internal fun isNewerVersion(remote: String, local: String): Boolean {
    val remoteParts = versionParts(remote)
    val localParts = versionParts(local)
    val length = maxOf(remoteParts.size, localParts.size)
    for (index in 0 until length) {
        val remotePart = remoteParts.getOrElse(index) { 0 }
        val localPart = localParts.getOrElse(index) { 0 }
        if (remotePart != localPart) {
            return remotePart > localPart
        }
    }
    return false
}

private fun versionParts(version: String): List<Int> {
    return version.removePrefix("v").split('.').map { part ->
        part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
    }
}

/**
 * One request to GitHub to find out whether there is a release newer than this build. It
 * fails silently: no network, no new thread, no dialog, no writing to disk. The pool is
 * the one of the style, the search and the routing, handed in rather than created, and
 * fetchJson is the same httpGet the search uses.
 *
 * The two questions worth testing are answered by the functions above and tested in
 * UpdateTest; what is left here is the glue between them and a dialog, which is not worth
 * a test of its own.
 */
internal class UpdateCheck(
    private val activity: Activity,
    private val handler: Handler,
    private val executor: Executor,
    private val currentVersion: String,
    private val fetchJson: (String) -> String,
) {
    fun check() {
        executor.execute {
            val release = runCatching {
                parseLatestRelease(fetchJson(LATEST_RELEASE_URL))
            }.getOrNull() ?: return@execute
            if (!isNewerVersion(release.version, currentVersion)) {
                return@execute
            }
            handler.post {
                if (activity.isGone()) {
                    return@post
                }
                showDialog(release)
            }
        }
    }

    private fun showDialog(release: AppRelease) {
        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.update_available, release.version))
            .setMessage(activity.getString(R.string.update_message, currentVersion))
            .setPositiveButton(R.string.update_download) { _, _ ->
                activity.openExternalUrl(release.apkUrl ?: release.notesUrl)
            }
            .setNegativeButton(R.string.update_not_now, null)
            .show()
    }
}
