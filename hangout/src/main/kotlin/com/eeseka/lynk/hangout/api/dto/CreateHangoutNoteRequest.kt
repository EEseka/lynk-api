package com.eeseka.lynk.hangout.api.dto

import com.eeseka.lynk.hangout.domain.HangoutConstants.MAX_NOTE_LENGTH
import com.eeseka.lynk.hangout.domain.model.HangoutNoteAudience
import jakarta.validation.constraints.NotBlank
import org.hibernate.validator.constraints.Length

data class CreateHangoutNoteRequest(
    @field:NotBlank(message = "Note cannot be blank")
    @field:Length(max = MAX_NOTE_LENGTH, message = "Note cannot exceed $MAX_NOTE_LENGTH characters")
    val content: String,
    val audience: HangoutNoteAudience
)
