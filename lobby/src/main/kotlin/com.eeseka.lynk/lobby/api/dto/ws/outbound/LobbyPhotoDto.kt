package com.eeseka.lynk.lobby.api.dto.ws.outbound

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.domain.type.HangoutPhotoId

data class LobbyPhotoDto(
    val hangoutId: HangoutId,
    val photoId: HangoutPhotoId
)
