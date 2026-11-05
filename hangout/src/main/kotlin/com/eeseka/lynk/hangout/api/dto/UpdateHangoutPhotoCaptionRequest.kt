package com.eeseka.lynk.hangout.api.dto

import com.eeseka.lynk.common.domain.ValidationPatterns.SINGLE_LINE
import com.eeseka.lynk.hangout.domain.HangoutConstants.MAX_CAPTION_LENGTH
import jakarta.validation.constraints.Pattern
import org.hibernate.validator.constraints.Length

data class UpdateHangoutPhotoCaptionRequest(
    @field:Length(max = MAX_CAPTION_LENGTH, message = "Caption cannot exceed $MAX_CAPTION_LENGTH characters")
    @field:Pattern(regexp = SINGLE_LINE, message = "Caption must be on one line")
    val caption: String?
)
