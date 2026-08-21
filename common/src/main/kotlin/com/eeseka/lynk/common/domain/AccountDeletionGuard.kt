package com.eeseka.lynk.common.domain

import com.eeseka.lynk.common.domain.type.UserId


interface AccountDeletionGuard {

    /** Throws when this module still holds work that deleting the account would strand. */
    fun assertAccountCanBeDeleted(userId: UserId)
}