package com.eeseka.lynk.assistant.service.agent.tools.dto.outbound

data class SpotPriceRangeDto(
    val currencyCode: String,
    val startAmount: Long?,
    val endAmount: Long?
)
