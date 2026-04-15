package com.eeseka.lynk.user.domain.model

import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.user.domain.type.AuthProvider

data class User(
    val id: UserId,
    val authProvider: AuthProvider,
    val email: String?, // Nullable because a Guest user has no email
    val displayName: String?, // Nullable because a Guest user has no display name
    val username: String?, // Nullable because they might not have finished Profile Setup or have a Guest account
    val profilePhotoUrl: String?
)