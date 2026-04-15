package com.eeseka.lynk.user.api.controllers

import com.eeseka.lynk.common.api.util.requestUserId
import com.eeseka.lynk.user.api.config.IpRateLimit
import com.eeseka.lynk.user.api.dto.GenerateProfilePictureUploadUrlRequest
import com.eeseka.lynk.user.api.dto.ProfilePictureUploadResponse
import com.eeseka.lynk.user.api.dto.UpdateProfileRequest
import com.eeseka.lynk.user.api.dto.UserDto
import com.eeseka.lynk.user.api.mappers.toResponse
import com.eeseka.lynk.user.api.mappers.toUserDto
import com.eeseka.lynk.user.service.UserService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*
import java.util.concurrent.TimeUnit

@RestController
@RequestMapping("/api/users")
class UserController(
    private val userService: UserService
) {

    @GetMapping("/username-available")
    @IpRateLimit(
        requests = 60,
        duration = 1L, 
        unit = TimeUnit.MINUTES
    )
    fun isUsernameAvailable(
        @RequestParam username: String
    ): Map<String, Boolean> {
        val isAvailable = userService.isUsernameAvailable(username)
        return mapOf("isAvailable" to isAvailable)
    }

    @PostMapping("/profile-picture/generate-upload-url")
    @IpRateLimit(
        requests = 20,
        duration = 1L,
        unit = TimeUnit.HOURS
    )
    fun generateUploadUrl(
        @Valid @RequestBody body: GenerateProfilePictureUploadUrlRequest
    ): ProfilePictureUploadResponse {
        return userService.generateProfilePictureUploadUrl(
            userId = requestUserId,
            mimeType = body.mimeType
        ).toResponse()
    }

    @PutMapping("/profile")
    @IpRateLimit(
        requests = 30,
        duration = 1L,
        unit = TimeUnit.HOURS
    )
    fun updateProfile(
        @Valid @RequestBody body: UpdateProfileRequest
    ): UserDto {
        return userService.updateProfile(
            userId = requestUserId,
            username = body.username,
            displayName = body.displayName,
            profilePhotoUrl = body.profilePhotoUrl
        ).toUserDto()
    }
}