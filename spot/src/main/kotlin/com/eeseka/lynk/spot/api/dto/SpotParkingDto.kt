package com.eeseka.lynk.spot.api.dto

data class SpotParkingDto(
    val hasFreeLot: Boolean?,
    val hasPaidLot: Boolean?,
    val hasFreeStreet: Boolean?,
    val hasPaidStreet: Boolean?,
    val hasValet: Boolean?,
    val hasFreeGarage: Boolean?,
    val hasPaidGarage: Boolean?
)
