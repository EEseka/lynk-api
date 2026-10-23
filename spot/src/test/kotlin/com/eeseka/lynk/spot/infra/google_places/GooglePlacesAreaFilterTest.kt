package com.eeseka.lynk.spot.infra.google_places

import com.eeseka.lynk.spot.infra.google_places.dto.GoogleAddressComponent
import com.eeseka.lynk.spot.infra.google_places.dto.GooglePlace
import com.eeseka.lynk.spot.infra.google_places.dto.GoogleText
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/** The addresses below are what Google returned for these real places in October 2026. */
class GooglePlacesAreaFilterTest {

    private val ikotunLounge = place(
        "big-jim",
        "Ikotun" to "locality",
        "Ikotun/Ijegun" to "administrative_area_level_3",
        "Alimosho" to "administrative_area_level_2",
        "Lagos" to "administrative_area_level_1"
    )
    private val surulereLounge = place(
        "the-cabin",
        "Alaka" to "neighborhood",
        "Surulere" to "administrative_area_level_2",
        "Lagos" to "administrative_area_level_1"
    )

    @Test
    fun `drops a place from another part of the city`() {
        val inArea = listOf(ikotunLounge, surulereLounge).inArea("Alimosho")

        assertEquals(listOf("big-jim"), inArea.map { it.id })
    }

    @Test
    fun `keeps the whole state when the area is the state`() {
        val inArea = listOf(ikotunLounge, surulereLounge).inArea("Lagos")

        assertEquals(listOf("big-jim", "the-cabin"), inArea.map { it.id })
    }

    @Test
    fun `matches however the area is spelt`() {
        val ijebuOde = place("ijebu-spot", "Ijebu Ode" to "locality", "Ogun" to "administrative_area_level_1")

        assertEquals(listOf("ijebu-spot"), listOf(ijebuOde).inArea("Ijebu-Ode").map { it.id })
    }

    @Test
    fun `never matches a street that happens to share the name`() {
        val onIkotunRoad = place("road-spot", "Ikotun Road" to "route", "Surulere" to "administrative_area_level_2")

        assertEquals(listOf("big-jim"), listOf(ikotunLounge, onIkotunRoad).inArea("Ikotun").map { it.id })
    }

    // The phone and Google naming the area differently is likelier than Google finding nothing there
    @Test
    fun `keeps every place when none of them match`() {
        val inArea = listOf(ikotunLounge, surulereLounge).inArea("Somewhere Else")

        assertEquals(listOf("big-jim", "the-cabin"), inArea.map { it.id })
    }

    private fun place(id: String, vararg components: Pair<String, String>) = GooglePlace(
        id = id,
        displayName = GoogleText(id),
        primaryTypeDisplayName = null,
        editorialSummary = null,
        generativeSummary = null,
        reviewSummary = null,
        photos = null,
        primaryType = null,
        types = null,
        priceLevel = null,
        priceRange = null,
        rating = null,
        userRatingCount = null,
        businessStatus = null,
        currentOpeningHours = null,
        utcOffsetMinutes = null,
        timeZone = null,
        goodForGroups = null,
        reservable = null,
        liveMusic = null,
        outdoorSeating = null,
        servesCocktails = null,
        goodForWatchingSports = null,
        parkingOptions = null,
        paymentOptions = null,
        formattedAddress = null,
        shortFormattedAddress = null,
        addressComponents = components.map { (text, type) -> GoogleAddressComponent(longText = text, types = listOf(type)) },
        location = null,
        internationalPhoneNumber = null,
        websiteUri = null,
        googleMapsUri = null,
        googleMapsLinks = null
    )
}
