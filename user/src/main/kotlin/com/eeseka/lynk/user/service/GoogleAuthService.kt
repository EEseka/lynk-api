package com.eeseka.lynk.user.service

import com.eeseka.lynk.common.domain.exception.InvalidTokenException
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.*

@Service
class GoogleAuthService(
    @Value("\${lynk.auth.google.client-id}")
    private val googleClientId: String
) {
    private val verifier = GoogleIdTokenVerifier
        .Builder(NetHttpTransport(), GsonFactory())
        .setAudience(Collections.singletonList(googleClientId))
        .build()

    fun verify(token: String): GoogleUser {
        try {
            val idToken: GoogleIdToken = verifier.verify(token)
                ?: throw InvalidTokenException("Invalid Google Token")

            val payload = idToken.payload

            if (payload.emailVerified != true) {
                throw InvalidTokenException("Your Google account email is not verified.")
            }

            return GoogleUser(
                email = payload.email,
                displayName = payload["name"] as? String,
                pictureUrl = payload["picture"] as? String
            )
        } catch (_: Exception) {
            throw InvalidTokenException("Failed to verify Google Token")
        }
    }

    data class GoogleUser(
        val email: String,
        val displayName: String?,
        val pictureUrl: String?
    )
}