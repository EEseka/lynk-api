package com.eeseka.lynk.payment.infra.database.repositories

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.payment.infra.database.entities.HangoutPayoutAccountEntity
import org.springframework.data.jpa.repository.JpaRepository

interface HangoutPayoutAccountRepository : JpaRepository<HangoutPayoutAccountEntity, Long> {
    fun findByHangoutId(hangoutId: HangoutId): HangoutPayoutAccountEntity?
    fun findByTransferReference(transferReference: String): HangoutPayoutAccountEntity?
}