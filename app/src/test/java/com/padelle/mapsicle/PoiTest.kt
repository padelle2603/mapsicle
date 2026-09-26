package com.padelle.mapsicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PoiTest {
    @Test
    fun buildsGoogleSearchUrlForThePlaceName() {
        assertEquals(
            "https://www.google.com/maps/search/?api=1&query=Trattoria+Da+Luigi",
            buildPlaceUrl("Trattoria Da Luigi"),
        )
    }

    @Test
    fun keepsTheApiParameterGoogleRequires() {
        // Without api=1 Google ignores all the other parameters: the documentation says so
        // explicitly, so this assertion exists because it shows at once if somebody
        // "simplifies" the URL by removing it.
        assertTrue(buildPlaceUrl("Bar").contains("?api=1&query="))
    }

    @Test
    fun encodesCharactersThatWouldBreakTheUrl() {
        assertEquals(
            "https://www.google.com/maps/search/?api=1&query=Caf%C3%A9+%26+Co",
            buildPlaceUrl("Café & Co"),
        )
    }

    @Test
    fun aNameCannotInjectAnotherParameter() {
        // "Bar&origin=my location" must end up entirely inside query, not become a
        // parameter: the link cannot tell Google where the user starts from. Even the "+"
        // of the name becomes %2B, so it is not read back as a space.
        assertEquals(
            "https://www.google.com/maps/search/?api=1&query=Bar%26origin%3Dmy%2Blocation",
            buildPlaceUrl("Bar&origin=my+location"),
        )
    }

    @Test
    fun aChainGetsTheAddressThatPinsDownTheBranch() {
        // The real Photon answer for the Starbucks on Via Torino: the name alone would
        // open the list of every branch, "name, address" is the format Google recommends
        // for a specific establishment. Postcode before city, as an address is written.
        val query = resolvePlaceQuery(
            SearchPlace("Starbucks", 45.4625343, 9.1870272),
            parsePlaceAddresses(STARBUCKS_VIA_TORINO),
        )

        assertEquals("Starbucks, Via Torino 21, 20123 Milano", query)
    }

    @Test
    fun encodesTheWholeQueryAsOneValue() {
        // The comma that joins name and address is the one Google documents, so it is not
        // escaped as %2C; nothing after the name can become a parameter of its own.
        assertEquals(
            "https://www.google.com/maps/search/?api=1&query=Starbucks%2C+Via+Torino+21%2C+20123+Milano",
            buildPlaceUrl("Starbucks, Via Torino 21, 20123 Milano"),
        )
    }

    @Test
    fun dropsTheAddressOfANamesakeInAnotherTown() {
        // Photon matches "Bar" loosely: near Siena it answers with a bar 15km away in a
        // town of the same name. Sending that address would be worse than sending nothing,
        // so out of range the link is the plain name it has always been.
        val query = resolvePlaceQuery(
            SearchPlace("Bar", 43.3188, 11.3308),
            parsePlaceAddresses(BAR_NEAR_SIENA),
        )

        assertEquals("Bar", query)
    }

    @Test
    fun takesTheNearestAddressNotTheFirstOne() {
        // The bias orders by distance but does not filter, so the first answer can be a
        // namesake: what is checked is the closest to the POI that was tapped.
        val query = resolvePlaceQuery(
            SearchPlace("Bar", 43.3188, 11.3308),
            listOf(
                SearchPlace("Via Roma 1, 50028 Barberino Val d'Elsa", 43.5418625, 11.1714189),
                SearchPlace("Piazza del Campo 2, 53013 Siena", 43.3188, 11.3308),
            ),
        )

        assertEquals("Bar, Piazza del Campo 2, 53013 Siena", query)
    }

    @Test
    fun keepsTheNameWhenThereIsNoAddressAtAll() {
        // No network, no answer, no features: the button must behave exactly as it did
        // before this lookup existed, with no crash and no empty comma in the query.
        assertEquals("Bar", resolvePlaceQuery(SearchPlace("Bar", 43.3188, 11.3308), emptyList()))
        assertEquals("Bar", resolvePlaceQuery(SearchPlace("Bar", 43.3188, 11.3308), parsePlaceAddresses("""{"features":[]}""")))
    }

    @Test
    fun writesTheAddressWithWhateverPhotonHas() {
        // Outside the big cities "city" is null and the postcode can be missing too, and
        // "locality" (the quarter) and "district" (the municipality) are never a city.
        val properties = """{"features":[{"geometry":{"coordinates":[11.1,43.2]},"properties":{
            "street":"Via Roma","city":null,"locality":"Centro","district":"Municipio 3"}}]}"""

        assertEquals(listOf(SearchPlace("Via Roma", 43.2, 11.1)), parsePlaceAddresses(properties))
    }

    private companion object {
        const val STARBUCKS_VIA_TORINO =
            """{"features":[{"geometry":{"type":"Point","coordinates":[9.1870272,45.4625343]},"properties":{"name":"Starbucks","street":"Via Torino","housenumber":"21","postcode":"20123","city":"Milano","locality":"Cinque Vie","district":"Municipio 1","state":"Lombardia","country":"Italia"}}]}"""
        const val BAR_NEAR_SIENA =
            """{"features":[{"geometry":{"coordinates":[11.8579562,43.3878858]},"properties":{"name":"Bar","street":"Strada Regionale Umbro Casentinese Romagnola","postcode":"54045","city":"Policiano"}},{"geometry":{"coordinates":[11.1714189,43.5418625]},"properties":{"name":"Barberino Val d'Elsa","postcode":"50028","county":"Firenze"}}]}"""
    }
}
