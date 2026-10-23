package com.eeseka.lynk.assistant.service.agent.tools.dto.inbound

import com.eeseka.lynk.spot.domain.model.PriceLevel
import com.eeseka.lynk.spot.domain.model.SpotCategory

data class SearchSpotsArgsDto(
    val query: String,
    val category: SpotCategory? = null,
    val priceLevel: PriceLevel? = null
)
