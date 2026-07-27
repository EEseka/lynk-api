package com.eeseka.lynk.hangout.api.dto

import com.eeseka.lynk.common.domain.type.UserId

data class InviteParticipantRequest(
    val userId: UserId
)