package com.eeseka.lynk.user.api.mappers

import com.eeseka.lynk.user.api.dto.AuthenticatedUserDto
import com.eeseka.lynk.user.api.dto.UserDto
import com.eeseka.lynk.user.domain.model.AuthenticatedUser
import com.eeseka.lynk.user.domain.model.User

fun AuthenticatedUser.toAuthenticatedUserDto(): AuthenticatedUserDto {
    return AuthenticatedUserDto(
        user = user.toUserDto(),
        accessToken = accessToken,
        refreshToken = refreshToken
    )
}

fun User.toUserDto(): UserDto {
    return UserDto(
        id = id,
        authProvider = authProvider,
        email = email,
        displayName = displayName,
        username = username,
        profilePhotoUrl = profilePhotoUrl
    )
}