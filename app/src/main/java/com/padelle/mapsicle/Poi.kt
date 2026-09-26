package com.padelle.mapsicle

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Apre il POI in Google Maps. Il nome basta: su 8478 POI curate fra Milano e Roma solo 9
 * hanno un nome generico ("Bar", "Minimarket"), e `api=1` e' il parametro che la
 * documentazione di Google chiama obbligatorio perche' senza vengono ignorati tutti gli
 * altri. Nessuna API key. Se l'app Google Maps non e' installata si apre il browser.
 *
 * Solo il nome del POI, mai la posizione dell'utente: il link non deve poter dire a Google
 * dove sta chi lo tocca.
 */
internal fun buildPlaceUrl(name: String): String {
    val encoded = URLEncoder.encode(name, StandardCharsets.UTF_8.name())
    return "https://www.google.com/maps/search/?api=1&query=$encoded"
}
