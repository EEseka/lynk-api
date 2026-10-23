package com.eeseka.lynk.assistant.service.agent.tools.dto.outbound

import com.eeseka.lynk.spot.domain.model.PriceLevel
import com.eeseka.lynk.spot.domain.model.SpotCategory

data class SpotCandidateDto(
    val spotId: String,
    val name: String,
    val type: String?,
    val category: SpotCategory,
    val priceLevel: PriceLevel?,
    val priceRange: SpotPriceRangeDto?,
    val rating: Double?,
    val reviewCount: Int?,
    val address: String?,
    val distanceKm: Double?,
    val isOpenAtHangoutTime: Boolean?,
    val isOpenNow: Boolean?,
    val isOnBallot: Boolean
)
