package com.padelle.mapsicle

import org.json.JSONArray
import org.json.JSONObject

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
