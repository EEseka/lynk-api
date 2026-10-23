package com.eeseka.lynk.assistant.service.agent.tools.dto.outbound

data class SpotDetailsDto(
    val spot: SpotCandidateDto,
    val description: String?,
    val weeklyHours: List<String>,
    val amenities: SpotAmenitiesDto?,
    val parking: SpotParkingDto?,
    val payment: SpotPaymentDto?
)
