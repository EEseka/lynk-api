package com.eeseka.lynk.notification.service

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.notification.domain.model.NotificationType
import com.eeseka.lynk.notification.domain.model.PushNotification
import com.eeseka.lynk.notification.infra.push_notification.FirebasePushNotificationClient
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentSkipListMap

@Service
class PushNotificationService(
    private val deviceTokenService: DeviceTokenService,
    private val firebasePushNotificationClient: FirebasePushNotificationClient
) {
    companion object {
        private val RETRY_DELAYS_SECONDS = listOf(30L, 60L, 120L, 300L, 600L)
        private const val MAX_RETRY_AGE_MINUTES = 30L
    }

    private val retryQueue = ConcurrentSkipListMap<Long, MutableList<RetryData>>()

    private val logger = LoggerFactory.getLogger(javaClass)

    fun sendToUsers(
        recipientIds: Collection<UserId>,
        title: String,
        message: String,
        hangoutId: HangoutId,
        type: NotificationType
    ) {
        val recipients = deviceTokenService.findTokensForUsers(recipientIds)
        if (recipients.isEmpty()) {
            logger.info("No device tokens registered for {} recipients", recipientIds.size)
            return
        }

        sendWithRetry(
            PushNotification(
                title = title,
                message = message,
                recipients = recipients,
                hangoutId = hangoutId,
                data = mapOf(
                    "type" to type.name,
                    "hangoutId" to hangoutId.toString()
                )
            )
        )
    }

    fun sendWithRetry(
        notification: PushNotification,
        attempt: Int = 0
    ) {
        val result = firebasePushNotificationClient.sendNotification(notification)

        deviceTokenService.removeTokens(result.permanentFailures.map { it.token })

        if (result.temporaryFailures.isNotEmpty() && attempt < RETRY_DELAYS_SECONDS.size) {
            scheduleRetry(
                notification = notification.copy(recipients = result.temporaryFailures),
                attempt = attempt + 1
            )
        }

        if (result.succeeded.isNotEmpty()) {
            logger.info("Successfully sent notification to {} devices", result.succeeded.size)
        }
    }

    private fun scheduleRetry(
        notification: PushNotification,
        attempt: Int
    ) {
        val delay = RETRY_DELAYS_SECONDS.getOrElse(attempt - 1) { RETRY_DELAYS_SECONDS.last() }
        val executeAtMillis = Instant.now().plusSeconds(delay).toEpochMilli()

        val retryData = RetryData(
            notification = notification,
            attempt = attempt,
            createdAt = Instant.now()
        )

        retryQueue.compute(executeAtMillis) { _, retries ->
            (retries ?: mutableListOf()).apply { add(retryData) }
        }

        logger.info("Scheduled retry {} for {} in {} seconds", attempt, notification.id, delay)
    }

    @Scheduled(fixedDelay = 15_000L)
    fun processRetries() {
        val now = Instant.now()
        val toProcess = retryQueue.headMap(now.toEpochMilli(), true)

        if (toProcess.isEmpty()) return

        toProcess.entries.toList().forEach { (timeMillis, retries) ->
            retryQueue.remove(timeMillis)

            retries.forEach { retry ->
                try {
                    val age = Duration.between(retry.createdAt, now)
                    if (age.toMinutes() > MAX_RETRY_AGE_MINUTES) {
                        logger.warn("Dropping a retry that is {} minutes old", age.toMinutes())
                        return@forEach
                    }

                    sendWithRetry(
                        notification = retry.notification,
                        attempt = retry.attempt
                    )
                } catch (e: Exception) {
                    logger.warn("Error processing retry ${retry.notification.id}", e)
                }
            }
        }
    }

    private data class RetryData(
        val notification: PushNotification,
        val attempt: Int,
        val createdAt: Instant
    )
}