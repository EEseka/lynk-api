package com.eeseka.lynk.payment

import com.eeseka.lynk.hangout.domain.exception.HangoutAccessDeniedException
import com.eeseka.lynk.hangout.domain.exception.HangoutIllegalStateException
import com.eeseka.lynk.hangout.domain.model.PaymentState
import com.eeseka.lynk.hangout.infra.database.entities.HangoutEntity
import com.eeseka.lynk.payment.domain.exception.PaymentAccessDeniedException
import com.eeseka.lynk.payment.domain.exception.PaymentIllegalStateException
import com.eeseka.lynk.payment.domain.exception.PayoutRejectedException
import com.eeseka.lynk.payment.domain.model.BankAccount
import com.eeseka.lynk.payment.domain.model.PaymentStatus
import com.eeseka.lynk.payment.infra.database.entities.PayoutEntity
import com.eeseka.lynk.payment.infra.database.repositories.HangoutPayoutAccountRepository
import com.eeseka.lynk.payment.infra.database.repositories.PayoutRepository
import com.eeseka.lynk.payment.service.PaymentSetupService
import com.eeseka.lynk.payment.service.PayoutService
import com.eeseka.lynk.support.IntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.then
import org.mockito.BDDMockito.willThrow
import org.mockito.Mockito.reset
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

/**
 * A wrong or closed account must be fixable, but only while the money has not started moving to it.
 */
class ChangePayoutAccountTest : IntegrationTest() {

    private companion object {
        const val OLD_RECIPIENT = "RCP_old"
        const val NEW_RECIPIENT = "RCP_new"
        const val ACCOUNT_NUMBER = "0123456789"
        const val BANK_CODE = "058"
    }

    @Autowired
    private lateinit var paymentSetupService: PaymentSetupService

    @Autowired
    private lateinit var payoutService: PayoutService

    @Autowired
    private lateinit var payoutRepository: PayoutRepository

    @Autowired
    private lateinit var hangoutPayoutAccountRepository: HangoutPayoutAccountRepository

    @BeforeEach
    fun stubPaystack() {
        given(paystackClient.getBanks()).willReturn(emptyList())
        given(paystackClient.resolveAccount(ACCOUNT_NUMBER, BANK_CODE))
            .willReturn(BankAccount(ACCOUNT_NUMBER, "ADA NEW", BANK_CODE))
        given(paystackClient.createTransferRecipient(any())).willReturn(NEW_RECIPIENT)
    }

    @Test
    fun `saves the new account while payments are still collecting`() {
        val hangout = arrangeHangout(PaymentState.COLLECTING)

        change(hangout)

        val payoutAccount = hangoutPayoutAccountRepository.findByHangoutId(hangout.id!!)!!
        assertEquals(NEW_RECIPIENT, payoutAccount.recipientCode)
        assertEquals("6789", payoutAccount.accountNumberLast4)
        assertEquals("ADA NEW", payoutAccount.accountHolderName)
    }

    @Test
    fun `pays the new account once a refused payout is retried`() {
        val hangout = arrangeHangout(PaymentState.READY_FOR_PAYOUT, collectedKobo = 500_000L)
        willThrow(PayoutRejectedException("Account is closed"))
            .given(paystackClient).transfer(anyString(), anyLong(), anyString(), anyString())
        payoutService.payHostsWhoAreDue()
        reset(paystackClient)
        stubPaystack()

        change(hangout)
        payoutService.retryPayout(hostId = hangout.hostId, hangoutId = hangout.id!!)
        payoutService.payHostsWhoAreDue()

        then(paystackClient).should().transfer(
            recipientCode = eq(NEW_RECIPIENT),
            amountKobo = eq(500_000L),
            reference = anyString(),
            reason = anyString()
        )
    }

    @Test
    fun `refuses while the money is on its way`() {
        val hangout = arrangeHangout(PaymentState.PAYING_OUT)

        assertThrows<HangoutIllegalStateException> { change(hangout) }

        assertKeptOldAccount(hangout)
        then(paystackClient).should(never()).createTransferRecipient(any())
    }

    @Test
    fun `refuses while a transfer is left in flight`() {
        val hangout = arrangeHangout(PaymentState.READY_FOR_PAYOUT)
        payoutRepository.save(
            PayoutEntity(hangoutId = hangout.id!!, reference = "lynk_payout_stuck", amountKobo = 500_000L)
        )

        assertThrows<PaymentIllegalStateException> { change(hangout) }

        assertKeptOldAccount(hangout)
    }

    @Test
    fun `refuses once the host has been paid`() {
        val hangout = arrangeHangout(PaymentState.PAID_OUT)

        assertThrows<HangoutIllegalStateException> { change(hangout) }
    }

    @Test
    fun `refuses a cancelled hangout`() {
        val hangout = arrangeHangout(PaymentState.CANCELLED)

        assertThrows<HangoutIllegalStateException> { change(hangout) }
    }

    @Test
    fun `refuses anyone but the host`() {
        val hangout = arrangeHangout(PaymentState.COLLECTING)
        val guest = fixtures.user(displayName = "Bola")
        fixtures.participant(hangout = hangout, user = guest)

        assertThrows<HangoutAccessDeniedException> { change(hangout, hostId = guest.userId) }

        assertKeptOldAccount(hangout)
    }

    @Test
    fun `shows the host the account after a change`() {
        val hangout = arrangeHangout(PaymentState.COLLECTING)
        change(hangout)

        val payoutAccount = paymentSetupService.getPayoutAccount(hangout.hostId, hangout.id!!)

        assertEquals("6789", payoutAccount.accountNumberLast4)
        assertEquals("ADA NEW", payoutAccount.accountHolderName)
    }

    @Test
    fun `shows nobody else where the payout goes`() {
        val hangout = arrangeHangout(PaymentState.COLLECTING)
        val guest = fixtures.user(displayName = "Bola")
        fixtures.participant(hangout = hangout, user = guest)

        assertThrows<PaymentAccessDeniedException> {
            paymentSetupService.getPayoutAccount(guest.userId, hangout.id!!)
        }
    }

    @Test
    fun `shows nothing once the details are erased`() {
        val hangout = arrangeHangout(PaymentState.CANCELLED)
        payoutService.erasePayoutAccount(hangout.id!!)

        assertThrows<PaymentIllegalStateException> {
            paymentSetupService.getPayoutAccount(hangout.hostId, hangout.id!!)
        }
    }

    private fun change(hangout: HangoutEntity, hostId: UUID = hangout.hostId) {
        paymentSetupService.changePayoutAccount(
            hostId = hostId,
            hangoutId = hangout.id!!,
            accountNumber = ACCOUNT_NUMBER,
            bankCode = BANK_CODE
        )
    }

    private fun assertKeptOldAccount(hangout: HangoutEntity) {
        assertEquals(OLD_RECIPIENT, hangoutPayoutAccountRepository.findByHangoutId(hangout.id!!)!!.recipientCode)
    }

    private fun arrangeHangout(paymentState: PaymentState, collectedKobo: Long = 0L): HangoutEntity {
        val host = fixtures.user(displayName = "Ada")
        val hangout = fixtures.hangout(
            host = host,
            costPerPersonKobo = 500_000L,
            paymentState = paymentState,
            splitHeadcount = 2
        )
        fixtures.participant(hangout = hangout, user = host)

        if (collectedKobo > 0) {
            val attendee = fixtures.user(displayName = "Attendee")
            fixtures.participant(hangout = hangout, user = attendee, hasPaid = true)
            fixtures.payment(
                hangoutId = hangout.id!!,
                userId = attendee.userId,
                amountKobo = collectedKobo,
                status = PaymentStatus.SUCCESS,
                paidAt = Instant.now()
            )
        }

        fixtures.payoutAccount(hangout = hangout, host = host, recipientCode = OLD_RECIPIENT)

        return hangout
    }
}
