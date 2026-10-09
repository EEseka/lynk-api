package com.eeseka.lynk.spot.domain.model

data class SpotParking(
    val hasFreeLot: Boolean?,
    val hasPaidLot: Boolean?,
    val hasFreeStreet: Boolean?,
    val hasPaidStreet: Boolean?,
    val hasValet: Boolean?,
    val hasFreeGarage: Boolean?,
    val hasPaidGarage: Boolean?
)
