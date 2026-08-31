package com.eeseka.lynk.payment

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.hangout.domain.model.HangoutStatus
import com.eeseka.lynk.hangout.domain.model.PaymentState
import com.eeseka.lynk.hangout.service.HangoutService
import com.eeseka.lynk.payment.domain.model.DeadlineDecision
import com.eeseka.lynk.support.IntegrationTest
import com.eeseka.lynk.support.PaidHangout
import com.eeseka.lynk.support.TestAccount
import com.eeseka.lynk.support.authenticatedAs
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals

/**
 * The deadline passed with somebody still unpaid, and the host has to say what happens next: give
 * them longer, drop them, go ahead anyway, or call the whole thing off.
 *
 * Every one of these decides what happens to money that has already been collected, and only the
 * host may make it.
 */
class DeadlineDecisionJourneyTest : IntegrationTest() {

    private companion object {
        const val SHARE_KOBO = 500_000L
    }

    @Autowired
    private lateinit var hangoutService: HangoutService

    @Test
    fun `gives everybody longer to pay`() {
        val paid = arrangeHangoutAwaitingDecision()
        val newDeadline = Instant.now().plus(Duration.ofDays(2))

        decide(paid.host, paid.hangoutId, DeadlineDecision.EXTEND, newDeadline)
            .andExpect { status { isNoContent() } }

        assertEquals(PaymentState.COLLECTING, hangoutService.findPaymentState(paid.hangoutId))
    }

    @Test
    fun `will not extend to no particular date`() {
        val paid = arrangeHangoutAwaitingDecision()

        decide(paid.host, paid.hangoutId, DeadlineDecision.EXTEND, newDeadline = null)
            .andExpect { status { isBadRequest() } }

        assertEquals(PaymentState.AWAITING_HOST_DECISION, hangoutService.findPaymentState(paid.hangoutId))
    }

    @Test
    fun `will not extend into the past`() {
        val paid = arrangeHangoutAwaitingDecision()

        decide(
            host = paid.host,
            hangoutId = paid.hangoutId,
            decision = DeadlineDecision.EXTEND,
            newDeadline = Instant.now().minus(Duration.ofDays(1))
        ).andExpect { status { isBadRequest() } }
    }

    @Test
    fun `goes ahead with whoever paid`() {
        val paid = arrangeHangoutAwaitingDecision()

        decide(paid.host, paid.hangoutId, DeadlineDecision.PROCEED_ANYWAY)
            .andExpect { status { isNoContent() } }

        assertEquals(PaymentState.READY_FOR_PAYOUT, hangoutService.findPaymentState(paid.hangoutId))
    }

    /** The guest never paid, so they lose their seat and the hangout goes ahead without them. */
    @Test
    fun `drops the people who never paid`() {
        val paid = arrangeHangoutAwaitingDecision()

        decide(paid.host, paid.hangoutId, DeadlineDecision.REMOVE_NON_PAYERS)
            .andExpect { status { isNoContent() } }

        assertEquals(PaymentState.READY_FOR_PAYOUT, hangoutService.findPaymentState(paid.hangoutId))
        mockMvc.get("/api/hangouts/${paid.hangoutId}") {
            authenticatedAs(paid.guest)
        }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `calls the whole thing off`() {
        val paid = arrangeHangoutAwaitingDecision()

        decide(paid.host, paid.hangoutId, DeadlineDecision.CANCEL)
            .andExpect { status { isNoContent() } }

        assertEquals(HangoutStatus.CANCELLED, hangoutService.findHangoutStatus(paid.hangoutId))
    }

    @Test
    fun `lets nobody but the host decide`() {
        val paid = arrangeHangoutAwaitingDecision()

        decide(paid.guest, paid.hangoutId, DeadlineDecision.PROCEED_ANYWAY)
            .andExpect { status { isForbidden() } }
    }

    @Test
    fun `refuses a decision on a hangout that is still collecting`() {
        val paid = hangouts.withPaymentsOn(totalCostKobo = SHARE_KOBO * 2)

        decide(paid.host, paid.hangoutId, DeadlineDecision.PROCEED_ANYWAY)
            .andExpect { status { isConflict() } }
    }

    @Test
    fun `lets the host move the deadline before it passes`() {
        val paid = hangouts.withPaymentsOn(totalCostKobo = SHARE_KOBO * 2)
        val newDeadline = Instant.now().plus(Duration.ofDays(3))

        mockMvc.patch("/api/payments/hangouts/${paid.hangoutId}/deadline") {
            contentType = MediaType.APPLICATION_JSON
            authenticatedAs(paid.host)
            content = """{"newDeadline":"$newDeadline"}"""
        }.andExpect { status { isNoContent() } }
    }

    /** The deadline cannot outlive the hangout: there would be nothing left to pay for. */
    @Test
    fun `refuses a deadline after the hangout itself`() {
        val paid = hangouts.withPaymentsOn(totalCostKobo = SHARE_KOBO * 2)

        mockMvc.patch("/api/payments/hangouts/${paid.hangoutId}/deadline") {
            contentType = MediaType.APPLICATION_JSON
            authenticatedAs(paid.host)
            content = """{"newDeadline":"${Instant.now().plus(Duration.ofDays(30))}"}"""
        }.andExpect { status { isBadRequest() } }
    }

    /** The state the sweep leaves behind when the deadline passes with somebody still unpaid. */
    private fun arrangeHangoutAwaitingDecision(): PaidHangout {
        val paid = hangouts.withPaymentsOn(totalCostKobo = SHARE_KOBO * 2)
        fixtures.movePaymentDeadline(paid.hangoutId, Instant.now().minus(Duration.ofHours(1)))

        hangoutService.resolvePaymentDeadlines()
        assertEquals(PaymentState.AWAITING_HOST_DECISION, hangoutService.findPaymentState(paid.hangoutId))

        return paid
    }

    private fun decide(
        host: TestAccount,
        hangoutId: HangoutId,
        decision: DeadlineDecision,
        newDeadline: Instant? = null
    ): ResultActionsDsl = mockMvc.post("/api/payments/hangouts/$hangoutId/deadline-decision") {
        contentType = MediaType.APPLICATION_JSON
        authenticatedAs(host)
        content = """
            {
              "decision": "${decision.name}",
              "newDeadline": ${newDeadline?.let { "\"$it\"" } ?: "null"}
            }
        """.trimIndent()
    }
}
