package com.eeseka.lynk.hangout.infra.database.entities

import com.eeseka.lynk.hangout.domain.model.PaymentState
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import java.time.Instant

/**
 * The one place in these entities where a nullable column does not mean a nullable value. The
 * columns have to be nullable because an all-null row is what Hibernate reads back as a null
 * [HangoutEntity.payment], and that is how "payments were never turned on" is represented. The
 * values themselves are written together and are never individually absent, so they stay non-null,
 * and callers check once, on the whole thing, instead of five times.
 */
@Embeddable
class HangoutPaymentEntity(
    @Column(nullable = true)
    var totalCostKobo: Long,

    @Column(nullable = true)
    var costPerPersonKobo: Long,

    @Column(nullable = true)
    var splitHeadcount: Int,

    @Column(name = "payment_deadline", nullable = true)
    var deadline: Instant,

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_state", nullable = true)
    var state: PaymentState
)