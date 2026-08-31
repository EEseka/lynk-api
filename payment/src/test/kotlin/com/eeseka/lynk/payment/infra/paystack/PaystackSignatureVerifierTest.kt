package com.eeseka.lynk.payment.infra.paystack

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PaystackSignatureVerifierTest {

    private companion object {
        const val SECRET = "test-paystack-secret"

        const val BODY = """{"event":"charge.success","data":{"reference":"lynk-ref-1"}}"""

        const val SIGNATURE =
            "3c11a48ae57e5cab6b02d24ac078810e57ca4271d6de8814775dc0e86540e0a1" +
                    "8d493dbd5e748ce9537564869fbaf832db630e9ddc8bdb10d3f90cbfa73123d5"
    }

    private val verifier = PaystackSignatureVerifier(SECRET)

    @Test
    fun `accepts a body signed with the secret`() {
        assertTrue(verifier.isValid(BODY, SIGNATURE))
    }

    @Test
    fun `rejects a body that changed after it was signed`() {
        val tampered = BODY.replace("lynk-ref-1", "lynk-ref-2")

        assertFalse(verifier.isValid(tampered, SIGNATURE))
    }

    @Test
    fun `rejects a signature made with a different secret`() {
        val otherVerifier = PaystackSignatureVerifier("not-the-paystack-secret")

        assertFalse(otherVerifier.isValid(BODY, SIGNATURE))
    }

    @ParameterizedTest(name = "rejects the signature header [{0}]")
    @NullSource
    @ValueSource(strings = ["", "   ", "not-hex-at-all", SIGNATURE + "00"])
    fun `rejects a missing or malformed signature`(signature: String?) {
        assertFalse(verifier.isValid(BODY, signature))
    }

    @Test
    fun `rejects the right digest in the wrong case`() {
        assertFalse(verifier.isValid(BODY, SIGNATURE.uppercase()))
    }
}
