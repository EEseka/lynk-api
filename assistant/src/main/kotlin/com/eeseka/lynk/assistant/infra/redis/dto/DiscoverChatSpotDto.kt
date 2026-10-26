package com.eeseka.lynk.assistant.infra.redis.dto

import com.eeseka.lynk.spot.domain.model.PriceLevel
import com.eeseka.lynk.spot.domain.model.SpotCategory

data class DiscoverChatSpotDto(
    val reason: String,
    val googlePlaceId: String,
    val name: String,
    val coverPhotoUrl: String?,
    val category: SpotCategory,
    val priceLevel: PriceLevel?,
    val shortAddress: String?,
    val latitude: Double,
    val longitude: Double
)
