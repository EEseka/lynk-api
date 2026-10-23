package com.eeseka.lynk.spot.infra.google_places

import com.eeseka.lynk.spot.domain.model.SpotCategory
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The type lists below are what Google returned for these real places in October 2026. */
class GooglePlaceTypesTest {

    @Test
    fun `files a supermarket as not a hangout despite its bakery counter`() {
        val shoprite = listOf(
            "grocery_store", "cell_phone_store", "butcher_shop", "transportation_service", "travel_agency",
            "bakery", "finance", "deli", "food_store", "store", "food", "point_of_interest", "service",
            "manufacturer", "establishment"
        )

        assertEquals(SpotCategory.OTHER, GooglePlaceTypes.categoryOf("grocery_store", shoprite))
    }

    @Test
    fun `files a government office as not a hangout despite its ice cream stand`() {
        val lagosHouse = listOf(
            "government_office", "ice_cream_shop", "dessert_shop", "confectionery", "food_store", "store", "food",
            "point_of_interest", "establishment"
        )

        assertEquals(SpotCategory.OTHER, GooglePlaceTypes.categoryOf("government_office", lagosHouse))
    }

    @Test
    fun `files a members club by its restaurant`() {
        val vaultClub = listOf("association_or_organization", "restaurant", "food", "point_of_interest")

        assertEquals(SpotCategory.RESTAURANT, GooglePlaceTypes.categoryOf("association_or_organization", vaultClub))
    }

    @Test
    fun `still files a mall as somewhere to go`() {
        assertEquals(SpotCategory.ACTIVITY, GooglePlaceTypes.categoryOf("shopping_mall", listOf("shopping_mall", "store")))
    }

    @Test
    fun `files an ice cream parlour as a cafe`() {
        val coldStone = listOf("ice_cream_shop", "dessert_shop", "confectionery", "food_store", "store", "food")

        assertEquals(SpotCategory.CAFE, GooglePlaceTypes.categoryOf("ice_cream_shop", coldStone))
    }

    @Test
    fun `files a hotel with nothing else to offer as not a hangout`() {
        assertEquals(SpotCategory.OTHER, GooglePlaceTypes.categoryOf("hotel", listOf("hotel", "lodging")))
    }

    @Test
    fun `files a hotel with a bar as a lounge`() {
        assertEquals(SpotCategory.LOUNGE, GooglePlaceTypes.categoryOf("hotel", listOf("hotel", "bar", "lodging")))
    }

    @Test
    fun `goes by the main type before the others`() {
        val terraKulture = listOf("cultural_center", "art_gallery", "book_store", "event_venue", "restaurant", "food")

        assertEquals(SpotCategory.ACTIVITY, GooglePlaceTypes.categoryOf("cultural_center", terraKulture))
    }

    @ParameterizedTest(name = "{0} is {1}")
    @CsvSource(
        "nigerian_restaurant, RESTAURANT",
        "steak_house, RESTAURANT",
        "snack_bar, RESTAURANT",
        "coffee_shop, CAFE",
        "bakery, CAFE",
        "hookah_bar, LOUNGE",
        "brewery, LOUNGE",
        "night_club, CLUB",
        "playground, ACTIVITY",
        "go_karting_venue, ACTIVITY",
        "live_music_venue, ACTIVITY",
        "art_studio, ACTIVITY",
        "botanical_garden, ACTIVITY",
        "resort_hotel, ACTIVITY",
        "mountain_peak, ACTIVITY",
        "internet_cafe, OTHER",
        "event_venue, OTHER",
        "community_center, OTHER",
        "gas_station, OTHER",
        "church, OTHER",
        "gym, OTHER",
        "spa, OTHER",
    )
    fun `files each type under its category`(type: String, category: SpotCategory) {
        assertEquals(category, GooglePlaceTypes.categoryOf(type, listOf(type)))
    }

    @Test
    fun `asks for no more types than Nearby Search accepts`() {
        assertTrue(GooglePlaceTypes.trendingTypes.size <= 50)
    }

    @Test
    fun `only asks trending for types it would list`() {
        GooglePlaceTypes.trendingTypes.forEach { type ->
            val category = GooglePlaceTypes.categoryOf(type, listOf(type))
            assertTrue(category != SpotCategory.OTHER, "$type would be asked for and then dropped")
        }
    }
}
