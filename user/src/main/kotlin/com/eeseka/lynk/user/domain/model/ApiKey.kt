package com.eeseka.lynk.user.domain.model

import java.security.SecureRandom
import java.time.Instant
import java.util.Base64

data class ApiKey(
    val key: String,
    val validFrom: Instant,
    val expiresAt: Instant,
) {
    companion object {
        private const val KEY_LENGTH = 20

        fun generateKey(): String {
            val bytes = ByteArray(KEY_LENGTH) { 0 }

            val secureRandom = SecureRandom()
            secureRandom.nextBytes(bytes)

            return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes)
        }
    }
}
