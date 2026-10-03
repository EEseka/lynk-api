package com.eeseka.lynk.hangout.domain.model

data class HangoutPhotoStats(
    val photoCount: Int,
    val myPhotoCount: Int // Counts my unfinished uploads too, like the cap does
)
