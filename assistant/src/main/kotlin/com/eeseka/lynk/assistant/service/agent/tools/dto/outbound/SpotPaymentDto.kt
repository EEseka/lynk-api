package com.eeseka.lynk.assistant.service.agent.tools.dto.outbound

data class SpotPaymentDto(
    val acceptsCreditCards: Boolean?,
    val acceptsDebitCards: Boolean?,
    val acceptsCashOnly: Boolean?,
    val acceptsNfc: Boolean?
)
