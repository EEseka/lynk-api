package com.eeseka.lynk.hangout.api.controllers

import com.eeseka.lynk.common.api.config.UserRateLimit
import com.eeseka.lynk.common.api.config.WhenRedisIsDown
import com.eeseka.lynk.common.api.util.requestUserId
import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.domain.type.HangoutPhotoId
import com.eeseka.lynk.hangout.api.dto.GenerateHangoutPhotoUploadUrlsRequest
import com.eeseka.lynk.hangout.api.dto.HangoutPhotoDto
import com.eeseka.lynk.hangout.api.dto.HangoutPhotoStatsDto
import com.eeseka.lynk.hangout.api.dto.HangoutPhotoUploadResponse
import com.eeseka.lynk.hangout.api.dto.UpdateHangoutPhotoCaptionRequest
import com.eeseka.lynk.hangout.api.mappers.toHangoutPhotoDto
import com.eeseka.lynk.hangout.api.mappers.toHangoutPhotoStatsDto
import com.eeseka.lynk.hangout.api.mappers.toHangoutPhotoUploadResponse
import com.eeseka.lynk.hangout.service.HangoutPhotoService
import com.eeseka.lynk.hangout.service.HangoutPhotoUploadService
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
@RequestMapping("/api/hangouts/{hangoutId}/photos")
class HangoutPhotoController(
    private val hangoutPhotoService: HangoutPhotoService,
    private val hangoutPhotoUploadService: HangoutPhotoUploadService
) {
    companion object {
        private const val DEFAULT_PAGE_SIZE = 20
        private const val MAX_PAGE_SIZE = 50
    }

    @GetMapping
    fun getPhotos(
        @PathVariable hangoutId: HangoutId,

        @RequestParam(required = false)
        before: Instant? = null,

        @RequestParam(required = false)
        @Min(1) @Max(MAX_PAGE_SIZE.toLong())
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): List<HangoutPhotoDto> {
        return hangoutPhotoService.getPhotos(
            userId = requestUserId,
            hangoutId = hangoutId,
            before = before,
            pageSize = pageSize
        ).map { (photo, downloadUrls) -> photo.toHangoutPhotoDto(downloadUrls) }
    }

    @GetMapping("/stats")
    fun getPhotoStats(
        @PathVariable hangoutId: HangoutId
    ): HangoutPhotoStatsDto {
        return hangoutPhotoService.getPhotoStats(
            userId = requestUserId,
            hangoutId = hangoutId
        ).toHangoutPhotoStatsDto()
    }

    @UserRateLimit(
        requests = 30,
        duration = 1L,
        unit = TimeUnit.HOURS,
        whenRedisIsDown = WhenRedisIsDown.REFUSE
    )
    @PostMapping("/generate-upload-urls")
    @ResponseStatus(HttpStatus.CREATED)
    fun generateUploadUrls(
        @PathVariable hangoutId: HangoutId,
        @Valid @RequestBody body: GenerateHangoutPhotoUploadUrlsRequest
    ): List<HangoutPhotoUploadResponse> {
        return hangoutPhotoUploadService.generateUploadCredentials(
            userId = requestUserId,
            hangoutId = hangoutId,
            count = body.count
        ).map { it.toHangoutPhotoUploadResponse() }
    }

    @PostMapping("/{photoId}/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun confirmUpload(
        @PathVariable hangoutId: HangoutId,
        @PathVariable photoId: HangoutPhotoId
    ) {
        hangoutPhotoUploadService.confirmUpload(
            userId = requestUserId,
            hangoutId = hangoutId,
            photoId = photoId
        )
    }

    @PatchMapping("/{photoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun updateCaption(
        @PathVariable hangoutId: HangoutId,
        @PathVariable photoId: HangoutPhotoId,
        @Valid @RequestBody body: UpdateHangoutPhotoCaptionRequest
    ) {
        hangoutPhotoService.updateCaption(
            userId = requestUserId,
            hangoutId = hangoutId,
            photoId = photoId,
            caption = body.caption
        )
    }

    @DeleteMapping("/{photoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deletePhoto(
        @PathVariable hangoutId: HangoutId,
        @PathVariable photoId: HangoutPhotoId
    ) {
        hangoutPhotoService.deletePhoto(
            userId = requestUserId,
            hangoutId = hangoutId,
            photoId = photoId
        )
    }
}
