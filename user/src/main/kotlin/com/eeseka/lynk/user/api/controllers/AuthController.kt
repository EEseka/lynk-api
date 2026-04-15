package com.eeseka.lynk.user.api.controllers

import com.eeseka.lynk.common.api.util.requestUserId
import com.eeseka.lynk.user.api.config.IpRateLimit
import com.eeseka.lynk.user.api.dto.AuthenticatedUserDto
import com.eeseka.lynk.user.api.dto.GoogleLoginRequest
import com.eeseka.lynk.user.api.dto.RefreshRequest
import com.eeseka.lynk.user.api.mappers.toAuthenticatedUserDto
import com.eeseka.lynk.user.service.AuthService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*
import java.util.concurrent.TimeUnit

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService
) {

    @PostMapping("/google")
    @IpRateLimit(
        requests = 50,
        duration = 1L,
        unit = TimeUnit.HOURS
    )
    fun googleLogin(
        @Valid @RequestBody body: GoogleLoginRequest
    ): AuthenticatedUserDto {
        return authService.googleLogin(body.token).toAuthenticatedUserDto()
    }

    @PostMapping("/guest")
    @IpRateLimit(
        requests = 50,
        duration = 1L,
        unit = TimeUnit.HOURS
    )
    fun guestLogin(): AuthenticatedUserDto {
        return authService.guestLogin().toAuthenticatedUserDto()
    }

    @PostMapping("/refresh")
    @IpRateLimit(
        requests = 50,
        duration = 1L,
        unit = TimeUnit.HOURS
    )
    fun refresh(
        @Valid @RequestBody body: RefreshRequest
    ): AuthenticatedUserDto {
        return authService.refresh(body.refreshToken).toAuthenticatedUserDto()
    }

    @PostMapping("/logout")
    fun logout(
        @Valid @RequestBody body: RefreshRequest
    ) {
        authService.logout(body.refreshToken)
    }

    @DeleteMapping("/account")
    fun deleteAccount() {
        authService.deleteAccount(requestUserId)
    }
}