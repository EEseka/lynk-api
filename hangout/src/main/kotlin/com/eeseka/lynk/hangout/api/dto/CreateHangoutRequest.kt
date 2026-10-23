package com.eeseka.lynk.hangout.api.dto

import com.eeseka.lynk.hangout.domain.HangoutConstants.MAX_ATTENDEES
import com.eeseka.lynk.hangout.domain.HangoutConstants.MAX_DESCRIPTION_LENGTH
import com.eeseka.lynk.hangout.domain.HangoutConstants.MAX_NAME_LENGTH
import com.eeseka.lynk.hangout.domain.HangoutConstants.MIN_ATTENDEES
import com.eeseka.lynk.hangout.domain.model.HangoutVibe
import jakarta.validation.constraints.Future
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import org.hibernate.validator.constraints.Length
import java.time.Instant

data class CreateHangoutRequest(
    @field:NotBlank(message = "Name cannot be blank")
    @field:Length(max = MAX_NAME_LENGTH, message = "Name cannot exceed $MAX_NAME_LENGTH characters")
    val name: String,
    @field:Length(max = MAX_DESCRIPTION_LENGTH, message = "Description cannot exceed $MAX_DESCRIPTION_LENGTH characters")
    val description: String?,
    val vibe: HangoutVibe,
    @field:Future(message = "Hangout must be scheduled in the future")
    val scheduledAt: Instant,
    @field:Min(value = MIN_ATTENDEES.toLong(), message = "Max attendees must be at least $MIN_ATTENDEES")
    @field:Max(value = MAX_ATTENDEES.toLong(), message = "Max attendees cannot exceed $MAX_ATTENDEES")
    val maxAttendees: Int?,
    val spotId: String?
)