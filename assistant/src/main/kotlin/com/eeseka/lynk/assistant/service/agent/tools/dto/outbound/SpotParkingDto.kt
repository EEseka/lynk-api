package com.eeseka.lynk.assistant.service.agent.tools.dto.outbound

data class SpotParkingDto(
    val hasFreeLot: Boolean?,
    val hasPaidLot: Boolean?,
    val hasFreeStreet: Boolean?,
    val hasPaidStreet: Boolean?,
    val hasValet: Boolean?,
    val hasFreeGarage: Boolean?,
    val hasPaidGarage: Boolean?
)
