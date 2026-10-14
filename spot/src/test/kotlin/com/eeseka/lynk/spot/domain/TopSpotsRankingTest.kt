package com.eeseka.lynk.spot.domain

import com.eeseka.lynk.spot.domain.model.Spot
import com.eeseka.lynk.spot.domain.model.SpotCategory
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TopSpotsRankingTest {

    @Test
    fun `ranks a well loved place above one that matched the words better`() {
        val ranked = TopSpotsRanking.pick(
            listOf(
                spot("booms", rating = 3.9, reviewCount = 68),
                spot("the-place", rating = 4.3, reviewCount = 1372)
            )
        )

        assertEquals(listOf("the-place", "booms"), ranked.map { it.id })
    }

    @Test
    fun `trusts many reviews over a perfect score from a few`() {
        val ranked = TopSpotsRanking.pick(
            listOf(
                spot("perfect-from-few", rating = 5.0, reviewCount = 25),
                spot("great-from-many", rating = 4.6, reviewCount = 900)
            )
        )

        assertEquals(listOf("great-from-many", "perfect-from-few"), ranked.map { it.id })
    }

    @Test
    fun `puts thinly reviewed places after every well reviewed one`() {
        val ranked = TopSpotsRanking.pick(
            listOf(
                spot("white-fox", rating = 5.0, reviewCount = 5),
                spot("club-rubies", rating = 3.8, reviewCount = 482)
            )
        )

        assertEquals(listOf("club-rubies", "white-fox"), ranked.map { it.id })
    }

    @Test
    fun `puts places nobody has rated last`() {
        val ranked = TopSpotsRanking.pick(
            listOf(
                spot("unrated", rating = null, reviewCount = null),
                spot("thin", rating = 3.5, reviewCount = 2)
            )
        )

        assertEquals(listOf("thin", "unrated"), ranked.map { it.id })
    }

    // A small town where nowhere has many reviews still gets a list, ordered by what reviews there are
    @Test
    fun `keeps a small town's places when none are well reviewed`() {
        val ranked = TopSpotsRanking.pick(
            listOf(
                spot("s-fresh", rating = 3.5, reviewCount = 2),
                spot("amala-formation", rating = 5.0, reviewCount = 10),
                spot("bar-1818", rating = 4.2, reviewCount = 5)
            )
        )

        assertEquals(listOf("amala-formation", "bar-1818", "s-fresh"), ranked.map { it.id })
    }

    @Test
    fun `shows ten at most`() {
        val ranked = TopSpotsRanking.pick((1..20).map { spot("spot-$it", rating = 4.2, reviewCount = 100 + it) })

        assertEquals(10, ranked.size)
    }

    @Test
    fun `has nothing to rank when Google found nothing`() {
        assertEquals(emptyList(), TopSpotsRanking.pick(emptyList()))
    }

    private fun spot(id: String, rating: Double?, reviewCount: Int?) = Spot(
        id = id,
        name = id,
        typeLabel = null,
        description = null,
        generativeSummary = null,
        reviewSummary = null,
        photoUrls = emptyList(),
        category = SpotCategory.LOUNGE,
        priceLevel = null,
        priceRange = null,
        rating = rating,
        reviewCount = reviewCount,
        businessStatus = null,
        openingHours = null,
        amenities = null,
        parking = null,
        payment = null,
        shortAddress = null,
        latitude = 6.548,
        longitude = 3.269,
        phoneNumber = null,
        websiteUrl = null,
        googleMapsUrl = null,
        directionsUrl = null,
        isSaved = false
    )
}
