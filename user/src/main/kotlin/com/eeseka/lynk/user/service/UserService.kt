package com.eeseka.lynk.user.service

import com.eeseka.lynk.common.domain.events.user.UserEvent
import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.common.infra.message_queue.EventPublisher
import com.eeseka.lynk.user.domain.events.ProfilePictureReplacedEvent
import com.eeseka.lynk.user.domain.exception.InvalidProfilePictureException
import com.eeseka.lynk.user.domain.exception.UserAlreadyExistsException
import com.eeseka.lynk.user.domain.exception.UserNotFoundException
import com.eeseka.lynk.user.domain.exception.UsernameAlreadySetException
import com.eeseka.lynk.user.domain.model.ProfilePictureUploadCredentials
import com.eeseka.lynk.user.domain.model.User
import com.eeseka.lynk.user.infra.database.mappers.toUser
import com.eeseka.lynk.user.infra.database.repositories.UserRepository
import com.eeseka.lynk.user.infra.storage.SupabaseUserStorageClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserService(
    private val userRepository: UserRepository,
    private val supabaseUserStorageClient: SupabaseUserStorageClient,
    @param:Value("\${supabase.url}") private val supabaseUrl: String,
    private val eventPublisher: EventPublisher,
    private val applicationEventPublisher: ApplicationEventPublisher,
) {

    private val reservedUsernames = setOf("admin", "support", "system", "api", "lynk", "root", "null")

    fun isUsernameAvailable(username: String): Boolean {
        val normalizedUsername = username.trim().lowercase()
        return normalizedUsername !in reservedUsernames && !userRepository.existsByUsername(normalizedUsername)
    }

    fun generateProfilePictureUploadUrl(
        userId: UserId,
        mimeType: String
    ): ProfilePictureUploadCredentials {
        return supabaseUserStorageClient.generateSignedUploadUrl(userId = userId, mimeType = mimeType)
    }

    fun getUser(userId: UserId): User {
        val userEntity = userRepository.findByIdOrNull(userId) ?: throw UserNotFoundException()
        return userEntity.toUser()
    }

    @Transactional
    fun createProfile(
        userId: UserId,
        username: String,
        displayName: String,
        profilePhotoUrl: String?
    ): User {
        val userEntity = userRepository.findByIdOrNull(userId) ?: throw UserNotFoundException()

        if (userEntity.username != null) {
            throw UsernameAlreadySetException()
        }

        val normalizedUsername = username.trim().lowercase()
        val cleanDisplayName = displayName.trim()

        if (normalizedUsername in reservedUsernames) {
            throw UserAlreadyExistsException()
        }
        if (userRepository.existsByUsername(normalizedUsername)) {
            throw UserAlreadyExistsException()
        }

        val oldPhotoUrl = userEntity.profilePhotoUrl
        val isPhotoChanged = oldPhotoUrl != profilePhotoUrl

        if (isPhotoChanged) {
            validateProfilePhotoUrl(profilePhotoUrl)
        }

        val savedUser = userRepository.save(
            userEntity.apply {
                this.username = normalizedUsername
                this.displayName = cleanDisplayName
                this.profilePhotoUrl = profilePhotoUrl
            }
        )

        if (isPhotoChanged) {
            requestOldPhotoDeletion(userId = userId, oldPhotoUrl = oldPhotoUrl)
        }

        // The email is the only field here we did not just write ourselves, and a guest has none.
        val safeEmail = requireNotNull(savedUser.email) { "Cannot complete profile without an email" }

        eventPublisher.publish(
            UserEvent.ProfileCompleted(
                userId = userId,
                email = safeEmail,
                username = normalizedUsername,
                displayName = cleanDisplayName,
                profilePictureUrl = profilePhotoUrl
            )
        )

        return savedUser.toUser()
    }

    @Transactional
    fun updateProfile(
        userId: UserId,
        displayName: String,
        profilePhotoUrl: String?
    ): User {
        val userEntity = userRepository.findByIdOrNull(userId) ?: throw UserNotFoundException()

        val cleanDisplayName = displayName.trim()

        val oldPhotoUrl = userEntity.profilePhotoUrl
        val isDisplayNameChanged = userEntity.displayName != cleanDisplayName
        val isPhotoChanged = oldPhotoUrl != profilePhotoUrl

        if (!isDisplayNameChanged && !isPhotoChanged) {
            return userEntity.toUser()
        }

        if (isPhotoChanged) {
            validateProfilePhotoUrl(profilePhotoUrl)
        }

        val savedUser = userRepository.save(
            userEntity.apply {
                this.displayName = cleanDisplayName
                this.profilePhotoUrl = profilePhotoUrl
            }
        )

        if (isPhotoChanged) {
            requestOldPhotoDeletion(userId = userId, oldPhotoUrl = oldPhotoUrl)
        }

        eventPublisher.publish(
            UserEvent.ProfileUpdated(
                userId = userId,
                displayName = cleanDisplayName,
                profilePictureUrl = profilePhotoUrl
            )
        )

        return savedUser.toUser()
    }

    private fun validateProfilePhotoUrl(profilePhotoUrl: String?) {
        if (profilePhotoUrl != null &&
            profilePhotoUrl.contains("supabase.co") &&
            !profilePhotoUrl.startsWith(supabaseUrl)
        ) {
            throw InvalidProfilePictureException("Invalid profile picture URL.")
        }
    }

    // Handed to a listener that runs after this commits, so the call to Supabase happens with no
    // database connection held and never deletes a file a rolled-back profile still points at.
    // Photos we did not upload (Google's) are not ours to delete.
    private fun requestOldPhotoDeletion(userId: UserId, oldPhotoUrl: String?) {
        if (oldPhotoUrl == null || !oldPhotoUrl.startsWith(supabaseUrl)) {
            return
        }

        applicationEventPublisher.publishEvent(
            ProfilePictureReplacedEvent(userId = userId, oldPhotoUrl = oldPhotoUrl)
        )
    }
}