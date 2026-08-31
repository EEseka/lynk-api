package com.eeseka.lynk.user.api.controllers

import com.eeseka.lynk.common.api.util.requestUserId
import com.eeseka.lynk.common.api.config.AllowGuest
import com.eeseka.lynk.common.api.config.IpRateLimit
import com.eeseka.lynk.user.api.dto.AuthenticatedUserDto
import com.eeseka.lynk.user.api.dto.GoogleLoginRequest
import com.eeseka.lynk.user.api.dto.RefreshRequest
import com.eeseka.lynk.user.api.mappers.toAuthenticatedUserDto
import com.eeseka.lynk.user.service.AuthService
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.*
import java.util.concurrent.TimeUnit

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

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
        requests = 20,
        duration = 1L,
        unit = TimeUnit.HOURS
    )
    fun guestLogin(request: HttpServletRequest): AuthenticatedUserDto {
        val user = authService.guestLogin()
        // The cap above is a guess until there are real numbers. Counting distinct users per ipaddress
        // per hour is what settles it, and carriers put many subscribers behind one ipaddress.
        logger.info("Guest {} signed in from {}", user.user.id, request.remoteAddr)
        return user.toAuthenticatedUserDto()
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

    @AllowGuest
    @PostMapping("/logout")
    fun logout(
        @Valid @RequestBody body: RefreshRequest
    ) {
        authService.logout(body.refreshToken)
    }

    @AllowGuest
    @DeleteMapping("/account")
    fun deleteAccount() {
        authService.deleteAccount(requestUserId)
    }
}