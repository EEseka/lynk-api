package com.eeseka.lynk.payment.infra.paystack

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Proves a webhook really came from Paystack.
 *
 * This is the ONLY authentication on the webhook endpoint - there is no JWT because Paystack has no
 * account with us. If this check is wrong, anyone who guesses the URL can mark themselves paid.
 */
@Component
class PaystackSignatureVerifier(
    @param:Value("\${paystack.secret-key}") private val secretKey: String
) {
    companion object {
        private const val ALGORITHM = "HmacSHA512"
    }

    fun isValid(rawBody: String, signature: String?): Boolean {
        if (signature.isNullOrBlank()) return false

        val mac = Mac.getInstance(ALGORITHM)
        mac.init(SecretKeySpec(secretKey.toByteArray(Charsets.UTF_8), ALGORITHM))

        val expected = mac.doFinal(rawBody.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        return MessageDigest.isEqual(
            expected.toByteArray(Charsets.UTF_8),
            signature.toByteArray(Charsets.UTF_8)
        )
    }
}