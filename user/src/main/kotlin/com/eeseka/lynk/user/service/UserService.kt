package com.eeseka.lynk.user.service

import com.eeseka.lynk.common.domain.events.user.UserEvent
import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.common.infra.message_queue.EventPublisher
import com.eeseka.lynk.user.domain.exception.InvalidProfilePictureException
import com.eeseka.lynk.user.domain.exception.UserAlreadyExistsException
import com.eeseka.lynk.user.domain.exception.UserNotFoundException
import com.eeseka.lynk.user.domain.model.ProfilePictureUploadCredentials
import com.eeseka.lynk.user.domain.model.User
import com.eeseka.lynk.user.infra.database.mappers.toUser
import com.eeseka.lynk.user.infra.database.repositories.UserRepository
import com.eeseka.lynk.user.infra.storage.SupabaseUserStorageService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserService(
    private val userRepository: UserRepository,
    private val supabaseUserStorageService: SupabaseUserStorageService,
    @param:Value("\${supabase.url}") private val supabaseUrl: String,
    private val eventPublisher: EventPublisher,
) {
    private val logger = LoggerFactory.getLogger(UserService::class.java)

    private val reservedUsernames = setOf("admin", "support", "system", "api", "lynk", "root", "null")

    fun isUsernameAvailable(username: String): Boolean {
        val normalizedUsername = username.trim().lowercase()

        if (reservedUsernames.contains(normalizedUsername)) {
            return false
        }

        return !userRepository.existsByUsername(normalizedUsername)
    }

    fun generateProfilePictureUploadUrl(
        userId: UserId,
        mimeType: String
    ): ProfilePictureUploadCredentials {
        return supabaseUserStorageService.generateSignedUploadUrl(userId = userId, mimeType = mimeType)
    }

    @Transactional
    fun updateProfile(
        userId: UserId,
        username: String,
        displayName: String,
        profilePhotoUrl: String?
    ): User {
        val normalizedUsername = username.trim().lowercase()
        val cleanDisplayName = displayName.trim()

        if (reservedUsernames.contains(normalizedUsername)) {
            throw UserAlreadyExistsException() // Or create a custom ReservedUsernameException
        }

        val userEntity = userRepository.findByIdOrNull(userId)
            ?: throw UserNotFoundException()

        // Validate Username availability against the normalized version
        if (userEntity.username != normalizedUsername && userRepository.existsByUsername(normalizedUsername)) {
            throw UserAlreadyExistsException()
        }

        // Validate Supabase URL (Allows Google URLs to pass safely)
        if (profilePhotoUrl != null && profilePhotoUrl.contains("supabase.co") && !profilePhotoUrl.startsWith(
                supabaseUrl
            )
        ) {
            throw InvalidProfilePictureException("Invalid profile picture URL.")
        }

        val oldUsername = userEntity.username
        val oldPhotoUrl = userEntity.profilePhotoUrl

        val savedUser = userRepository.save(
            userEntity.apply {
                this.username = normalizedUsername
                this.displayName = cleanDisplayName
                this.profilePhotoUrl = profilePhotoUrl
            }
        )

        // Safe Cleanup of the old file
        if (oldPhotoUrl != null && oldPhotoUrl != profilePhotoUrl && oldPhotoUrl.startsWith(supabaseUrl)) {
            try {
                supabaseUserStorageService.deleteFile(oldPhotoUrl)
            } catch (e: Exception) {
                logger.warn("Failed to delete old profile picture for user $userId", e)
            }
        }

        // New Profile Setup
        if (oldUsername == null) {
            // This safely guarantees the compiler that these values are NOT null.
            // If an attacker tries to hit this with a guest account, it throws a clean,
            // readable error instead of a catastrophic NullPointerException.
            val safeEmail = requireNotNull(savedUser.email) { "Cannot complete profile without an email" }
            val safeUsername = requireNotNull(savedUser.username) { "Cannot complete profile without a username" }
            val safeDisplayName =
                requireNotNull(savedUser.displayName) { "Cannot complete profile without a display name" }

            eventPublisher.publish(
                UserEvent.ProfileCompleted(
                    userId = savedUser.id!!,
                    email = safeEmail,
                    username = safeUsername,
                    displayName = safeDisplayName
                )
            )
        } else {
            // Existing Profile Update
            eventPublisher.publish(
                UserEvent.ProfileUpdated(
                    userId = savedUser.id!!
                )
            )
        }

        return savedUser.toUser()
    }
}