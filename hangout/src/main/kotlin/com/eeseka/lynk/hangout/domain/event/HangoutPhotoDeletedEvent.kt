package com.eeseka.lynk.hangout.domain.event

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.domain.type.HangoutPhotoId

data class HangoutPhotoDeletedEvent(
    val hangoutId: HangoutId,
    val photoId: HangoutPhotoId
)
