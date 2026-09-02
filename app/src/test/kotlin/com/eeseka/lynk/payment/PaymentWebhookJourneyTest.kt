package com.eeseka.lynk.payment

import com.eeseka.lynk.hangout.infra.database.repositories.HangoutParticipantRepository
import com.eeseka.lynk.payment.domain.exception.RefundRejectedException
import com.eeseka.lynk.payment.domain.model.PaymentStatus
import com.eeseka.lynk.payment.domain.model.RefundStatus
import com.eeseka.lynk.payment.infra.database.entities.PaymentEntity
import com.eeseka.lynk.payment.infra.database.repositories.PaymentRepository
import com.eeseka.lynk.support.IntegrationTest
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.BDDMockito.willThrow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.post
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.*

/**
 * The whole way in from Paystack: a signed webhook arrives over HTTP, and a payment, a participant,
 * and a refund all end up where they should.
 *
 * This is the only path in the application where money is confirmed, and nobody is logged in for it
 * — Paystack has no account with us, so the signature is the entire authentication.
 */
class PaymentWebhookJourneyTest : IntegrationTest() {

    private companion object {
        const val WEBHOOK_PATH = "/api/payments/webhook"
        const val SIGNATURE_HEADER = "x-paystack-signature"
        const val AMOUNT_OWED_KOBO = 500_000L
    }

    @Autowired
    private lateinit var paymentRepository: PaymentRepository

    @Autowired
    private lateinit var hangoutParticipantRepository: HangoutParticipantRepository

    @Value("\${paystack.secret-key}")
    private lateinit var paystackSecretKey: String

    @Test
    fun `confirms the payment and seats the guest`() {
        val payment = arrangePendingPayment()

        postWebhook(chargeSucceeded(payment.reference, paidAmountKobo = AMOUNT_OWED_KOBO))
            .andExpect { status { isOk() } }

        val confirmed = paymentRepository.findByReference(payment.reference)
        assertNotNull(confirmed)
        assertEquals(PaymentStatus.SUCCESS, confirmed.status)
        assertNotNull(confirmed.paidAt)

        val participant = hangoutParticipantRepository.findByHangoutIdAndHangoutUserUserId(
            hangoutId = confirmed.hangoutId,
            userId = confirmed.userId
        )
        assertNotNull(participant)
        assertTrue(participant.hasPaid, "the guest paid but is still marked unpaid")
    }

    @Test
    fun `turns away a webhook nobody signed`() {
        val payment = arrangePendingPayment()

        mockMvc.post(WEBHOOK_PATH) {
            contentType = MediaType.APPLICATION_JSON
            content = chargeSucceeded(payment.reference, paidAmountKobo = AMOUNT_OWED_KOBO)
        }.andExpect { status { isUnauthorized() } }

        assertEquals(PaymentStatus.PENDING, paymentRepository.findByReference(payment.reference)?.status)
    }

    @Test
    fun `turns away a webhook signed with the wrong secret`() {
        val payment = arrangePendingPayment()
        val body = chargeSucceeded(payment.reference, paidAmountKobo = AMOUNT_OWED_KOBO)

        mockMvc.post(WEBHOOK_PATH) {
            contentType = MediaType.APPLICATION_JSON
            content = body
            header(SIGNATURE_HEADER, sign(body, secret = "somebody-elses-secret"))
        }.andExpect { status { isUnauthorized() } }

        assertEquals(PaymentStatus.PENDING, paymentRepository.findByReference(payment.reference)?.status)
    }

    /**
     * Paystack retries a webhook it did not hear back from, so the same charge arriving twice is
     * ordinary traffic rather than an attack. The second one must not move anything.
     */
    @Test
    fun `takes the same charge twice without paying it twice`() {
        val payment = arrangePendingPayment()
        val body = chargeSucceeded(payment.reference, paidAmountKobo = AMOUNT_OWED_KOBO)

        postWebhook(body).andExpect { status { isOk() } }
        val afterFirst = paymentRepository.findByReference(payment.reference)!!.paidAt

        postWebhook(body).andExpect { status { isOk() } }
        val afterSecond = paymentRepository.findByReference(payment.reference)!!

        assertEquals(PaymentStatus.SUCCESS, afterSecond.status)
        assertEquals(afterFirst, afterSecond.paidAt, "the repeat webhook confirmed the payment again")
        assertEquals(RefundStatus.NONE, afterSecond.refundStatus, "a retry was mistaken for a second payment")
    }

    /**
     * Two devices, two checkouts, two real charges. One of them buys the seat and the other goes
     * straight back — and the row it goes back on is a success the database will not hold twice for one
     * person on one hangout, so the refund has to be claimed by the write that records the charge.
     */
    @Test
    fun `sends back a second charge the same guest made from another device`() {
        val (first, second) = arrangeTwoPendingPayments()

        postWebhook(chargeSucceeded(first.reference, paidAmountKobo = AMOUNT_OWED_KOBO))
            .andExpect { status { isOk() } }
        postWebhook(chargeSucceeded(second.reference, paidAmountKobo = AMOUNT_OWED_KOBO))
            .andExpect { status { isOk() } }

        val keeper = paymentRepository.findByReference(first.reference)
        assertNotNull(keeper)
        assertEquals(PaymentStatus.SUCCESS, keeper.status)
        assertEquals(RefundStatus.NONE, keeper.refundStatus, "the payment that bought the seat was sent back")

        val duplicate = paymentRepository.findByReference(second.reference)
        assertNotNull(duplicate)
        assertEquals(PaymentStatus.SUCCESS, duplicate.status, "the second charge was real and has to say so")
        assertEquals(RefundStatus.REQUESTED, duplicate.refundStatus, "the second charge was never sent back")

        val participant = hangoutParticipantRepository.findByHangoutIdAndHangoutUserUserId(
            hangoutId = keeper.hangoutId,
            userId = keeper.userId
        )
        assertNotNull(participant)
        assertTrue(participant.hasPaid, "the guest paid but is still marked unpaid")
    }

    /**
     * The charge went through, so the money is real, but it is short of what was owed. It buys no
     * seat, and we do not keep it.
     */
    @Test
    fun `fails a payment that came up short and sends the money back`() {
        val payment = arrangePendingPayment()

        postWebhook(chargeSucceeded(payment.reference, paidAmountKobo = 499_999L))
            .andExpect { status { isOk() } }

        val failed = paymentRepository.findByReference(payment.reference)
        assertNotNull(failed)
        assertEquals(PaymentStatus.FAILED, failed.status)
        assertNotNull(failed.failureReason)
        assertEquals(RefundStatus.REQUESTED, failed.refundStatus)

        val participant = hangoutParticipantRepository.findByHangoutIdAndHangoutUserUserId(
            hangoutId = failed.hangoutId,
            userId = failed.userId
        )
        assertNotNull(participant)
        assertFalse(participant.hasPaid, "a short payment seated the guest anyway")
    }

    /**
     * Paystack can refuse a refund. The money is then still ours to send back by hand, so the payment
     * has to be left saying so rather than looking like a refund on its way.
     */
    @Test
    fun `records a refund the provider refused`() {
        val payment = arrangePendingPayment()
        willThrow(RefundRejectedException("Transaction has been fully reversed"))
            .given(paystackClient).refund(anyString(), anyLong())

        postWebhook(chargeSucceeded(payment.reference, paidAmountKobo = AMOUNT_OWED_KOBO - 1))
            .andExpect { status { isOk() } }

        val refused = paymentRepository.findByReference(payment.reference)
        assertNotNull(refused)
        assertEquals(RefundStatus.FAILED, refused.refundStatus)
        assertNull(refused.refundedAmountKobo, "nothing went back, so no amount should be recorded")
    }

    @Test
    fun `accepts a signed webhook for a reference it has never heard of`() {
        postWebhook(chargeSucceeded("lynk-a-reference-we-never-issued", paidAmountKobo = AMOUNT_OWED_KOBO))
            .andExpect { status { isOk() } }

        assertEquals(0, paymentRepository.count())
    }

    /** A guest who is attending a hangout with money on it and has a charge in flight. */
    private fun arrangePendingPayment(): PaymentEntity {
        val host = fixtures.user(displayName = "Ada")
        val guest = fixtures.user(displayName = "Bola")
        val hangout = fixtures.hangout(host = host)
        fixtures.participant(hangout = hangout, user = host)
        fixtures.participant(hangout = hangout, user = guest)

        return fixtures.payment(
            hangoutId = hangout.id!!,
            userId = guest.userId,
            amountKobo = AMOUNT_OWED_KOBO
        )
    }

    /** One guest with two checkouts in flight on the same hangout, as two devices would leave them. */
    private fun arrangeTwoPendingPayments(): Pair<PaymentEntity, PaymentEntity> {
        val host = fixtures.user(displayName = "Ada")
        val guest = fixtures.user(displayName = "Bola")
        val hangout = fixtures.hangout(host = host)
        fixtures.participant(hangout = hangout, user = host)
        fixtures.participant(hangout = hangout, user = guest)

        val first = fixtures.payment(
            hangoutId = hangout.id!!,
            userId = guest.userId,
            amountKobo = AMOUNT_OWED_KOBO
        )
        val second = fixtures.payment(
            hangoutId = hangout.id!!,
            userId = guest.userId,
            amountKobo = AMOUNT_OWED_KOBO
        )

        return first to second
    }

    private fun postWebhook(body: String) = mockMvc.post(WEBHOOK_PATH) {
        contentType = MediaType.APPLICATION_JSON
        content = body
        header(SIGNATURE_HEADER, sign(body, secret = paystackSecretKey))
    }

    private fun chargeSucceeded(reference: String, paidAmountKobo: Long) = """
        {
          "event": "charge.success",
          "data": {
            "reference": "$reference",
            "amount": $paidAmountKobo,
            "status": "success",
            "gateway_response": "Successful"
          }
        }
    """.trimIndent()

    private fun sign(body: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA512")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA512"))

        return mac.doFinal(body.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
