package com.eeseka.lynk.hangout.api.controllers

import com.eeseka.lynk.common.api.config.UserRateLimit
import com.eeseka.lynk.common.api.config.WhenRedisIsDown
import com.eeseka.lynk.common.api.util.requestUserId
import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.domain.type.HangoutPhotoId
import com.eeseka.lynk.hangout.api.dto.ConfirmHangoutPhotoUploadRequest
import com.eeseka.lynk.hangout.api.dto.GenerateHangoutPhotoUploadUrlsRequest
import com.eeseka.lynk.hangout.api.dto.HangoutPhotoUploadResponse
import com.eeseka.lynk.hangout.api.mappers.toHangoutPhotoUploadResponse
import com.eeseka.lynk.hangout.service.HangoutPhotoUploadService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.TimeUnit

@RestController
@RequestMapping("/api/hangouts/{hangoutId}/photos")
class HangoutPhotoController(
    private val hangoutPhotoUploadService: HangoutPhotoUploadService
) {
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
        @PathVariable photoId: HangoutPhotoId,
        @Valid @RequestBody body: ConfirmHangoutPhotoUploadRequest
    ) {
        hangoutPhotoUploadService.confirmUpload(
            userId = requestUserId,
            hangoutId = hangoutId,
            photoId = photoId,
            caption = body.caption
        )
    }
}
