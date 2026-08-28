package com.eeseka.lynk.notification.service

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.notification.service.util.toNairaString
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ClassPathResource
import org.springframework.mail.MailException
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service

@Service
class EmailService(
    private val javaMailSender: JavaMailSender,
    private val templateService: EmailTemplateService,
    @param:Value("\${lynk.email.from}")
    private val emailFrom: String,
    @param:Value("\${lynk.email.url}")
    private val baseUrl: String
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val LOGO_CONTENT_ID = "lynkLogo"
        private const val LOGO_PATH = "images/lynk-logo.png"
        /**
         * The custom scheme the client registers, matching the deep link already declared on
         * HangoutsGraph. It needs no domain verification, which a https link would, so it works
         * before there is a landing page to fall back to. Swap it for https at launch.
         */
        private const val HANGOUT_DEEP_LINK = "lynk://hangout_detail"
    }

    fun sendCompleteProfileEmail(email: String, displayName: String?) {
        sendHtmlEmail(
            to = email,
            subject = "Pick your Lynk username",
            templateName = "emails/complete-profile",
            variables = mapOf("displayName" to displayName)
        )
    }

    fun sendWelcomeEmail(email: String, displayName: String, username: String) {
        sendHtmlEmail(
            to = email,
            subject = "Welcome to Lynk",
            templateName = "emails/welcome",
            variables = mapOf(
                "displayName" to displayName,
                "username" to username
            )
        )
    }

    fun sendGoodbyeEmail(email: String, displayName: String?) {
        sendHtmlEmail(
            to = email,
            subject = "Sorry to see you go",
            templateName = "emails/goodbye",
            variables = mapOf("displayName" to displayName)
        )
    }

    fun sendHangoutCancelledEmail(
        email: String,
        hangoutName: String,
        hostDisplayName: String
    ) {
        sendHtmlEmail(
            to = email,
            subject = "\"$hangoutName\" has been cancelled",
            templateName = "emails/hangout-cancelled",
            variables = mapOf(
                "hangoutName" to hangoutName,
                "hostDisplayName" to hostDisplayName
            )
        )
    }

    fun sendRemovedForNonPaymentEmail(
        email: String,
        hangoutName: String,
        hostDisplayName: String
    ) {
        sendHtmlEmail(
            to = email,
            subject = "You have been removed from \"$hangoutName\"",
            templateName = "emails/removed-for-non-payment",
            variables = mapOf(
                "hangoutName" to hangoutName,
                "hostDisplayName" to hostDisplayName
            )
        )
    }

    fun sendPaymentDeadlineResolvedEmail(
        email: String,
        hangoutId: HangoutId,
        hangoutName: String,
        needsDecision: Boolean,
        unpaidCount: Int
    ) {
        sendHtmlEmail(
            to = email,
            subject = if (needsDecision) {
                "\"$hangoutName\" needs a decision from you"
            } else {
                "Everyone has paid for \"$hangoutName\""
            },
            templateName = "emails/payment-deadline-resolved",
            variables = mapOf(
                "hangoutName" to hangoutName,
                "hangoutUrl" to "$HANGOUT_DEEP_LINK/$hangoutId",
                "needsDecision" to needsDecision,
                "unpaidCount" to unpaidCount
            )
        )
    }

    fun sendPayoutOutcomeEmail(
        email: String,
        hangoutId: HangoutId,
        hangoutName: String,
        succeeded: Boolean,
        reference: String?,
        amountKobo: Long
    ) {
        val nothingToSend = succeeded && amountKobo <= 0

        sendHtmlEmail(
            to = email,
            subject = when {
                nothingToSend -> "There was nothing to pay out for \"$hangoutName\""
                succeeded -> "The money from \"$hangoutName\" is on its way"
                else -> "We could not send the money from \"$hangoutName\""
            },
            templateName = "emails/payout-outcome",
            variables = mapOf(
                "hangoutName" to hangoutName,
                "hangoutUrl" to "$HANGOUT_DEEP_LINK/$hangoutId",
                "succeeded" to succeeded,
                "nothingToSend" to nothingToSend,
                "reference" to reference,
                "amount" to amountKobo.toNairaString()
            )
        )
    }

    fun sendRefundIssuedEmail(
        email: String,
        hangoutName: String,
        amountKobo: Long,
        reference: String
    ) {
        sendHtmlEmail(
            to = email,
            subject = "Your refund for \"$hangoutName\" is on its way",
            templateName = "emails/refund-issued",
            variables = mapOf(
                "hangoutName" to hangoutName,
                "amount" to amountKobo.toNairaString(),
                "reference" to reference
            )
        )
    }

    private fun sendHtmlEmail(
        to: String,
        subject: String,
        templateName: String,
        variables: Map<String, Any?>
    ) {
        val html = templateService.processTemplate(
            templateName = templateName,
            variables = variables + ("baseUrl" to baseUrl)
        )

        val message = javaMailSender.createMimeMessage()
        MimeMessageHelper(message, true, "UTF-8").apply {
            setFrom(emailFrom)
            setTo(to)
            setSubject(subject)
            setText(html, true)
            addInline(LOGO_CONTENT_ID, ClassPathResource(LOGO_PATH), "image/png")
        }

        try {
            javaMailSender.send(message)
            logger.info("Sent \"{}\" email", subject)
        } catch (e: MailException) {
            logger.error("Could not send \"$subject\" email", e)
        }
    }
}