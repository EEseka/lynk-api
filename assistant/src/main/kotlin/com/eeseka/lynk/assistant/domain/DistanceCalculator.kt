package com.eeseka.lynk.assistant.domain

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object DistanceCalculator {
    private const val EARTH_RADIUS_METERS = 6371000.0

    /**
     * Calculates the great-circle distance between two points on the Earth's surface.
     * Returns the distance in meters.
     */
    fun calculateDistanceInMeters(
        originLat: Double,
        originLng: Double,
        spotLat: Double,
        spotLng: Double
    ): Int {
        val lat1Rad = Math.toRadians(originLat)
        val lat2Rad = Math.toRadians(spotLat)
        val deltaLat = Math.toRadians(spotLat - originLat)
        val deltaLng = Math.toRadians(spotLng - originLng)

        // Haversine formula
        val a = sin(deltaLat / 2).pow(2) +
                cos(lat1Rad) * cos(lat2Rad) *
                sin(deltaLng / 2).pow(2)

        val c = 2 * asin(sqrt(a))

        return (EARTH_RADIUS_METERS * c).roundToInt()
    }
}
