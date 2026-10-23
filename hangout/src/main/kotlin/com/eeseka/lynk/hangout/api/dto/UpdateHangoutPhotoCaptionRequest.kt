package com.eeseka.lynk.hangout.api.dto

import com.eeseka.lynk.hangout.domain.HangoutConstants.MAX_CAPTION_LENGTH
import org.hibernate.validator.constraints.Length

data class UpdateHangoutPhotoCaptionRequest(
    @field:Length(max = MAX_CAPTION_LENGTH, message = "Caption cannot exceed $MAX_CAPTION_LENGTH characters")
    val caption: String?
)
