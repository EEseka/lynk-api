package com.eeseka.lynk.spot.api.dto

data class SpotAmenitiesDto(
    val isGoodForGroups: Boolean?,
    val isReservable: Boolean?,
    val hasLiveMusic: Boolean?,
    val hasOutdoorSeating: Boolean?,
    val servesCocktails: Boolean?,
    val isGoodForWatchingSports: Boolean?
)
