package com.eeseka.lynk.hangout.api.dto

import com.eeseka.lynk.hangout.domain.model.RsvpStatus

data class RsvpRequest(
    val rsvpStatus: RsvpStatus
)
