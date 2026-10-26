package com.eeseka.lynk.assistant.service

import com.eeseka.lynk.assistant.domain.exception.AssistantAnswerInProgressException
import com.eeseka.lynk.assistant.domain.model.AssistantAnswer
import com.eeseka.lynk.assistant.domain.model.AssistantFailureReason
import com.eeseka.lynk.assistant.domain.model.AssistantMessage
import com.eeseka.lynk.assistant.domain.model.AssistantMessageRole
import com.eeseka.lynk.assistant.domain.model.AssistantMessageStatus
import com.eeseka.lynk.assistant.domain.model.AssistantRecommendation
import com.eeseka.lynk.assistant.infra.database.entities.AssistantMessageEntity
import com.eeseka.lynk.assistant.infra.database.entities.AssistantSpotEntity
import com.eeseka.lynk.assistant.infra.database.entities.AssistantUsageEntity
import com.eeseka.lynk.assistant.infra.database.mappers.toAssistantMessage
import com.eeseka.lynk.assistant.infra.database.mappers.toAssistantRecommendation
import com.eeseka.lynk.assistant.infra.database.repositories.AssistantMessageRepository
import com.eeseka.lynk.assistant.infra.database.repositories.AssistantSpotRepository
import com.eeseka.lynk.common.domain.type.AssistantMessageId
import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.spot.domain.model.PriceLevel
import com.eeseka.lynk.spot.domain.model.SpotCategory
import org.springframework.data.domain.PageRequest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class AssistantMessageService(
    private val assistantMessageRepository: AssistantMessageRepository,
    private val assistantSpotRepository: AssistantSpotRepository
) {
    @Transactional
    fun startAnswer(hangoutId: HangoutId, userId: UserId, question: String): AssistantMessageId {
        assistantMessageRepository.save(
            AssistantMessageEntity(
                hangoutId = hangoutId,
                userId = userId,
                role = AssistantMessageRole.USER,
                content = question
            )
        )
        val answer = assistantMessageRepository.saveAndFlush(
            AssistantMessageEntity(
                hangoutId = hangoutId,
                userId = userId,
                role = AssistantMessageRole.ASSISTANT,
                status = AssistantMessageStatus.PENDING
            )
        )
        return answer.id!!
    }

    @Transactional
    fun finishAnswer(
        messageId: AssistantMessageId,
        answer: AssistantAnswer,
        recommendations: List<AssistantRecommendation>
    ) {
        val message = assistantMessageRepository.findByIdOrNull(messageId) ?: return

        assistantMessageRepository.save(
            message.apply {
                status = AssistantMessageStatus.ANSWERED
                content = answer.content
                usage = AssistantUsageEntity(
                    inputTokens = answer.usage.inputTokens,
                    cacheCreationInputTokens = answer.usage.cacheCreationInputTokens,
                    cacheReadInputTokens = answer.usage.cacheReadInputTokens,
                    outputTokens = answer.usage.outputTokens,
                    searchCount = answer.usage.searchCount
                )
                recommendations.forEachIndexed { index, recommendation ->
                    val spot = recommendation.spot
                    spots.add(
                        AssistantSpotEntity(
                            message = this,
                            position = index + 1,
                            reason = recommendation.reason,
                            googlePlaceId = spot.id,
                            name = spot.name,
                            coverPhotoUrl = spot.photoUrls.firstOrNull(),
                            category = spot.category,
                            priceLevel = spot.priceLevel,
                            shortAddress = spot.shortAddress,
                            latitude = spot.latitude,
                            longitude = spot.longitude
                        )
                    )
                }
            }
        )
    }

    @Transactional
    fun failAnswer(messageId: AssistantMessageId, reason: AssistantFailureReason) {
        val message = assistantMessageRepository.findByIdOrNull(messageId) ?: return

        assistantMessageRepository.save(
            message.apply {
                status = AssistantMessageStatus.FAILED
                failureReason = reason
            }
        )
    }

    @Transactional
    fun failAnswersPendingSince(cutoff: Instant): List<UserId> {
        val stuck = assistantMessageRepository.findByStatusAndCreatedAtBefore(AssistantMessageStatus.PENDING, cutoff)
        assistantMessageRepository.saveAll(
            stuck.map {
                it.apply {
                    status = AssistantMessageStatus.FAILED
                    failureReason = AssistantFailureReason.ERROR
                }
            }
        )
        return stuck.map { it.userId }
    }

    fun getMessages(
        hangoutId: HangoutId,
        userId: UserId,
        before: Instant?,
        pageSize: Int
    ): List<AssistantMessage> {
        val messages = assistantMessageRepository.findByHangoutIdAndUserIdAndCreatedAtBefore(
            hangoutId = hangoutId,
            userId = userId,
            before = before ?: Instant.now(),
            pageable = PageRequest.of(0, pageSize)
        )

        val recommendationsByMessageId = assistantSpotRepository
            .findByMessageIdInOrderByPosition(messages.content.map { it.id!! })
            .groupBy { it.message.id }

        return messages.content.map { message ->
            message.toAssistantMessage(
                recommendations = recommendationsByMessageId[message.id].orEmpty().map { it.toAssistantRecommendation() }
            )
        }
    }

    fun getMessageCount(hangoutId: HangoutId, userId: UserId): Long {
        return assistantMessageRepository.countByHangoutIdAndUserId(hangoutId, userId)
    }

    @Transactional
    fun clearChat(hangoutId: HangoutId, userId: UserId) {
        if (assistantMessageRepository.existsByHangoutIdAndUserIdAndStatus(hangoutId, userId, AssistantMessageStatus.PENDING)) {
            throw AssistantAnswerInProgressException()
        }
        assistantMessageRepository.deleteByHangoutIdAndUserId(hangoutId, userId)
    }

    @Transactional
    fun deleteAllForUser(userId: UserId) {
        assistantMessageRepository.deleteByUserId(userId)
    }

    @Transactional
    fun deleteAllForHangouts(hangoutIds: List<HangoutId>) {
        if (hangoutIds.isEmpty()) return
        assistantMessageRepository.deleteByHangoutIdIn(hangoutIds)
    }

    // Cards go stale like saved spots do (the venue moved, or Google expired the cover photo's name)
    @Transactional
    fun refreshSpotSnapshots(
        spotId: String,
        name: String,
        coverPhotoUrl: String?,
        category: SpotCategory,
        priceLevel: PriceLevel?,
        shortAddress: String?,
        latitude: Double,
        longitude: Double
    ) {
        assistantSpotRepository.updateSnapshotData(
            googlePlaceId = spotId,
            name = name,
            coverPhotoUrl = coverPhotoUrl,
            category = category,
            priceLevel = priceLevel,
            shortAddress = shortAddress,
            latitude = latitude,
            longitude = longitude
        )
    }
}
