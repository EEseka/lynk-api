package com.eeseka.lynk.payment.infra.database.repositories

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.payment.infra.database.entities.HangoutPayoutAccountEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

interface HangoutPayoutAccountRepository : JpaRepository<HangoutPayoutAccountEntity, Long> {
    fun findByHangoutId(hangoutId: HangoutId): HangoutPayoutAccountEntity?
    fun findByTransferReference(transferReference: String): HangoutPayoutAccountEntity?

    @Transactional
    @Modifying
    @Query("""
        UPDATE HangoutPayoutAccountEntity a
        SET a.transferReference = :transferReference, a.updatedAt = :updatedAt
        WHERE a.hangoutId = :hangoutId
    """)
    fun updateTransferReferenceByHangoutId(
        hangoutId: HangoutId,
        transferReference: String,
        updatedAt: Instant
    )
}
