package com.eeseka.lynk.user.api.controllers

import com.eeseka.lynk.common.api.config.IpRateLimit
import com.eeseka.lynk.user.api.dto.ApiKeyDto
import com.eeseka.lynk.user.api.dto.CreateApiKeyRequest
import com.eeseka.lynk.user.api.mappers.toApiKeyDto
import com.eeseka.lynk.user.service.ApiKeyService
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.TimeUnit

@RestController
@RequestMapping("/api/auth/apiKey")
class ApiKeyController(
    private val apiKeyService: ApiKeyService,
    @param:Value("\${lynk.api-key.admin.username}")
    private val adminUsername: String,
    @param:Value("\${lynk.api-key.admin.password}")
    private val adminPassword: String,
) {
    @PostMapping
    @IpRateLimit(
        requests = 3,
        duration = 1L,
        unit = TimeUnit.HOURS
    )
    fun createApiKey(
        @RequestHeader("Authorization") authHeader: String,
        @RequestBody body: CreateApiKeyRequest
    ): ApiKeyDto {
        if (!isAuthorized(authHeader)) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
        }

        return apiKeyService.createKey(body.email).toApiKeyDto()
    }

    private fun isAuthorized(authHeader: String?): Boolean {
        if (authHeader == null || !authHeader.startsWith("Basic ")) {
            return false
        }

        return try {
            val base64Credentials = authHeader.substringAfter("Basic ")
            val credentials = String(Base64.getDecoder().decode(base64Credentials))
            val parts = credentials.split(":", limit = 2)

            if (parts.size != 2) {
                return false
            }

            val username = parts[0]
            val password = parts[1]

            isEqual(username, adminUsername) && isEqual(password, adminPassword)
        } catch (_: Exception) {
            false
        }
    }

    private fun isEqual(given: String, expected: String): Boolean = MessageDigest.isEqual(
        given.toByteArray(Charsets.UTF_8),
        expected.toByteArray(Charsets.UTF_8)
    )
}