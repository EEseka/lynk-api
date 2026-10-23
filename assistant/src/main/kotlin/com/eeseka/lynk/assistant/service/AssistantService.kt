package com.eeseka.lynk.assistant.service

import ai.koog.prompt.Prompt
import com.eeseka.lynk.assistant.domain.exception.AssistantAnswerInProgressException
import com.eeseka.lynk.assistant.domain.exception.AssistantDisabledException
import com.eeseka.lynk.assistant.domain.exception.AssistantIllegalArgumentException
import com.eeseka.lynk.assistant.domain.exception.AssistantNotAttendingException
import com.eeseka.lynk.assistant.domain.exception.AssistantRefusedException
import com.eeseka.lynk.assistant.domain.exception.AssistantVotingClosedException
import com.eeseka.lynk.assistant.domain.exception.DiscoverChatExpiredException
import com.eeseka.lynk.assistant.domain.model.AssistantAnswer
import com.eeseka.lynk.assistant.domain.model.AssistantFailureReason
import com.eeseka.lynk.assistant.domain.model.AssistantMessage
import com.eeseka.lynk.assistant.domain.model.AssistantMessageRole
import com.eeseka.lynk.assistant.domain.model.AssistantStreamEvent
import com.eeseka.lynk.assistant.domain.model.DiscoverChat
import com.eeseka.lynk.assistant.domain.model.DiscoverChatMessage
import com.eeseka.lynk.assistant.infra.redis.DiscoverChatRepository
import com.eeseka.lynk.assistant.service.agent.AssistantAgent
import com.eeseka.lynk.assistant.service.agent.AssistantPrompts
import com.eeseka.lynk.assistant.service.agent.AssistantTurn
import com.eeseka.lynk.common.domain.model.UserRateLimitUsage
import com.eeseka.lynk.common.domain.type.DiscoverChatId
import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.common.infra.rate_limiting.UserRateLimiter
import com.eeseka.lynk.hangout.domain.model.RsvpStatus
import com.eeseka.lynk.hangout.service.HangoutParticipantService
import com.eeseka.lynk.hangout.service.HangoutService
import com.eeseka.lynk.lobby.service.LobbyVotingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.DateTimeException
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

@Service
class AssistantService(
    private val assistantMessageService: AssistantMessageService,
    private val assistantAgent: AssistantAgent,
    private val discoverChatRepository: DiscoverChatRepository,
    private val hangoutService: HangoutService,
    private val hangoutParticipantService: HangoutParticipantService,
    private val lobbyVotingService: LobbyVotingService,
    private val userRateLimiter: UserRateLimiter,
    @param:Value("\${assistant.disabled}") private val isDisabled: Boolean
) {
    companion object {
        const val MESSAGES_PER_DAY = 20

        // Must match the key UserRateLimitInterceptor builds from the controller's class and method names
        const val SEND_MESSAGE_ROUTE = "AssistantController.sendMessage"

        private val ANSWER_TIMEOUT = 60.seconds
        private val STUCK_ANSWER_AGE = Duration.ofMinutes(2)

        private const val HISTORY_MESSAGES = 20
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    // Answers outlive the request that asked for them, so a phone that drops off still finds its answer saved
    private val answerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun startAnswer(
        userId: UserId,
        hangoutId: HangoutId?,
        discoverChatId: DiscoverChatId?,
        latitude: Double?,
        longitude: Double?,
        timeZone: String?,
        question: String,
        onEvent: (AssistantStreamEvent) -> Unit,
        onFinished: () -> Unit
    ) {
        try {
            if (isDisabled) throw AssistantDisabledException()
            val zoneId = zoneIdOf(timeZone)

            if (hangoutId != null) {
                startHangoutAnswer(
                    userId = userId,
                    hangoutId = hangoutId,
                    zoneId = zoneId,
                    question = question,
                    onEvent = onEvent,
                    onFinished = onFinished
                )
            } else {
                startDiscoverAnswer(
                    userId = userId,
                    discoverChatId = discoverChatId,
                    latitude = latitude,
                    longitude = longitude,
                    zoneId = zoneId,
                    question = question,
                    onEvent = onEvent,
                    onFinished = onFinished
                )
            }
        } catch (e: Exception) {
            userRateLimiter.refund(userId, SEND_MESSAGE_ROUTE)
            throw e
        }
    }

    fun getMessages(userId: UserId, hangoutId: HangoutId, before: Instant?, pageSize: Int): List<AssistantMessage> {
        return assistantMessageService.getMessages(hangoutId, userId, before, pageSize)
    }

    fun getMessageCount(userId: UserId, hangoutId: HangoutId): Long {
        return assistantMessageService.getMessageCount(hangoutId, userId)
    }

    fun clearChat(userId: UserId, hangoutId: HangoutId) {
        assistantMessageService.clearChat(hangoutId, userId)
    }

    fun getUsage(userId: UserId): UserRateLimitUsage {
        return userRateLimiter.findUsage(userId, SEND_MESSAGE_ROUTE)
    }

    @Scheduled(fixedDelay = 60 * 1000)
    fun failStuckAnswers() {
        val userIds = assistantMessageService.failAnswersPendingSince(Instant.now().minus(STUCK_ANSWER_AGE))
        if (userIds.isEmpty()) return

        userIds.forEach { userRateLimiter.refund(it, SEND_MESSAGE_ROUTE) }
        logger.info("Failed and refunded {} assistant answers left unfinished", userIds.size)
    }

    private fun startHangoutAnswer(
        userId: UserId,
        hangoutId: HangoutId,
        zoneId: ZoneId,
        question: String,
        onEvent: (AssistantStreamEvent) -> Unit,
        onFinished: () -> Unit
    ) {
        if (hangoutParticipantService.findRsvpStatus(hangoutId, userId) != RsvpStatus.ATTENDING) {
            throw AssistantNotAttendingException()
        }
        if (!hangoutService.isVotingOpen(hangoutId)) throw AssistantVotingClosedException()

        val hangout = hangoutService.getHangout(userId, hangoutId)
        val votingSnapshot = lobbyVotingService.getVotingSnapshot(hangoutId)
        val history = assistantMessageService.getMessages(hangoutId, userId, before = null, pageSize = HISTORY_MESSAGES)

        val prompt = AssistantPrompts.hangoutPrompt(
            hangout = hangout,
            ballot = votingSnapshot.candidates,
            hasGroupCenter = votingSnapshot.centerLatitude != null,
            zoneId = zoneId,
            history = history,
            question = question
        )
        val turn = AssistantTurn(
            userId = userId,
            searchLatitude = votingSnapshot.centerLatitude,
            searchLongitude = votingSnapshot.centerLongitude,
            hangoutTime = hangout.scheduledAt,
            ballotSpotIds = votingSnapshot.candidates.map { it.id },
            earlierRecommendedSpotIds = history.flatMap { message -> message.recommendations.map { it.spot.id } }.distinct()
        )

        val messageId = try {
            assistantMessageService.startAnswer(hangoutId, userId, question)
        } catch (_: DataIntegrityViolationException) {
            throw AssistantAnswerInProgressException()
        }

        onEvent(AssistantStreamEvent.Started(messageId = messageId, discoverChatId = null))
        launchAnswer(
            prompt = prompt,
            turn = turn,
            onEvent = onEvent,
            onFinished = onFinished,
            saveAnswer = { answer ->
                assistantMessageService.finishAnswer(messageId, answer, turn.recommendations, turn.searchCount)
            },
            saveFailure = { reason -> assistantMessageService.failAnswer(messageId, reason) },
            release = {}
        )
    }

    private fun startDiscoverAnswer(
        userId: UserId,
        discoverChatId: DiscoverChatId?,
        latitude: Double?,
        longitude: Double?,
        zoneId: ZoneId,
        question: String,
        onEvent: (AssistantStreamEvent) -> Unit,
        onFinished: () -> Unit
    ) {
        if (latitude == null || longitude == null) {
            throw AssistantIllegalArgumentException("A Discover question needs the area being looked at.")
        }

        if (!discoverChatRepository.tryStartAnswering(userId)) throw AssistantAnswerInProgressException()

        val chat = try {
            if (discoverChatId == null) {
                DiscoverChat(id = UUID.randomUUID(), messages = emptyList())
            } else {
                discoverChatRepository.find(userId, discoverChatId) ?: throw DiscoverChatExpiredException()
            }
        } catch (e: Exception) {
            discoverChatRepository.finishAnswering(userId)
            throw e
        }

        val prompt = AssistantPrompts.discoverPrompt(zoneId = zoneId, history = chat.messages, question = question)
        val turn = AssistantTurn(
            userId = userId,
            searchLatitude = latitude,
            searchLongitude = longitude,
            hangoutTime = null,
            ballotSpotIds = emptyList(),
            earlierRecommendedSpotIds = chat.messages.flatMap { it.spotIds }.distinct()
        )

        onEvent(AssistantStreamEvent.Started(messageId = null, discoverChatId = chat.id))
        launchAnswer(
            prompt = prompt,
            turn = turn,
            onEvent = onEvent,
            onFinished = onFinished,
            saveAnswer = { answer ->
                val messages = chat.messages +
                        DiscoverChatMessage(role = AssistantMessageRole.USER, content = question, spotIds = emptyList()) +
                        DiscoverChatMessage(
                            role = AssistantMessageRole.ASSISTANT,
                            content = answer.content,
                            spotIds = turn.recommendations.map { it.spot.id }
                        )
                discoverChatRepository.save(userId, chat.copy(messages = messages.takeLast(HISTORY_MESSAGES)))
            },
            saveFailure = {},
            release = { discoverChatRepository.finishAnswering(userId) }
        )
    }

    private fun launchAnswer(
        prompt: Prompt,
        turn: AssistantTurn,
        onEvent: (AssistantStreamEvent) -> Unit,
        onFinished: () -> Unit,
        saveAnswer: (AssistantAnswer) -> Unit,
        saveFailure: (AssistantFailureReason) -> Unit,
        release: () -> Unit
    ) {
        answerScope.launch {
            fun fail(reason: AssistantFailureReason) {
                try {
                    saveFailure(reason)
                } catch (e: Exception) {
                    logger.error("Could not save a failed assistant answer for user {}", turn.userId, e)
                }
                userRateLimiter.refund(turn.userId, SEND_MESSAGE_ROUTE)
                onEvent(AssistantStreamEvent.Error(reason))
            }

            try {
                val answer = withTimeout(ANSWER_TIMEOUT) { assistantAgent.answer(prompt, turn, onEvent) }
                saveAnswer(answer)
                if (turn.recommendations.isNotEmpty()) {
                    onEvent(AssistantStreamEvent.Spots(turn.recommendations.toList()))
                }
                onEvent(AssistantStreamEvent.Done)
            } catch (_: TimeoutCancellationException) {
                fail(AssistantFailureReason.TIMEOUT)
            } catch (_: AssistantRefusedException) {
                fail(AssistantFailureReason.REFUSED)
            } catch (e: Exception) {
                logger.error("Assistant answer failed for user {}", turn.userId, e)
                fail(AssistantFailureReason.ERROR)
            } finally {
                release()
                onFinished()
            }
        }
    }

    // A phone that sends no zone, or one Java doesn't know, gets UTC rather than a failed question
    private fun zoneIdOf(timeZone: String?): ZoneId {
        if (timeZone.isNullOrBlank()) return ZoneOffset.UTC
        return try {
            ZoneId.of(timeZone)
        } catch (_: DateTimeException) {
            ZoneOffset.UTC
        }
    }
}
