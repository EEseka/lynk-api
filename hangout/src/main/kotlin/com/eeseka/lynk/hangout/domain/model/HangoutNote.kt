package com.eeseka.lynk.hangout.domain.model

import com.eeseka.lynk.common.domain.type.HangoutNoteId
import java.time.Instant

data class HangoutNote(
    val id: HangoutNoteId,
    val author: HangoutUser,
    val content: String,
    val createdAt: Instant,
    val updatedAt: Instant?
)
