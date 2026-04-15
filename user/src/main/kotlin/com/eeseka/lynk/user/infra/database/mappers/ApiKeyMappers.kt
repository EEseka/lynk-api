package com.eeseka.lynk.user.infra.database.mappers

import com.eeseka.lynk.user.domain.model.ApiKey
import com.eeseka.lynk.user.infra.database.entities.ApiKeyEntity

fun ApiKeyEntity.toApiKey(): ApiKey {
    return ApiKey(
        key = key,
        validFrom = validFrom,
        expiresAt = expiresAt
    )
}