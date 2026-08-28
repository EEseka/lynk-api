package com.eeseka.lynk.notification.api.controllers

import com.eeseka.lynk.common.api.util.requestUserId
import com.eeseka.lynk.notification.api.dto.DeviceTokenDto
import com.eeseka.lynk.notification.api.dto.RegisterDeviceRequest
import com.eeseka.lynk.notification.api.mappers.toDeviceTokenDto
import com.eeseka.lynk.notification.service.DeviceTokenService
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@Validated
@RestController
@RequestMapping("/api/notifications")
class DeviceTokenController(
    private val deviceTokenService: DeviceTokenService
) {
    @PostMapping("/register")
    fun registerDeviceToken(
        @Valid @RequestBody body: RegisterDeviceRequest
    ): DeviceTokenDto {
        return deviceTokenService.registerDevice(
            userId = requestUserId,
            token = body.token,
            platform = body.platform
        ).toDeviceTokenDto()
    }

    @DeleteMapping("/{token}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unregisterDeviceToken(
        @PathVariable @NotBlank token: String
    ) {
        deviceTokenService.unregisterDevice(token)
    }
}