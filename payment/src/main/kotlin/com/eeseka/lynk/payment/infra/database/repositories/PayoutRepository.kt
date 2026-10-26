package com.eeseka.lynk.payment.infra.database.repositories

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.payment.domain.model.PayoutStatus
import com.eeseka.lynk.payment.infra.database.entities.PayoutEntity
import org.springframework.data.jpa.repository.JpaRepository

interface PayoutRepository : JpaRepository<PayoutEntity, Long> {

    fun findByReference(reference: String): PayoutEntity?

    // A hangout has at most one transfer in flight
    fun findByHangoutIdAndStatus(hangoutId: HangoutId, status: PayoutStatus): PayoutEntity?

    fun findFirstByHangoutIdAndStatusOrderByCreatedAtDesc(hangoutId: HangoutId, status: PayoutStatus): PayoutEntity?
}
