package com.eeseka.lynk.hangout.api.controllers

import com.eeseka.lynk.common.api.config.UserRateLimit
import com.eeseka.lynk.common.api.util.requestUserId
import com.eeseka.lynk.common.domain.model.WhenRedisIsDown
import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.domain.type.HangoutNoteId
import com.eeseka.lynk.hangout.api.dto.CreateHangoutNoteRequest
import com.eeseka.lynk.hangout.api.dto.HangoutNoteCountDto
import com.eeseka.lynk.hangout.api.dto.HangoutNoteDto
import com.eeseka.lynk.hangout.api.dto.UpdateHangoutNoteRequest
import com.eeseka.lynk.hangout.api.mappers.toHangoutNoteDto
import com.eeseka.lynk.hangout.service.HangoutNoteService
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.concurrent.TimeUnit

@Validated
@RestController
@RequestMapping("/api/hangouts/{hangoutId}/notes")
class HangoutNoteController(
    private val hangoutNoteService: HangoutNoteService
) {
    companion object {
        private const val DEFAULT_PAGE_SIZE = 20
        private const val MAX_PAGE_SIZE = 50
    }

    @GetMapping
    fun getNotes(
        @PathVariable hangoutId: HangoutId,

        @RequestParam(required = false)
        before: Instant? = null,

        @RequestParam(required = false)
        @Min(1) @Max(MAX_PAGE_SIZE.toLong())
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): List<HangoutNoteDto> {
        return hangoutNoteService.getNotes(
            userId = requestUserId,
            hangoutId = hangoutId,
            before = before,
            pageSize = pageSize
        ).map { it.toHangoutNoteDto() }
    }

    @GetMapping("/count")
    fun getNoteCount(
        @PathVariable hangoutId: HangoutId
    ): HangoutNoteCountDto {
        return HangoutNoteCountDto(
            hangoutNoteService.getNoteCount(
                userId = requestUserId,
                hangoutId = hangoutId
            )
        )
    }

    @UserRateLimit(
        requests = 20,
        duration = 1L,
        unit = TimeUnit.HOURS,
        whenRedisIsDown = WhenRedisIsDown.REFUSE
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun addNote(
        @PathVariable hangoutId: HangoutId,
        @Valid @RequestBody body: CreateHangoutNoteRequest
    ): HangoutNoteDto {
        return hangoutNoteService.addNote(
            userId = requestUserId,
            hangoutId = hangoutId,
            content = body.content,
            audience = body.audience
        ).toHangoutNoteDto()
    }

    @PatchMapping("/{noteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun editNote(
        @PathVariable hangoutId: HangoutId,
        @PathVariable noteId: HangoutNoteId,
        @Valid @RequestBody body: UpdateHangoutNoteRequest
    ) {
        hangoutNoteService.editNote(
            userId = requestUserId,
            hangoutId = hangoutId,
            noteId = noteId,
            content = body.content
        )
    }

    @DeleteMapping("/{noteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteNote(
        @PathVariable hangoutId: HangoutId,
        @PathVariable noteId: HangoutNoteId
    ) {
        hangoutNoteService.deleteNote(
            userId = requestUserId,
            hangoutId = hangoutId,
            noteId = noteId
        )
    }
}
