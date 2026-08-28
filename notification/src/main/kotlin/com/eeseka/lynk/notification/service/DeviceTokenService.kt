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
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

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

        val entity = if (existing != null) {
            deviceTokenRepository.save(
                existing.apply {
                    this.userId = userId
                    this.platform = platform
                }
            )
        } else {
            deviceTokenRepository.save(
                DeviceTokenEntity(
                    userId = userId,
                    token = trimmedToken,
                    platform = platform
                )
            )
        }

        return entity.toDeviceToken()
    }

    @Transactional
    fun unregisterDevice(token: String) {
        deviceTokenRepository.deleteByToken(token.trim())
    }

    @Transactional
    fun unregisterAllDevices(userId: UserId) {
        deviceTokenRepository.deleteByUserId(userId)
    }

    fun findTokensForUsers(userIds: Collection<UserId>): List<DeviceToken> =
        deviceTokenRepository.findByUserIdIn(userIds).map { it.toDeviceToken() }

    @Transactional
    fun removeTokens(tokens: Collection<String>) {
        if (tokens.isEmpty()) return

        deviceTokenRepository.deleteByTokenIn(tokens)
        logger.info("Removed {} device tokens Firebase rejected outright", tokens.size)
    }
}
