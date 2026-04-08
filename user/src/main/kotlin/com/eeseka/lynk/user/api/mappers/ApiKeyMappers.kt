package com.eeseka.lynk.user.api.mappers

import com.eeseka.lynk.user.api.dto.ApiKeyDto
import com.eeseka.lynk.user.domain.model.ApiKey

fun ApiKey.toApiKeyDto(): ApiKeyDto {
    return ApiKeyDto(
        key = key,
        validFrom = validFrom,
        expiresAt = expiresAt,
    )
}