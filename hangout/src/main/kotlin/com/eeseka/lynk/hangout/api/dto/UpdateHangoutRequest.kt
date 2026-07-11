package com.eeseka.lynk.hangout.api.dto

import com.eeseka.lynk.hangout.domain.model.HangoutVibe
import jakarta.validation.constraints.Future
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import org.hibernate.validator.constraints.Length
import java.time.Instant

data class UpdateHangoutRequest(
    @field:NotBlank(message = "Name cannot be blank")
    @field:Length(max = 50, message = "Name cannot exceed 50 characters")
    val name: String,
    @field:Length(max = 500, message = "Description cannot exceed 500 characters")
    val description: String?,
    val vibe: HangoutVibe,
    @field:Future(message = "Hangout must be scheduled in the future")
    val scheduledAt: Instant,
    @field:Min(value = 2, message = "Max attendees must be at least 2")
    @field:Max(value = 50, message = "Max attendees cannot exceed 50")
    val maxAttendees: Int?,
    val spotId: String?
)