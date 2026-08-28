package com.eeseka.lynk.user.domain.events

import com.eeseka.lynk.common.domain.type.UserId

data class ProfilePictureDeletedEvent(
    val userId: UserId,
    val photoUrl: String
)
