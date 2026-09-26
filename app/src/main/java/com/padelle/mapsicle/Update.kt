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
 * GitHub restituisce l'ultima release pubblicata (mai bozza o prerelease). Non serve
 * autenticazione: basta l'User-Agent che l'app gia' manda su ogni richiesta. Sono 60
 * richieste all'ora per IP, una per avvio dell'app: se la soglia finisce, la chiamata
 * fallisce e non succede niente, senza errori a schermo.
 */
internal fun parseLatestRelease(json: String): AppRelease? {
    val release = runCatching { JSONObject(json) }.getOrNull() ?: return null
    val version = release.optString("tag_name").removePrefix("v")
    if (version.isBlank()) {
        return null
    }
    // Con --generate-notes il corpo contiene solo "Full Changelog": inutile in un dialog,
    // quindi si usa solo il link alle note.
    val assets = release.optJSONArray("assets") ?: JSONArray()
    val apkUrl = (0 until assets.length())
        .mapNotNull { assets.optJSONObject(it) }
        .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
        ?.optString("browser_download_url")
        ?.takeIf { it.isNotBlank() }
    val notesUrl = release.optString("html_url")
    // Senza nessun link non c'e' nulla da aprire: meglio nessun popup che un Intent con
    // una stringa vuota dentro.
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
 * Numerico per componenti, non di stringhe: "1.10.0" > "1.9.0" con il confronto lessico
 * darebbe il risultato opposto. Un componente non numerico (build di debug con
 * versionName "0.1", "-rc1") vale 0 invece di far fallire il confronto.
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
