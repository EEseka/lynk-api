package com.eeseka.lynk.support

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.hangout.api.dto.HangoutDto
import com.eeseka.lynk.hangout.domain.model.RsvpStatus
import com.eeseka.lynk.payment.domain.model.Bank
import com.eeseka.lynk.payment.domain.model.BankAccount
import com.eeseka.lynk.payment.infra.bank_logo.BankLogoClient
import com.eeseka.lynk.payment.infra.paystack.PaystackClient
import org.mockito.BDDMockito.given
import org.mockito.kotlin.any
import org.springframework.boot.test.context.TestComponent
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import java.time.Duration
import java.time.Instant
import tools.jackson.databind.ObjectMapper

/**
 * A hangout with money on it, arranged the long way round.
 *
 * Getting to a payable share takes four requests, and a host who has told us their bank details and
 * a test about paying should not have to spell that out. Everything goes through the real endpoints,
 * so the split, the deadline, and the payout account are the ones the application would have written.
 */
@TestComponent
class TestHangouts(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
    private val accounts: TestAccounts,
    private val paystackClient: PaystackClient,
    private val bankLogoClient: BankLogoClient
) {

    private companion object {
        const val ACCOUNT_NUMBER = "0123456789"
        const val BANK_CODE = "058"
    }

    fun withPaymentsOn(
        totalCostKobo: Long,
        deadline: Instant = Instant.now().plus(Duration.ofDays(2))
    ): PaidHangout {
        val host = accounts.signIn(email = "ada@lynk.test", displayName = "Ada", username = "ada")
        val guest = accounts.signIn(email = "bola@lynk.test", displayName = "Bola", username = "bola")
        val hangout = scheduled(host)

        invite(host, hangout.id, guest)
        accept(guest, hangout.id)
        enablePayments(host, hangout.id, totalCostKobo, deadline)

        return PaidHangout(hangoutId = hangout.id, host = host, guest = guest)
    }

    /** Payments need a scheduled hangout, and a hangout is scheduled once it has a spot. */
    fun scheduled(host: TestAccount, spotId: String? = "ChIJtestspot"): HangoutDto {
        val response = mockMvc.post("/api/hangouts") {
            contentType = MediaType.APPLICATION_JSON
            authenticatedAs(host)
            content = """
                {
                  "name": "Sunday jollof",
                  "description": null,
                  "vibe": "FOOD",
                  "scheduledAt": "${Instant.now().plus(Duration.ofDays(7))}",
                  "maxAttendees": null,
                  "spotId": ${spotId?.let { "\"$it\"" } ?: "null"}
                }
            """.trimIndent()
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString

        return objectMapper.readValue(response, HangoutDto::class.java)
    }

    fun invite(host: TestAccount, hangoutId: HangoutId, invitee: TestAccount) {
        mockMvc.post("/api/hangouts/$hangoutId/participants") {
            contentType = MediaType.APPLICATION_JSON
            authenticatedAs(host)
            content = """{"userId":"${invitee.userId}"}"""
        }.andExpect { status { isCreated() } }
    }

    fun accept(account: TestAccount, hangoutId: HangoutId) {
        mockMvc.patch("/api/hangouts/$hangoutId/rsvp") {
            contentType = MediaType.APPLICATION_JSON
            authenticatedAs(account)
            content = """{"rsvpStatus":"${RsvpStatus.ATTENDING.name}"}"""
        }.andExpect { status { isOk() } }
    }

    private fun enablePayments(
        host: TestAccount,
        hangoutId: HangoutId,
        totalCostKobo: Long,
        deadline: Instant
    ) {
        // Checking the host's account and registering them as somewhere money can be sent are both
        // trips to Paystack, and neither is what a test about paying is asking about.
        given(paystackClient.getBanks()).willReturn(listOf(Bank("Test Bank", BANK_CODE, null)))
        given(bankLogoClient.getLogoUrlsByBankCode()).willReturn(emptyMap())
        given(paystackClient.resolveAccount(any(), any()))
            .willReturn(BankAccount(ACCOUNT_NUMBER, "Ada Eze", BANK_CODE))
        given(paystackClient.createTransferRecipient(any())).willReturn("RCP_test_recipient")

        mockMvc.patch("/api/payments/hangouts/$hangoutId") {
            contentType = MediaType.APPLICATION_JSON
            authenticatedAs(host)
            content = """
                {
                  "totalCostKobo": $totalCostKobo,
                  "paymentDeadline": "$deadline",
                  "accountNumber": "$ACCOUNT_NUMBER",
                  "bankCode": "$BANK_CODE"
                }
            """.trimIndent()
        }.andExpect { status { isOk() } }
    }
}

/** A scheduled hangout, its host, and one guest who is attending and owes a share. */
data class PaidHangout(
    val hangoutId: HangoutId,
    val host: TestAccount,
    val guest: TestAccount
)