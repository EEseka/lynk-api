package com.eeseka.lynk.notification

import com.eeseka.lynk.notification.service.EmailService
import com.eeseka.lynk.support.IntegrationTest
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.then
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Every email, rendered with the variables the code actually passes it.
 *
 * Thymeleaf expressions are resolved at send time, so a typo in a fragment argument compiles fine,
 * passes every other test, and then breaks in somebody's inbox. Each branch is rendered here for
 * that reason - including the ones that only differ by a boolean.
 */
class EmailRenderTest : IntegrationTest() {

    private companion object {
        const val RECIPIENT = "ada@lynk.test"
        const val HANGOUT_NAME = "Sunday jollof"
        val HANGOUT_ID: UUID = UUID.fromString("11111111-2222-3333-4444-555555555555")
        val UNPROCESSED_THYMELEAF = Regex("""\sth:[a-z]+=|xmlns:th=""")
    }

    @Autowired
    private lateinit var emailService: EmailService

    @Test
    fun `asks a new account to finish signing up`() {
        emailService.sendCompleteProfileEmail(email = RECIPIENT, displayName = "Ada")

        val email = sentEmail()
        assertEquals("Pick your Lynk username", email.subject)
        assertContains(email.body, "Ada")
    }

    /** Google does not always give a name, and the template has a branch for that. */
    @Test
    fun `asks somebody with no name to finish signing up`() {
        emailService.sendCompleteProfileEmail(email = RECIPIENT, displayName = null)

        assertRendered(sentEmail().body)
    }

    @Test
    fun `welcomes a finished account`() {
        emailService.sendWelcomeEmail(email = RECIPIENT, displayName = "Ada", username = "ada")

        val email = sentEmail()
        assertEquals("Welcome to Lynk", email.subject)
        assertContains(email.body, "ada")
    }

    @Test
    fun `says goodbye`() {
        emailService.sendGoodbyeEmail(email = RECIPIENT, displayName = "Ada")

        assertEquals("Sorry to see you go", sentEmail().subject)
    }

    @Test
    fun `says goodbye to somebody with no name`() {
        emailService.sendGoodbyeEmail(email = RECIPIENT, displayName = null)

        assertRendered(sentEmail().body)
    }

    @Test
    fun `tells somebody a hangout was cancelled`() {
        emailService.sendHangoutCancelledEmail(
            email = RECIPIENT,
            hangoutName = HANGOUT_NAME,
            hostDisplayName = "Ada"
        )

        val email = sentEmail()
        assertEquals("\"$HANGOUT_NAME\" has been cancelled", email.subject)
        assertContains(email.body, HANGOUT_NAME)
    }

    @Test
    fun `tells somebody they were removed for not paying`() {
        emailService.sendRemovedForNonPaymentEmail(
            email = RECIPIENT,
            hangoutName = HANGOUT_NAME,
            hostDisplayName = "Ada"
        )

        assertEquals("You have been removed from \"$HANGOUT_NAME\"", sentEmail().subject)
    }

    @Test
    fun `asks the host to decide when somebody has not paid`() {
        emailService.sendPaymentDeadlineResolvedEmail(
            email = RECIPIENT,
            hangoutId = HANGOUT_ID,
            hangoutName = HANGOUT_NAME,
            needsDecision = true,
            unpaidCount = 2
        )

        val email = sentEmail()
        assertEquals("\"$HANGOUT_NAME\" needs a decision from you", email.subject)
        assertContains(email.body, "lynk://hangout_detail/$HANGOUT_ID")
    }

    @Test
    fun `tells the host everybody paid`() {
        emailService.sendPaymentDeadlineResolvedEmail(
            email = RECIPIENT,
            hangoutId = HANGOUT_ID,
            hangoutName = HANGOUT_NAME,
            needsDecision = false,
            unpaidCount = 0
        )

        assertEquals("Everyone has paid for \"$HANGOUT_NAME\"", sentEmail().subject)
    }

    @Test
    fun `tells the host their money is on its way`() {
        emailService.sendPayoutOutcomeEmail(
            email = RECIPIENT,
            hangoutId = HANGOUT_ID,
            hangoutName = HANGOUT_NAME,
            succeeded = true,
            reference = "lynk_payout_1",
            amountKobo = 1_000_000L
        )

        val email = sentEmail()
        assertEquals("The money from \"$HANGOUT_NAME\" is on its way", email.subject)
        assertContains(email.body, "₦10,000")
    }

    @Test
    fun `tells the host there was nothing to send`() {
        emailService.sendPayoutOutcomeEmail(
            email = RECIPIENT,
            hangoutId = HANGOUT_ID,
            hangoutName = HANGOUT_NAME,
            succeeded = true,
            reference = null,
            amountKobo = 0L
        )

        assertEquals("There was nothing to pay out for \"$HANGOUT_NAME\"", sentEmail().subject)
    }

    @Test
    fun `tells the host the money could not be sent`() {
        emailService.sendPayoutOutcomeEmail(
            email = RECIPIENT,
            hangoutId = HANGOUT_ID,
            hangoutName = HANGOUT_NAME,
            succeeded = false,
            reference = "lynk_payout_1",
            amountKobo = 1_000_000L
        )

        assertEquals("We could not send the money from \"$HANGOUT_NAME\"", sentEmail().subject)
    }

    @Test
    fun `tells somebody their refund is on its way`() {
        emailService.sendRefundIssuedEmail(
            email = RECIPIENT,
            hangoutName = HANGOUT_NAME,
            amountKobo = 500_050L,
            reference = "lynk-refund-1"
        )

        val email = sentEmail()
        assertEquals("Your refund for \"$HANGOUT_NAME\" is on its way", email.subject)
        assertContains(email.body, "₦5,000.50")
    }

    private fun sentEmail(): SentEmail {
        val subjectCaptor = argumentCaptor<String>()
        val htmlCaptor = argumentCaptor<String>()
        then(brevoEmailClient).should().sendHtmlEmail(any(), subjectCaptor.capture(), htmlCaptor.capture())

        val body = htmlCaptor.lastValue
        assertRendered(body)

        return SentEmail(subject = subjectCaptor.lastValue, body = body)
    }

    /**
     * A leftover `th:` attribute means Thymeleaf never processed that element. Matching the attribute
     * shape rather than the bare string, because `max-width:` in the stylesheet contains "th:".
     */
    private fun assertRendered(body: String) {
        assertTrue(body.isNotBlank(), "the template rendered to nothing")
        assertFalse(
            UNPROCESSED_THYMELEAF.containsMatchIn(body),
            "the email still has Thymeleaf attributes in it"
        )
        assertFalse(body.contains("${'$'}{"), "the email still has an unresolved expression in it")
    }

    private data class SentEmail(val subject: String, val body: String)
}
