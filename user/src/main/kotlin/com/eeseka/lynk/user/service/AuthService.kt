package com.eeseka.lynk.user.service

import com.eeseka.lynk.common.domain.AccountDeletionGuard
import com.eeseka.lynk.common.domain.events.user.UserEvent
import com.eeseka.lynk.common.domain.exception.InvalidTokenException
import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.common.infra.message_queue.EventPublisher
import com.eeseka.lynk.common.service.JwtService
import com.eeseka.lynk.user.domain.events.ProfilePictureDeletedEvent
import com.eeseka.lynk.user.domain.exception.UserNotFoundException
import com.eeseka.lynk.user.domain.model.AuthenticatedUser
import com.eeseka.lynk.user.domain.model.AuthProvider
import com.eeseka.lynk.user.infra.database.entities.RefreshTokenEntity
import com.eeseka.lynk.user.infra.database.entities.UserEntity
import com.eeseka.lynk.user.infra.database.mappers.toUser
import com.eeseka.lynk.user.infra.database.repositories.RefreshTokenRepository
import com.eeseka.lynk.user.infra.database.repositories.UserRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.repository.findByIdOrNull
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val jwtService: JwtService,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val eventPublisher: EventPublisher,
    private val googleAuthService: GoogleAuthService,
    private val accountDeletionGuards: List<AccountDeletionGuard>,
    private val applicationEventPublisher: ApplicationEventPublisher,
    @param:Value("\${supabase.url}") private val supabaseUrl: String
) {

    @Transactional
    fun googleLogin(token: String): AuthenticatedUser {
        val googleUser = googleAuthService.verify(token)

        val existingUser = userRepository.findByEmail(googleUser.email)

        val user = existingUser ?: run {
            val newUser = userRepository.save(
                UserEntity(
                    email = googleUser.email,
                    displayName = googleUser.displayName,
                    username = null,
                    authProvider = AuthProvider.GOOGLE,
                    profilePhotoUrl = googleUser.pictureUrl
                )
            )

            eventPublisher.publish(
                UserEvent.Created(
                    userId = newUser.id!!,
                    email = googleUser.email,
                    displayName = googleUser.displayName
                )
            )
            newUser
        }

        return generateAuthResponse(user)
    }

    @Transactional
    fun guestLogin(): AuthenticatedUser {
        val guestUser = userRepository.save(
            UserEntity(
                email = null,
                displayName = null,
                username = null,
                authProvider = AuthProvider.GUEST,
                profilePhotoUrl = null
            )
        )

        return generateAuthResponse(guestUser)
    }

    @Transactional
    fun refresh(refreshToken: String): AuthenticatedUser {
        if (!jwtService.validateRefreshToken(refreshToken)) {
            throw InvalidTokenException(message = "Invalid refresh token")
        }

        val userId = jwtService.getUserIdFromToken(refreshToken)
        val user = userRepository.findByIdOrNull(userId)
            ?: throw UserNotFoundException()

        val hashed = hashToken(refreshToken)

        refreshTokenRepository.findByUserIdAndHashedToken(userId, hashed)
            ?: throw InvalidTokenException("Invalid refresh token")

        refreshTokenRepository.deleteByUserIdAndHashedToken(userId, hashed)

        return generateAuthResponse(user)
    }

    @Transactional
    fun logout(refreshToken: String) {
        val userId = jwtService.getUserIdFromToken(refreshToken)
        val hashed = hashToken(refreshToken)
        refreshTokenRepository.deleteByUserIdAndHashedToken(userId, hashed)
    }

    @Transactional
    fun deleteAccount(userId: UserId) {
        val userEntity = userRepository.findByIdOrNull(userId) ?: throw UserNotFoundException()

        // Asked before anything is removed, so a refusal leaves the account exactly as it was.
        // Each module answers for its own data; the throw travels straight back to the caller.
        accountDeletionGuards.forEach { guard ->
            guard.assertAccountCanBeDeleted(userId)
        }

        val isGuest = userEntity.authProvider == AuthProvider.GUEST
        val email = userEntity.email
        val displayName = userEntity.displayName
        val profilePhotoUrl = userEntity.profilePhotoUrl

        refreshTokenRepository.deleteByUserId(userId)
        userRepository.deleteById(userId)

        requestProfilePhotoDeletion(userId, profilePhotoUrl)

        if (isGuest) return

        val safeEmail = requireNotNull(email) { "A ${userEntity.authProvider} account must have an email" }

        eventPublisher.publish(
            event = UserEvent.Deleted(
                userId = userId,
                email = safeEmail,
                displayName = displayName
            )
        )
    }

    private fun requestProfilePhotoDeletion(userId: UserId, photoUrl: String?) {
        if (photoUrl == null || !photoUrl.startsWith(supabaseUrl)) {
            return
        }

        applicationEventPublisher.publishEvent(
            ProfilePictureDeletedEvent(userId = userId, photoUrl = photoUrl)
        )
    }

    @Scheduled(cron = "0 0 4 * * *")
    @Transactional
    fun cleanupStaleGuestAccounts() {
        val thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS)

        userRepository.deleteByAuthProviderAndCreatedAtLessThan(
            authProvider = AuthProvider.GUEST,
            date = thirtyDaysAgo
        )
    }

    @Scheduled(cron = "0 0 5 * * *")
    @Transactional
    fun cleanupExpiredRefreshTokens() {
        refreshTokenRepository.deleteByExpiresAtLessThan(Instant.now())
    }

    private fun generateAuthResponse(userEntity: UserEntity): AuthenticatedUser {
        val userId = userEntity.id!!
        val newAccessToken = jwtService.generateAccessToken(
            userId = userId,
            isGuest = userEntity.authProvider == AuthProvider.GUEST
        )
        val newRefreshToken = jwtService.generateRefreshToken(userId)

        storeRefreshToken(userId, newRefreshToken)

        return AuthenticatedUser(
            user = userEntity.toUser(),
            accessToken = newAccessToken,
            refreshToken = newRefreshToken
        )
    }

    private fun storeRefreshToken(userId: UserId, token: String) {
        val hashed = hashToken(token)
        val expiryMs = jwtService.refreshTokenValidityMs
        val expiresAt = Instant.now().plusMillis(expiryMs)

        refreshTokenRepository.save(
            RefreshTokenEntity(
                userId = userId,
                expiresAt = expiresAt,
                hashedToken = hashed
            )
        )
    }

    private fun hashToken(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(token.encodeToByteArray())
        return Base64.getEncoder().encodeToString(hashBytes)
    }
}