package com.eeseka.lynk.notification.service

import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.notification.domain.exception.InvalidDeviceTokenException
import com.eeseka.lynk.notification.domain.model.DeviceToken
import com.eeseka.lynk.notification.domain.model.Platform
import com.eeseka.lynk.notification.infra.database.entities.DeviceTokenEntity
import com.eeseka.lynk.notification.infra.database.mappers.toDeviceToken
import com.eeseka.lynk.notification.infra.database.repositories.DeviceTokenRepository
import com.eeseka.lynk.notification.infra.push_notification.FirebasePushNotificationClient
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class DeviceTokenService(
    private val deviceTokenRepository: DeviceTokenRepository,
    private val firebasePushNotificationClient: FirebasePushNotificationClient
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun registerDevice(
        userId: UserId,
        token: String,
        platform: Platform
    ): DeviceToken {
        val trimmedToken = token.trim()
        val existing = deviceTokenRepository.findByToken(trimmedToken)

        if (existing == null && !firebasePushNotificationClient.isValidToken(trimmedToken)) {
            throw InvalidDeviceTokenException()
        }

        val updatedRows = deviceTokenRepository.updateOwnerByToken(trimmedToken, userId, platform)
        if (updatedRows == 0) {
            try {
                deviceTokenRepository.save(
                    DeviceTokenEntity(
                        userId = userId,
                        token = trimmedToken,
                        platform = platform
                    )
                )
            } catch (_: DataIntegrityViolationException) {
                // Two registrations of a new token raced and the unique key on token let only one insert
                // through, so this one takes over the row the other just wrote
                deviceTokenRepository.updateOwnerByToken(trimmedToken, userId, platform)
            }
        }

        return DeviceToken(
            userId = userId,
            token = trimmedToken,
            platform = platform,
            createdAt = existing?.createdAt ?: Instant.now()
        )
    }

    @Transactional
    fun unregisterDevice(userId: UserId, token: String) {
        deviceTokenRepository.deleteByUserIdAndToken(userId, token.trim())
    }

    @Transactional
    fun unregisterAllDevices(userId: UserId) {
        deviceTokenRepository.deleteByUserId(userId)
    }

    fun findTokensForUsers(userIds: Set<UserId>): List<DeviceToken> =
        deviceTokenRepository.findByUserIdIn(userIds).map { it.toDeviceToken() }

    @Transactional
    fun removeTokens(tokens: List<String>) {
        if (tokens.isEmpty()) return

        deviceTokenRepository.deleteByTokenIn(tokens)
        logger.info("Removed {} device tokens Firebase rejected outright", tokens.size)
    }
}
