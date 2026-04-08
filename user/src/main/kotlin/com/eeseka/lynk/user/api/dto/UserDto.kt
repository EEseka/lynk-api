package com.eeseka.lynk.user.api.dto

import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.user.domain.type.AuthProvider

data class UserDto(
    val id: UserId,
    val authProvider: AuthProvider,
    val email: String?,
    val displayName: String?,
    val username: String?,
    val profilePhotoUrl: String?
)