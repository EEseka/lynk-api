package com.eeseka.lynk.payment

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.hangout.api.dto.HangoutDto
import com.eeseka.lynk.hangout.domain.model.PaymentState
import com.eeseka.lynk.hangout.domain.model.RsvpStatus
import com.eeseka.lynk.payment.domain.model.Bank
import com.eeseka.lynk.payment.domain.model.BankAccount
import com.eeseka.lynk.payment.infra.database.repositories.HangoutPayoutAccountRepository
import com.eeseka.lynk.support.IntegrationTest
import com.eeseka.lynk.support.TestAccount
import com.eeseka.lynk.support.authenticatedAs
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.BDDMockito.given
import org.mockito.kotlin.eq
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * The host putting a price on a hangout: the bill is split between whoever is coming, the host's
 * bank account is checked with Paystack, and everyone starts owing their share.
 *
 * This is where the hangout module and the payment module meet, and where the number a guest is
 * later charged is decided.
 */
class EnablePaymentsJourneyTest : IntegrationTest() {

    private companion object {
        const val ACCOUNT_NUMBER = "0123456789"
        const val BANK_CODE = "058"
        const val RECIPIENT_CODE = "RCP_test_recipient"

        val BANK_ACCOUNT = BankAccount(
            accountNumber = ACCOUNT_NUMBER,
            accountName = "Ada Eze",
            bankCode = BANK_CODE
        )
    }

    @Autowired
    private lateinit var payoutAccountRepository: HangoutPayoutAccountRepository

    @BeforeEach
    fun stubPaystackBankLookups() {
        given(paystackClient.getBanks()).willReturn(
            listOf(Bank(name = "Test Bank", code = BANK_CODE, logoUrl = null))
        )
        given(bankLogoClient.getLogoUrlsByBankCode()).willReturn(emptyMap())
        given(paystackClient.resolveAccount(anyString(), anyString())).willReturn(
            BankAccount(accountNumber = ACCOUNT_NUMBER, accountName = "Ada Eze", bankCode = BANK_CODE)
        )
        given(paystackClient.createTransferRecipient(eq(BANK_ACCOUNT))).willReturn(RECIPIENT_CODE)
    }

    @Test
    fun `splits the bill between the people who are coming`() {
        val host = signIn("ada")
        val hangoutId = arrangeHangoutWithGuest(host)

        enablePayments(host, hangoutId, totalCostKobo = 1_000_000L).andExpect {
            status { isOk() }
            // Two people are attending, so each owes half.
            jsonPath("$.costPerPersonKobo") { value(500_000) }
            jsonPath("$.accountNumberLast4") { value(ACCOUNT_NUMBER.takeLast(4)) }
        }

        // Held in a local: a property from another module cannot be smart cast.
        val payment = getHangout(host, hangoutId).payment
        assertNotNull(payment, "the hangout does not show that it now costs money")
        assertEquals(PaymentState.COLLECTING, payment.state)
        assertEquals(500_000L, payment.costPerPersonKobo)
    }

    /**
     * Integer division leaves a remainder, and it stays with the host: every guest's share is the
     * same number, and together they never come to more than the bill.
     */
    @Test
    fun `leaves the odd kobo with the host`() {
        val host = signIn("ada")
        val hangoutId = arrangeHangoutWithGuest(host)

        enablePayments(host, hangoutId, totalCostKobo = 1_000_001L).andExpect {
            status { isOk() }
            jsonPath("$.costPerPersonKobo") { value(500_000) }
        }
    }

    @Test
    fun `records the account the money will be sent to`() {
        val host = signIn("ada")
        val hangoutId = arrangeHangoutWithGuest(host)

        enablePayments(host, hangoutId, totalCostKobo = 1_000_000L).andExpect { status { isOk() } }

        val payoutAccount = payoutAccountRepository.findByHangoutId(hangoutId)
        assertNotNull(payoutAccount)
        assertEquals(RECIPIENT_CODE, payoutAccount.recipientCode)
        assertEquals(host.userId, payoutAccount.hostId)
        assertEquals(ACCOUNT_NUMBER.takeLast(4), payoutAccount.accountNumberLast4)
    }

    @Test
    fun `lets nobody but the host put a price on a hangout`() {
        val host = signIn("ada")
        val friend = signIn("bola")
        val hangoutId = arrangeHangoutWithGuest(host, guest = friend)

        enablePayments(friend, hangoutId, totalCostKobo = 1_000_000L)
            .andExpect { status { isForbidden() } }
    }

    @Test
    fun `refuses a deadline after the hangout itself`() {
        val host = signIn("ada")
        val hangoutId = arrangeHangoutWithGuest(host)

        enablePayments(
            host = host,
            hangoutId = hangoutId,
            totalCostKobo = 1_000_000L,
            deadline = Instant.now().plus(Duration.ofDays(10))
        ).andExpect { status { isBadRequest() } }
    }

    @Test
    fun `refuses to turn payments on twice`() {
        val host = signIn("ada")
        val hangoutId = arrangeHangoutWithGuest(host)
        enablePayments(host, hangoutId, totalCostKobo = 1_000_000L).andExpect { status { isOk() } }

        enablePayments(host, hangoutId, totalCostKobo = 2_000_000L)
            .andExpect { status { isConflict() } }
    }

    /** A hangout with only its host on it has nobody to split a bill with. */
    @Test
    fun `refuses to charge for a hangout nobody else is coming to`() {
        val host = signIn("ada")
        val hangoutId = createScheduledHangout(host)

        enablePayments(host, hangoutId, totalCostKobo = 1_000_000L)
            .andExpect { status { isConflict() } }
    }

    @Test
    fun `refuses a share too small to charge`() {
        val host = signIn("ada")
        val hangoutId = arrangeHangoutWithGuest(host)

        // Split two ways this comes to fifty kobo each, under the one naira Paystack will take.
        enablePayments(host, hangoutId, totalCostKobo = 100L)
            .andExpect { status { isBadRequest() } }
    }

    private fun signIn(username: String): TestAccount = accounts.signIn(
        email = "$username@lynk.test",
        displayName = username.replaceFirstChar { it.uppercase() },
        username = username
    )

    /** A scheduled hangout with the host and one guest attending — the least payments will accept. */
    private fun arrangeHangoutWithGuest(
        host: TestAccount,
        guest: TestAccount = signIn("bola")
    ): HangoutId {
        val hangoutId = createScheduledHangout(host)

        mockMvc.post("/api/hangouts/$hangoutId/participants") {
            contentType = MediaType.APPLICATION_JSON
            authenticatedAs(host)
            content = """{"userId":"${guest.userId}"}"""
        }.andExpect { status { isCreated() } }

        mockMvc.patch("/api/hangouts/$hangoutId/rsvp") {
            contentType = MediaType.APPLICATION_JSON
            authenticatedAs(guest)
            content = """{"rsvpStatus":"${RsvpStatus.ATTENDING.name}"}"""
        }.andExpect { status { isOk() } }

        return hangoutId
    }

    /** Payments need a scheduled hangout, and a hangout is scheduled once it has a spot. */
    private fun createScheduledHangout(host: TestAccount): HangoutId {
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
                  "spotId": "places/test-spot"
                }
            """.trimIndent()
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString

        return objectMapper.readValue(response, HangoutDto::class.java).id
    }

    private fun enablePayments(
        host: TestAccount,
        hangoutId: HangoutId,
        totalCostKobo: Long,
        deadline: Instant = Instant.now().plus(Duration.ofDays(3))
    ): ResultActionsDsl = mockMvc.patch("/api/payments/hangouts/$hangoutId") {
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
    }

    private fun getHangout(caller: TestAccount, hangoutId: HangoutId): HangoutDto {
        val response = mockMvc.get("/api/hangouts/$hangoutId") {
            authenticatedAs(caller)
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString

        return objectMapper.readValue(response, HangoutDto::class.java)
    }
}
