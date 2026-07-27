package com.eeseka.lynk.hangout.service

import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.hangout.domain.exception.HangoutUserNotFoundException
import com.eeseka.lynk.hangout.domain.model.HangoutUser
import com.eeseka.lynk.hangout.infra.database.mappers.toHangoutUser
import com.eeseka.lynk.hangout.infra.database.mappers.toHangoutUserEntity
import com.eeseka.lynk.hangout.infra.database.repositories.HangoutUserRepository
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

@Service
class HangoutUserService(
    private val hangoutUserRepository: HangoutUserRepository
) {
    private val logger = LoggerFactory.getLogger(HangoutUserService::class.java)

    fun createHangoutUser(hangoutUser: HangoutUser) {
        hangoutUserRepository.save(hangoutUser.toHangoutUserEntity())
    }

    fun updateHangoutUser(
        userId: UserId,
        displayName: String,
        profilePictureUrl: String?
    ) {
        val hangoutUserEntity = hangoutUserRepository.findByIdOrNull(userId) ?: run {
            logger.warn("ProfileUpdated event received for unknown HangoutUser $userId, skipping!")
            return
        }
        hangoutUserRepository.save(
            hangoutUserEntity.apply {
                this.displayName = displayName
                this.profilePictureUrl = profilePictureUrl
            }
        )
    }

    fun deleteHangoutUser(userId: UserId) {
        hangoutUserRepository.deleteById(userId)
    }

    fun getHangoutUserById(userId: UserId): HangoutUser {
        return hangoutUserRepository.findByIdOrNull(userId)?.toHangoutUser()
            ?: throw HangoutUserNotFoundException(userId.toString())
    }

    fun getHangoutUserByUsername(username: String): HangoutUser {
        val normalizedUsername = username.trim().lowercase()
        return hangoutUserRepository.findByUsername(normalizedUsername)?.toHangoutUser()
            ?: throw HangoutUserNotFoundException("@$normalizedUsername")
    }
}