package com.eeseka.lynk.payment

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.hangout.infra.database.entities.HangoutUserEntity
import com.eeseka.lynk.payment.domain.model.PaymentStatus
import com.eeseka.lynk.payment.domain.model.RefundStatus
import com.eeseka.lynk.payment.infra.database.entities.PaymentEntity
import com.eeseka.lynk.payment.infra.database.repositories.PaymentRepository
import com.eeseka.lynk.support.IntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * `V2__one_live_success_payment_per_user_per_hangout.sql` — the partial unique index that JPA cannot
 * express, and the reason the tests run against a real Postgres rather than an in-memory database.
 *
 * It is the last thing standing between a guest who taps Pay twice and being charged twice.
 */
class PaymentUniquenessTest : IntegrationTest() {

    @Autowired
    private lateinit var paymentRepository: PaymentRepository

    @Test
    fun `refuses a second live payment by the same guest for the same hangout`() {
        val (hangoutId, guest) = arrangePaidGuest()

        assertFailsWith<DataIntegrityViolationException> {
            paymentRepository.saveAndFlush(
                successfulPayment(hangoutId = hangoutId, userId = guest)
            )
        }
    }

    /**
     * A refund never changes `status`, so a refunded payment stays SUCCESS forever. Without the
     * `refund_status` half of the index, that row would block the guest from ever paying again —
     * and the failure would land after Paystack had already taken the money.
     */
    @Test
    fun `lets a guest pay again once the first payment was sent back`() {
        val (hangoutId, guest) = arrangePaidGuest(refundStatus = RefundStatus.PROCESSED)

        paymentRepository.saveAndFlush(successfulPayment(hangoutId = hangoutId, userId = guest))

        assertEquals(2, paymentRepository.findAllByHangoutIdAndUserIdAndStatus(
            hangoutId = hangoutId,
            userId = guest,
            status = PaymentStatus.SUCCESS
        ).size)
    }

    @Test
    fun `lets a guest have a failed payment alongside a successful one`() {
        val (hangoutId, guest) = arrangePaidGuest()

        paymentRepository.saveAndFlush(
            successfulPayment(hangoutId = hangoutId, userId = guest).apply {
                status = PaymentStatus.FAILED
            }
        )
    }

    @Test
    fun `lets two guests pay for the same hangout`() {
        val (hangoutId, _) = arrangePaidGuest()
        val otherGuest = fixtures.user(displayName = "Chidi")

        paymentRepository.saveAndFlush(
            successfulPayment(hangoutId = hangoutId, userId = otherGuest.userId)
        )
    }

    private fun arrangePaidGuest(
        refundStatus: RefundStatus = RefundStatus.NONE
    ): Pair<HangoutId, UserId> {
        val host = fixtures.user(displayName = "Ada")
        val guest: HangoutUserEntity = fixtures.user(displayName = "Bola")
        val hangout = fixtures.hangout(host = host)
        fixtures.participant(hangout = hangout, user = host)
        fixtures.participant(hangout = hangout, user = guest)
        fixtures.payment(
            hangoutId = hangout.id!!,
            userId = guest.userId,
            status = PaymentStatus.SUCCESS,
            refundStatus = refundStatus,
            paidAt = Instant.now()
        )

        return hangout.id!! to guest.userId
    }

    private fun successfulPayment(hangoutId: HangoutId, userId: UserId) = PaymentEntity(
        hangoutId = hangoutId,
        userId = userId,
        reference = "lynk-${UUID.randomUUID()}",
        amountKobo = 500_000L,
        netAmountKobo = 500_000L,
        status = PaymentStatus.SUCCESS,
        paidAt = Instant.now()
    )
}
