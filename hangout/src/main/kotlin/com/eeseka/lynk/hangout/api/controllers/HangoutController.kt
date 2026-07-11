package com.eeseka.lynk.hangout.api.controllers

import com.eeseka.lynk.common.api.util.requestUserId
import com.eeseka.lynk.hangout.api.dto.CreateHangoutRequest
import com.eeseka.lynk.hangout.api.dto.HangoutDto
import com.eeseka.lynk.hangout.api.dto.HangoutSummaryDto
import com.eeseka.lynk.hangout.api.dto.UpdateHangoutRequest
import com.eeseka.lynk.hangout.api.mappers.toHangoutDto
import com.eeseka.lynk.hangout.api.mappers.toHangoutSummaryDto
import com.eeseka.lynk.hangout.domain.model.HangoutStatus
import com.eeseka.lynk.hangout.domain.model.HangoutVibe
import com.eeseka.lynk.hangout.service.HangoutService
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.util.*

@Validated
@RestController
@RequestMapping("/api/hangouts")
class HangoutController(
    private val hangoutService: HangoutService
) {
    companion object {
        private const val DEFAULT_PAGE_SIZE = 20
        private const val MAX_PAGE_SIZE = 50
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createHangout(
        @Valid @RequestBody body: CreateHangoutRequest
    ): HangoutDto {
        val (hangout, spot) = hangoutService.createHangout(
            userId = requestUserId,
            name = body.name,
            description = body.description,
            vibe = body.vibe,
            scheduledAt = body.scheduledAt,
            maxAttendees = body.maxAttendees,
            spotId = body.spotId
        )
        return hangout.toHangoutDto(spot)
    }

    @PutMapping("/{hangoutId}")
    fun updateHangout(
        @PathVariable hangoutId: UUID,
        @Valid @RequestBody body: UpdateHangoutRequest
    ): HangoutDto {
        val (hangout, spot) = hangoutService.updateHangout(
            userId = requestUserId,
            hangoutId = hangoutId,
            name = body.name,
            description = body.description,
            vibe = body.vibe,
            scheduledAt = body.scheduledAt,
            maxAttendees = body.maxAttendees,
            spotId = body.spotId
        )
        return hangout.toHangoutDto(spot)
    }

    @GetMapping("/{hangoutId}")
    fun getHangoutDetails(
        @PathVariable hangoutId: UUID
    ): HangoutDto {
        val (hangout, spot) = hangoutService.getHangoutDetails(
            userId = requestUserId,
            hangoutId = hangoutId
        )
        return hangout.toHangoutDto(spot)
    }

    @GetMapping
    fun getHangouts(
        @RequestParam(required = false)
        before: Instant? = null,

        @RequestParam(required = false)
        status: HangoutStatus? = null,

        @RequestParam(required = false)
        vibe: HangoutVibe? = null,

        @RequestParam(required = false)
        query: String? = null,

        @RequestParam(required = false)
        @Min(1) @Max(MAX_PAGE_SIZE.toLong())
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): List<HangoutSummaryDto> {
        return hangoutService.getHangouts(
            userId = requestUserId,
            pageSize = pageSize,
            before = before,
            status = status,
            vibe = vibe,
            query = query
        ).map { it.toHangoutSummaryDto() }
    }

    @DeleteMapping("/{hangoutId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun cancelHangout(
        @PathVariable hangoutId: UUID
    ) {
        hangoutService.cancelHangout(
            userId = requestUserId,
            hangoutId = hangoutId
        )
    }

    @PatchMapping("/{hangoutId}/complete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun completeHangout(
        @PathVariable hangoutId: UUID
    ) {
        hangoutService.completeHangout(
            userId = requestUserId,
            hangoutId = hangoutId
        )
    }
}
