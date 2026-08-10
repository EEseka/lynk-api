package com.eeseka.lynk.user.domain.events

import com.eeseka.lynk.common.domain.type.UserId

data class ProfilePictureReplacedEvent(
    val userId: UserId,
    val oldPhotoUrl: String
)