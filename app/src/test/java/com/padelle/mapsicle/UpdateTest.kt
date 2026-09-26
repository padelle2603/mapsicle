package com.padelle.mapsicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateTest {
    @Test
    fun comparesVersionsByNumberNotByText() {
        // Il bug classico: "1.10.0" come stringa e' minore di "1.9.0", come numeri no.
        assertTrue(isNewerVersion("1.10.0", "1.9.0"))
        assertTrue(isNewerVersion("2.0.0", "1.99.99"))
        assertTrue(isNewerVersion("1.2.1", "1.2.0"))
    }

    @Test
    fun sameOrOlderIsNotAnUpdate() {
        assertFalse(isNewerVersion("1.2.0", "1.2.0"))
        assertFalse(isNewerVersion("1.1.0", "1.2.0"))
        assertFalse(isNewerVersion("0.9.9", "1.0.0"))
    }

    @Test
    fun toleratesLocalBuildsAndOddVersions() {
        // Il build di debug ha versionName "0.1": deve annunciare la release, non fallire.
        assertTrue(isNewerVersion("1.2.0", "0.1"))
        // Confronto numerico stretto, zero componenti in piu': 1.2 == 1.2.0
        assertFalse(isNewerVersion("1.2", "1.2.0"))
        // Suffissi non numerici: vale 0, non eccezione.
        assertFalse(isNewerVersion("1.2.0-rc1", "1.2.0"))
        assertTrue(isNewerVersion("1.3.0", "1.2.0-rc1"))
    }

    @Test
    fun readsTagApkAndNotesFromTheGithubResponse() {
        val release = parseLatestRelease(
            """
            {
              "tag_name": "v1.3.0",
              "html_url": "https://github.com/padelle2603/mapsicle/releases/tag/v1.3.0",
              "assets": [
                {"name": "mapsicle-v1.3.0.apk",
                 "browser_download_url": "https://github.com/padelle2603/mapsicle/releases/download/v1.3.0/mapsicle-v1.3.0.apk"}
              ]
            }
            """.trimIndent(),
        )

        assertEquals("1.3.0", release?.version)
        assertEquals(
            "https://github.com/padelle2603/mapsicle/releases/download/v1.3.0/mapsicle-v1.3.0.apk",
            release?.apkUrl,
        )
        assertEquals(
            "https://github.com/padelle2603/mapsicle/releases/tag/v1.3.0",
            release?.notesUrl,
        )
    }

    @Test
    fun survivesResponsesWithoutWhatItNeeds() {
        // 404 di GitHub, rate limit esaurito, HTML per un'errore di rete, release senza
        // link: nessuna di queste risposte deve far crashare l'avvio, solo restare senza
        // popup.
        assertNull(parseLatestRelease("""{"message": "Not Found"}"""))
        assertNull(parseLatestRelease("Not Found"))
        assertNull(parseLatestRelease(""))
        assertNull(parseLatestRelease("""{"tag_name": "v1.3.0"}"""))
    }

    @Test
    fun fallsBackToTheNotesPageWhenThereIsNoApk() {
        val release = parseLatestRelease(
            """{"tag_name": "v1.3.0", "html_url": "https://example.org/r", "assets": []}""",
        )

        assertNull(release?.apkUrl)
        assertEquals("https://example.org/r", release?.notesUrl)
    }
}
