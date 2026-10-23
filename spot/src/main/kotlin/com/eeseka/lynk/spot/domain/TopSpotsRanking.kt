package com.eeseka.lynk.spot.domain

import com.eeseka.lynk.spot.domain.model.Spot

/**
 * Orders an area's Top 10 out of the places Google returned for it.
 *
 * Google orders a text search by how well each place matches the words, not by how good it is, so
 * a 5.0 with 25 reviews can sit above a 4.6 with 900 reviews. The list is ranked again here by rating,
 * weighted by how many people gave it.
 */
object TopSpotsRanking {
    private const val TOP_SPOTS_COUNT = 10

    // A handful of reviews says little, so those places only fill in after every well-reviewed one.
    // In a small town that may be every place, and they still make the list rather than leave it empty
    private const val WELL_REVIEWED = 20

    // Every place starts at TYPICAL_RATING, and past TRUSTED_REVIEW_COUNT reviews its own rating counts for more
    private const val TYPICAL_RATING = 4.0
    private const val TRUSTED_REVIEW_COUNT = 100

    fun pick(spots: List<Spot>): List<Spot> {
        return spots
            .sortedWith(
                compareBy<Spot> { it.reviewTier() }
                    .thenByDescending { weightedRating(it.rating ?: TYPICAL_RATING, it.reviewCount ?: 0) }
            )
            .take(TOP_SPOTS_COUNT)
    }

    // Well reviewed first, then thinly reviewed, then places nobody has rated
    private fun Spot.reviewTier(): Int = when {
        rating == null -> 2
        (reviewCount ?: 0) < WELL_REVIEWED -> 1
        else -> 0
    }

    private fun weightedRating(rating: Double, reviewCount: Int): Double {
        val ownWeight = reviewCount.toDouble() / (reviewCount + TRUSTED_REVIEW_COUNT)
        return ownWeight * rating + (1 - ownWeight) * TYPICAL_RATING
    }
}
