package com.eeseka.lynk.spot.infra.google_places.mappers

import com.eeseka.lynk.spot.infra.google_places.dto.GoogleLocation
import com.eeseka.lynk.spot.infra.google_places.dto.GoogleMoney
import com.eeseka.lynk.spot.infra.google_places.dto.GooglePlace
import com.eeseka.lynk.spot.infra.google_places.dto.GooglePriceRange
import com.eeseka.lynk.spot.infra.google_places.dto.GoogleText
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GooglePlacesMapperTest {

    @Test
    fun `reads the cheapest band starting at 1 as up to its end`() {
        val spot = place(priceRange = GooglePriceRange(startPrice = naira("1"), endPrice = naira("10000"))).toSpot()

        assertNull(spot?.priceRange?.startAmount)
        assertEquals(10_000, spot?.priceRange?.endAmount)
    }

    @Test
    fun `keeps a real start of a price range`() {
        val spot = place(priceRange = GooglePriceRange(startPrice = naira("20000"), endPrice = naira("30000"))).toSpot()

        assertEquals(20_000, spot?.priceRange?.startAmount)
        assertEquals(30_000, spot?.priceRange?.endAmount)
    }

    private fun naira(units: String) = GoogleMoney(currencyCode = "NGN", units = units)

    private fun place(priceRange: GooglePriceRange) = GooglePlace(
        id = "the-place",
        displayName = GoogleText("The Place"),
        primaryTypeDisplayName = null,
        editorialSummary = null,
        generativeSummary = null,
        reviewSummary = null,
        photos = null,
        primaryType = null,
        types = null,
        priceLevel = null,
        priceRange = priceRange,
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
        addressComponents = null,
        location = GoogleLocation(latitude = 6.4474, longitude = 3.4720),
        internationalPhoneNumber = null,
        websiteUri = null,
        googleMapsUri = null,
        googleMapsLinks = null
    )
}
