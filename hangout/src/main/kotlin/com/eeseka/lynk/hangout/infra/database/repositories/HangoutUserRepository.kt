package com.eeseka.lynk.hangout.infra.database.repositories

import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.hangout.infra.database.entities.HangoutUserEntity
import org.springframework.data.jpa.repository.JpaRepository

interface HangoutUserRepository : JpaRepository<HangoutUserEntity, UserId> {
    fun findByUsername(username: String): HangoutUserEntity?
}
