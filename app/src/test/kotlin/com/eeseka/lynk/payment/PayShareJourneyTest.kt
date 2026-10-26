package com.eeseka.lynk.payment

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.payment.domain.PaystackFees
import com.eeseka.lynk.payment.domain.model.PaymentStatus
import com.eeseka.lynk.payment.domain.model.ProviderTransaction
import com.eeseka.lynk.payment.domain.model.ProviderTransactionStatus
import com.eeseka.lynk.payment.infra.database.repositories.PaymentRepository
import com.eeseka.lynk.payment.service.RefundService
import com.eeseka.lynk.support.IntegrationTest
import com.eeseka.lynk.support.PaidHangout
import com.eeseka.lynk.support.TestAccount
import com.eeseka.lynk.support.authenticatedAs
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.post
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * An attendee paying their share: the charge Paystack is asked for, and what checking on it later says.
 *
 * The amount is the part that matters most — it is the only place the gross-up in [PaystackFees]
 * meets a real hangout, and it is what a card is actually charged.
 */
class PayShareJourneyTest : IntegrationTest() {

    private companion object {
        const val SHARE_KOBO = 500_000L
        const val AUTHORIZATION_URL = "https://checkout.paystack.com/test-charge"
    }

    @Autowired
    private lateinit var paymentRepository: PaymentRepository

    @Autowired
    private lateinit var refundService: RefundService

    @Test
    fun `charges the attendee their share plus what Paystack will take`() {
        val paid = arrangePaidHangout()
        given(paystackClient.initializeCharge(any(), any(), any(), any(), any()))
            .willReturn(AUTHORIZATION_URL)

        initialize(paid.attendee, paid.hangoutId).andExpect {
            status { isOk() }
            jsonPath("$.authorizationUrl") { value(AUTHORIZATION_URL) }
        }

        val payment = paymentRepository.findAll().single()
        assertEquals(SHARE_KOBO, payment.netAmountKobo, "the host must still receive the whole share")
        assertEquals(PaystackFees.grossUpKobo(SHARE_KOBO), payment.amountKobo)
        assertEquals(PaymentStatus.PENDING, payment.status)
        assertEquals(paid.attendee.userId, payment.userId)
    }

    @Test
    fun `will not let the host pay their own hangout`() {
        val paid = arrangePaidHangout()

        initialize(paid.host, paid.hangoutId).andExpect { status { isForbidden() } }
    }

    @Test
    fun `will not let somebody outside the hangout pay`() {
        val paid = arrangePaidHangout()
        val stranger = signIn("chidi")

        initialize(stranger, paid.hangoutId).andExpect { status { isNotFound() } }
    }

    @Test
    fun `will not take a second payment for the same share`() {
        val paid = arrangePaidHangout()
        given(paystackClient.initializeCharge(any(), any(), any(), any(), any()))
            .willReturn(AUTHORIZATION_URL)
        initialize(paid.attendee, paid.hangoutId).andExpect { status { isOk() } }
        confirmPaymentOf(paid.attendee, paid.hangoutId)

        initialize(paid.attendee, paid.hangoutId).andExpect { status { isConflict() } }
    }

    /** The share they paid the first time was sent back, so it is not a reason to turn them away. */
    @Test
    fun `lets an attendee pay again after leaving and being invited back`() {
        val paid = arrangePaidHangout()
        given(paystackClient.initializeCharge(any(), any(), any(), any(), any()))
            .willReturn(AUTHORIZATION_URL)
        initialize(paid.attendee, paid.hangoutId).andExpect { status { isOk() } }
        confirmPaymentOf(paid.attendee, paid.hangoutId)
        val first = paymentRepository.findAll().single().reference
        leave(paid.attendee, paid.hangoutId)
        refundService.applyRefundOutcome(first, succeeded = true)
        hangouts.invite(paid.host, paid.hangoutId, paid.attendee)
        hangouts.accept(paid.attendee, paid.hangoutId)

        initialize(paid.attendee, paid.hangoutId).andExpect { status { isOk() } }
        confirmPaymentOf(paid.attendee, paid.hangoutId)

        assertEquals(PaymentStatus.REFUNDED, paymentRepository.findByReference(first)?.status)
        val second = paymentRepository.findAll().single { it.reference != first }
        assertEquals(PaymentStatus.SUCCESS, second.status)
    }

    @Test
    fun `will not take a payment after the deadline`() {
        val paid = arrangePaidHangout()
        fixtures.movePaymentDeadline(paid.hangoutId, Instant.now().minus(Duration.ofHours(1)))

        initialize(paid.attendee, paid.hangoutId).andExpect { status { isConflict() } }
    }

    @Test
    fun `confirms a payment when the attendee comes back and Paystack says it went through`() {
        val paid = arrangePaidHangout()
        given(paystackClient.initializeCharge(any(), any(), any(), any(), any()))
            .willReturn(AUTHORIZATION_URL)
        initialize(paid.attendee, paid.hangoutId).andExpect { status { isOk() } }

        val reference = paymentRepository.findAll().single().reference
        given(paystackClient.verifyTransaction(eq(reference))).willReturn(
            ProviderTransaction(
                status = ProviderTransactionStatus.SUCCEEDED,
                paidAmountKobo = PaystackFees.grossUpKobo(SHARE_KOBO)
            )
        )

        verify(paid.attendee, paid.hangoutId).andExpect {
            status { isOk() }
            jsonPath("$.status") { value(PaymentStatus.SUCCESS.name) }
        }

        assertEquals(PaymentStatus.SUCCESS, paymentRepository.findByReference(reference)?.status)
    }

    /** Paystack is still thinking. There is no answer to give, and nothing should change. */
    @Test
    fun `says a payment is still pending while Paystack decides`() {
        val paid = arrangePaidHangout()
        given(paystackClient.initializeCharge(any(), any(), any(), any(), any()))
            .willReturn(AUTHORIZATION_URL)
        initialize(paid.attendee, paid.hangoutId).andExpect { status { isOk() } }
        given(paystackClient.verifyTransaction(any()))
            .willReturn(ProviderTransaction(ProviderTransactionStatus.PENDING, null))

        verify(paid.attendee, paid.hangoutId).andExpect {
            status { isOk() }
            jsonPath("$.status") { value(PaymentStatus.PENDING.name) }
        }
    }

    @Test
    fun `fails a payment Paystack says did not go through`() {
        val paid = arrangePaidHangout()
        given(paystackClient.initializeCharge(any(), any(), any(), any(), any()))
            .willReturn(AUTHORIZATION_URL)
        initialize(paid.attendee, paid.hangoutId).andExpect { status { isOk() } }
        given(paystackClient.verifyTransaction(any()))
            .willReturn(ProviderTransaction(ProviderTransactionStatus.FAILED, null))

        verify(paid.attendee, paid.hangoutId).andExpect {
            status { isOk() }
            jsonPath("$.status") { value(PaymentStatus.FAILED.name) }
        }

        val payment = paymentRepository.findAll().single()
        assertEquals(PaymentStatus.FAILED, payment.status)
        assertNotNull(payment.failureReason)
    }

    @Test
    fun `has nothing to check for somebody who never started paying`() {
        val paid = arrangePaidHangout()

        verify(paid.attendee, paid.hangoutId).andExpect { status { isConflict() } }
    }

    private fun initialize(account: TestAccount, hangoutId: HangoutId): ResultActionsDsl =
        mockMvc.post("/api/payments/hangouts/$hangoutId/initialize") {
            authenticatedAs(account)
        }

    private fun verify(account: TestAccount, hangoutId: HangoutId): ResultActionsDsl =
        mockMvc.post("/api/payments/hangouts/$hangoutId/verify") {
            authenticatedAs(account)
        }

    /** Leaving is what sends a paid share back once the event reaches the payment module. */
    private fun leave(account: TestAccount, hangoutId: HangoutId) {
        mockMvc.delete("/api/hangouts/$hangoutId/leave") {
            authenticatedAs(account)
        }.andExpect { status { isNoContent() } }
        broker.deliverEvents()
    }

    /** Puts the money through by the same route Paystack's webhook would. */
    private fun confirmPaymentOf(account: TestAccount, hangoutId: HangoutId) {
        val reference = paymentRepository
            .findAllByHangoutIdAndUserIdAndStatus(hangoutId, account.userId, PaymentStatus.PENDING)
            .single().reference
        given(paystackClient.verifyTransaction(eq(reference))).willReturn(
            ProviderTransaction(
                status = ProviderTransactionStatus.SUCCEEDED,
                paidAmountKobo = PaystackFees.grossUpKobo(SHARE_KOBO)
            )
        )

        verify(account, hangoutId).andExpect { status { isOk() } }
    }

    private fun arrangePaidHangout(): PaidHangout =
        hangouts.withPaymentsOn(totalCostKobo = SHARE_KOBO * 2)

    private fun signIn(username: String): TestAccount = accounts.signIn(
        email = "$username@lynk.test",
        displayName = username.replaceFirstChar { it.uppercase() },
        username = username
    )
}
