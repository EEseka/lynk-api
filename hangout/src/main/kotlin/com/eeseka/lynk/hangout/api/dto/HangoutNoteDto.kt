package com.eeseka.lynk.hangout.api.dto

import com.eeseka.lynk.common.domain.type.HangoutNoteId
import java.time.Instant

data class HangoutNoteDto(
    val id: HangoutNoteId,
    val author: HangoutUserDto,
    val content: String,
    val createdAt: Instant,
    val updatedAt: Instant?
)
