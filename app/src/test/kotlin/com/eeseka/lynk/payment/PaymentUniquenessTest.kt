package com.eeseka.lynk.payment

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.hangout.infra.database.entities.HangoutUserEntity
import com.eeseka.lynk.payment.domain.model.PaymentStatus
import com.eeseka.lynk.payment.domain.model.PayoutStatus
import com.eeseka.lynk.payment.domain.model.RefundStatus
import com.eeseka.lynk.payment.infra.database.entities.PaymentEntity
import com.eeseka.lynk.payment.infra.database.entities.RefundEntity
import com.eeseka.lynk.payment.infra.database.repositories.PaymentRepository
import com.eeseka.lynk.payment.infra.database.repositories.RefundRepository
import com.eeseka.lynk.support.IntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * The partial unique indexes JPA cannot express, and the reason the tests run against a real Postgres
 * rather than an in-memory database.
 *
 * They are the last thing standing between an attendee who taps Pay twice and being charged twice, a
 * payment being sent back twice, and a host being paid twice.
 */
class PaymentUniquenessTest : IntegrationTest() {

    @Autowired
    private lateinit var paymentRepository: PaymentRepository

    @Autowired
    private lateinit var refundRepository: RefundRepository

    @Test
    fun `refuses a second live payment by the same attendee for the same hangout`() {
        val (hangoutId, attendee) = arrangePaidAttendee()

        assertFailsWith<DataIntegrityViolationException> {
            paymentRepository.saveAndFlush(
                successfulPayment(hangoutId = hangoutId, userId = attendee)
            )
        }
    }

    // Without this an attendee who paid, left and was invited back could never pay again, and only after Paystack took the money
    @Test
    fun `lets an attendee pay again once the first payment was sent back`() {
        val (hangoutId, attendee) = arrangePaidAttendee(status = PaymentStatus.REFUNDED)

        paymentRepository.saveAndFlush(successfulPayment(hangoutId = hangoutId, userId = attendee))

        assertEquals(1, paymentRepository.findAllByHangoutIdAndUserIdAndStatus(
            hangoutId = hangoutId,
            userId = attendee,
            status = PaymentStatus.SUCCESS
        ).size)
    }

    @Test
    fun `lets an attendee have a failed payment alongside a successful one`() {
        val (hangoutId, attendee) = arrangePaidAttendee()

        paymentRepository.saveAndFlush(
            successfulPayment(hangoutId = hangoutId, userId = attendee).apply {
                status = PaymentStatus.FAILED
            }
        )
    }

    @Test
    fun `lets two attendees pay for the same hangout`() {
        val (hangoutId, _) = arrangePaidAttendee()
        val otherAttendee = fixtures.user(displayName = "Chidi")

        paymentRepository.saveAndFlush(
            successfulPayment(hangoutId = hangoutId, userId = otherAttendee.userId)
        )
    }

    @Test
    fun `refuses a second refund in flight for the same payment`() {
        val (hangoutId, attendee) = arrangePaidAttendee(status = PaymentStatus.REFUNDING)
        val payment = paymentRepository.findAllByHangoutIdAndUserIdAndStatus(hangoutId, attendee, PaymentStatus.REFUNDING).single()
        refundRepository.saveAndFlush(refund(payment, RefundStatus.REQUESTED))

        assertFailsWith<DataIntegrityViolationException> {
            refundRepository.saveAndFlush(refund(payment, RefundStatus.REQUESTED))
        }
    }

    @Test
    fun `keeps every refused refund beside the one in flight`() {
        val (hangoutId, attendee) = arrangePaidAttendee(status = PaymentStatus.REFUNDING)
        val payment = paymentRepository.findAllByHangoutIdAndUserIdAndStatus(hangoutId, attendee, PaymentStatus.REFUNDING).single()

        refundRepository.saveAndFlush(refund(payment, RefundStatus.FAILED))
        refundRepository.saveAndFlush(refund(payment, RefundStatus.FAILED))
        refundRepository.saveAndFlush(refund(payment, RefundStatus.REQUESTED))

        assertEquals(3, refundRepository.count())
    }

    @Test
    fun `refuses a second live payout for the same hangout`() {
        val (hangoutId, _) = arrangePaidAttendee()
        fixtures.payout(hangoutId = hangoutId, status = PayoutStatus.SUCCEEDED)

        assertFailsWith<DataIntegrityViolationException> {
            fixtures.payout(hangoutId = hangoutId, status = PayoutStatus.IN_FLIGHT)
        }
    }

    @Test
    fun `lets a hangout try a payout again after one was refused`() {
        val (hangoutId, _) = arrangePaidAttendee()
        fixtures.payout(hangoutId = hangoutId, status = PayoutStatus.FAILED)

        fixtures.payout(hangoutId = hangoutId, status = PayoutStatus.IN_FLIGHT)
    }

    private fun arrangePaidAttendee(
        status: PaymentStatus = PaymentStatus.SUCCESS
    ): Pair<HangoutId, UserId> {
        val host = fixtures.user(displayName = "Ada")
        val attendee: HangoutUserEntity = fixtures.user(displayName = "Bola")
        val hangout = fixtures.hangout(host = host)
        fixtures.participant(hangout = hangout, user = host)
        fixtures.participant(hangout = hangout, user = attendee)
        fixtures.payment(
            hangoutId = hangout.id!!,
            userId = attendee.userId,
            status = status,
            paidAt = Instant.now()
        )

        return hangout.id!! to attendee.userId
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

    private fun refund(payment: PaymentEntity, status: RefundStatus) = RefundEntity(
        paymentId = payment.id!!,
        amountKobo = payment.netAmountKobo,
        status = status
    )
}
