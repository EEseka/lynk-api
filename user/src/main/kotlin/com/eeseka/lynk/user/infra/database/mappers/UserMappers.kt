package com.eeseka.lynk.user.infra.database.mappers

import com.eeseka.lynk.user.domain.model.User
import com.eeseka.lynk.user.infra.database.entities.UserEntity

fun UserEntity.toUser(): User {
    return User(
        id = id!!,
        authProvider = authProvider,
        email = email,
        displayName = displayName,
        username = username,
        profilePhotoUrl = profilePhotoUrl
    )
}