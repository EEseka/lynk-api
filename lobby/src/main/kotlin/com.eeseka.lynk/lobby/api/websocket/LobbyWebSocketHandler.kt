package com.eeseka.lynk.lobby.api.websocket

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.hangout.domain.event.*
import com.eeseka.lynk.hangout.domain.model.RsvpStatus
import com.eeseka.lynk.hangout.service.HangoutParticipantService
import com.eeseka.lynk.hangout.service.HangoutService
import com.eeseka.lynk.lobby.api.dto.ws.IncomingWebSocketMessage
import com.eeseka.lynk.lobby.api.dto.ws.IncomingWebSocketMessageType
import com.eeseka.lynk.lobby.api.dto.ws.OutgoingWebSocketMessage
import com.eeseka.lynk.lobby.api.dto.ws.OutgoingWebSocketMessageType
import com.eeseka.lynk.lobby.api.dto.ws.inbound.*
import com.eeseka.lynk.lobby.api.dto.ws.outbound.*
import com.eeseka.lynk.lobby.api.websocket.LobbyHandshakeInterceptor.Companion.USER_ID_ATTRIBUTE
import com.eeseka.lynk.spot.api.mappers.toSpotDto
import com.eeseka.lynk.spot.domain.model.Spot
import com.eeseka.lynk.spot.service.SpotService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import org.springframework.web.socket.*
import org.springframework.web.socket.handler.TextWebSocketHandler
import tools.jackson.core.JacksonException
import tools.jackson.databind.ObjectMapper
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

@Component
class LobbyWebSocketHandler(
    private val hangoutParticipantService: HangoutParticipantService,
    private val spotService: SpotService,
    private val hangoutService: HangoutService,
    private val objectMapper: ObjectMapper
) : TextWebSocketHandler() {

    companion object {
        private const val PING_INTERVAL_MS = 30_000L
        private const val PONG_TIMEOUT_MS = 60_000L
        private const val MAX_CANDIDATES = 10
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    private val connectionLock = ReentrantReadWriteLock()

    private val sessions = ConcurrentHashMap<String, UserSession>()
    private val userToSessions = ConcurrentHashMap<UserId, MutableSet<String>>()
    private val userHangoutIds = ConcurrentHashMap<UserId, MutableSet<HangoutId>>()
    private val hangoutToSessions = ConcurrentHashMap<HangoutId, MutableSet<String>>()

    private val sessionViewing = ConcurrentHashMap<String, HangoutId>()
    private val hangoutViewers = ConcurrentHashMap<HangoutId, MutableSet<String>>()

    private val hangoutCandidates = ConcurrentHashMap<HangoutId, LinkedHashMap<String, Spot>>()
    private val hangoutVotes = ConcurrentHashMap<HangoutId, MutableMap<UserId, String>>()
    private val hangoutLocations = ConcurrentHashMap<HangoutId, MutableMap<UserId, Coordinates>>()

    override fun afterConnectionEstablished(session: WebSocketSession) {

        val userId = session.attributes[USER_ID_ATTRIBUTE] as? UserId ?: run {
            logger.error("Session ${session.id} opened with no user on it")
            session.close(CloseStatus.SERVER_ERROR.withReason("Authentication failed"))
            return
        }

        val userSession = UserSession(userId = userId, session = session)

        connectionLock.write {
            sessions[session.id] = userSession

            userToSessions.compute(userId) { _, existingSessions ->
                (existingSessions ?: mutableSetOf()).apply { add(session.id) }
            }

            // If first connect for this user: load their hangouts from the DB.
            // Already connected on another device: reuse what we loaded then.
            val hangoutIds = userHangoutIds.computeIfAbsent(userId) {
                val ids = hangoutParticipantService.findAttendingHangoutIds(userId)
                ConcurrentHashMap.newKeySet<HangoutId>().apply { addAll(ids) }
            }

            hangoutIds.forEach { hangoutId ->
                hangoutToSessions.compute(hangoutId) { _, sessions ->
                    (sessions ?: mutableSetOf()).apply { add(session.id) }
                }
            }
        }

        logger.info("Websocket connection established for user $userId")
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        // If this socket was viewing a lobby, its dot must go out.
        var viewingHangout: HangoutId? = null

        connectionLock.write {
            sessions.remove(session.id)?.let { userSession ->
                val userId = userSession.userId

                viewingHangout = sessionViewing.remove(session.id)
                viewingHangout?.let { hangoutId ->
                    hangoutViewers.compute(hangoutId) { _, viewers ->
                        viewers?.apply { remove(session.id) }?.takeIf { it.isNotEmpty() }
                    }
                }

                // Drop this socket from the user's set; drop the whole entry if it was their last one
                val remainingSessions = userToSessions.compute(userId) { _, sessions ->
                    sessions
                        ?.apply { remove(session.id) }
                        ?.takeIf { it.isNotEmpty() }
                }

                // Unsubscribe this socket from every hangout it was in.
                userHangoutIds[userId]?.forEach { hangoutId ->
                    hangoutToSessions.compute(hangoutId) { _, sessions ->
                        sessions
                            ?.apply { remove(session.id) }
                            ?.takeIf { it.isNotEmpty() }
                    }
                }

                if (remainingSessions == null) {
                    userHangoutIds.remove(userId)
                }

                logger.info("Websocket connection closed for user $userId")
            }
        }

        // Their dot goes out for whichever lobby they were viewing.
        viewingHangout?.let { broadcastPresence(it) }
    }

    override fun handleTransportError(session: WebSocketSession, exception: Throwable) {
        logger.error("Transport error on lobby session ${session.id}", exception)
        session.close(CloseStatus.SERVER_ERROR.withReason("Transport error"))
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        // We only act on messages from a socket we know; ignore anything else.
        val userSession = connectionLock.read { sessions[session.id] } ?: return

        try {
            val incomingMessage = objectMapper.readValue(
                message.payload,
                IncomingWebSocketMessage::class.java
            )
            when (incomingMessage.type) {
                IncomingWebSocketMessageType.ENTER_LOBBY -> {
                    val dto = objectMapper.readValue(
                        incomingMessage.payload,
                        LobbyFocusDto::class.java
                    )
                    handleEnterLobby(userSession, dto.hangoutId)
                }

                IncomingWebSocketMessageType.LEAVE_LOBBY -> {
                    val dto = objectMapper.readValue(
                        incomingMessage.payload,
                        LobbyFocusDto::class.java
                    )
                    handleLeaveLobby(userSession, dto.hangoutId)
                }

                IncomingWebSocketMessageType.PROPOSE_SPOT -> {
                    val dto = objectMapper.readValue(
                        incomingMessage.payload,
                        ProposeSpotDto::class.java
                    )
                    handleProposeSpot(userSession, dto.hangoutId, dto.spotId)
                }

                IncomingWebSocketMessageType.REMOVE_SPOT -> {
                    val dto = objectMapper.readValue(
                        incomingMessage.payload,
                        RemoveSpotDto::class.java
                    )
                    handleRemoveSpot(userSession, dto.hangoutId, dto.spotId)
                }

                IncomingWebSocketMessageType.CAST_VOTE -> {
                    val dto = objectMapper.readValue(
                        incomingMessage.payload,
                        CastVoteDto::class.java
                    )
                    handleCastVote(userSession, dto.hangoutId, dto.spotId)
                }

                IncomingWebSocketMessageType.LOCATION_SHARE -> {
                    val dto = objectMapper.readValue(
                        incomingMessage.payload,
                        LocationShareDto::class.java
                    )
                    handleLocationShare(userSession, dto.hangoutId, dto.latitude, dto.longitude)
                }

                IncomingWebSocketMessageType.CLOSE_VOTING -> {
                    val dto = objectMapper.readValue(
                        incomingMessage.payload,
                        CloseVotingDto::class.java
                    )
                    handleCloseVoting(userSession, dto.hangoutId, dto.chosenSpotId)
                }
            }
        } catch (e: JacksonException) {
            logger.warn("Could not parse inbound message: ${message.payload}", e)
            sendError(
                session = userSession.session,
                error = ErrorDto(
                    code = "INVALID_JSON",
                    message = "Incoming JSON or UUID is invalid"
                )
            )
        } catch (e: Exception) {
            // One failed action must not kill the socket: it carries every hangout this user is in.
            logger.error("Failed handling lobby message from user ${userSession.userId}", e)
            sendError(
                session = userSession.session,
                error = ErrorDto(
                    code = "SERVER_ERROR",
                    message = "Something went wrong; please try again"
                )
            )
        }
    }

    override fun handlePongMessage(session: WebSocketSession, message: PongMessage) {
        connectionLock.write {
            sessions.compute(session.id) { _, userSession ->
                userSession?.copy(lastPongTimestamp = System.currentTimeMillis())
            }
        }
    }

    @Scheduled(fixedDelay = PING_INTERVAL_MS)
    fun pingClients() {
        val now = System.currentTimeMillis()
        val sessionsToClose = mutableListOf<String>()

        val snapshot = connectionLock.read { sessions.toMap() }

        snapshot.forEach { (sessionId, userSession) ->
            try {
                if (userSession.session.isOpen) {
                    if (now - userSession.lastPongTimestamp > PONG_TIMEOUT_MS) {
                        sessionsToClose.add(sessionId)
                        return@forEach
                    }
                    userSession.session.sendMessage(PingMessage())
                }
            } catch (e: Exception) {
                logger.error("Could not ping session $sessionId", e)
                sessionsToClose.add(sessionId)
            }
        }

        sessionsToClose.forEach { sessionId ->
            connectionLock.read { sessions[sessionId]?.session }?.let { session ->
                try {
                    session.close(CloseStatus.GOING_AWAY.withReason("Ping timeout"))
                } catch (e: Exception) {
                    logger.error("Could not close timed-out session $sessionId", e)
                }
            }
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onHangoutCreated(event: HangoutCreatedEvent) {
        subscribeUserToHangout(event.hostId, event.hangoutId)
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onParticipantInvited(event: HangoutParticipantInvitedEvent) {
        broadcastToHangout(
            hangoutId = event.hangoutId,
            message = OutgoingWebSocketMessage(
                type = OutgoingWebSocketMessageType.PARTICIPANT_INVITED,
                payload = objectMapper.writeValueAsString(
                    LobbyParticipantDto(
                        hangoutId = event.hangoutId,
                        userId = event.userId,
                        displayName = event.displayName
                    )
                )
            )
        )
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onRsvpUpdated(event: HangoutRsvpUpdatedEvent) {
        // A fresh ATTENDING means a brand-new attendee: wire their live sockets into
        // this lobby BEFORE broadcasting, so they receive the roster update too.
        if (event.rsvpStatus == RsvpStatus.ATTENDING) {
            subscribeUserToHangout(event.userId, event.hangoutId)
        }
        broadcastToHangout(
            hangoutId = event.hangoutId,
            message = OutgoingWebSocketMessage(
                type = OutgoingWebSocketMessageType.RSVP_UPDATED,
                payload = objectMapper.writeValueAsString(
                    LobbyRsvpDto(
                        hangoutId = event.hangoutId,
                        userId = event.userId,
                        displayName = event.displayName,
                        rsvpStatus = event.rsvpStatus
                    )
                )
            )
        )
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onInviteWithdrawn(event: HangoutInviteWithdrawnEvent) {
        // Invitee was only PENDING, never subscribed — nothing to unwire, just tell the room.
        broadcastToHangout(
            hangoutId = event.hangoutId,
            message = OutgoingWebSocketMessage(
                type = OutgoingWebSocketMessageType.INVITE_WITHDRAWN,
                payload = objectMapper.writeValueAsString(
                    LobbyParticipantDto(
                        hangoutId = event.hangoutId,
                        userId = event.userId,
                        displayName = event.displayName
                    )
                )
            )
        )
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onNonPayerRemoved(event: HangoutNonPayerRemovedEvent) {
        unsubscribeUserFromHangout(event.userId, event.hangoutId)
        broadcastToHangout(
            hangoutId = event.hangoutId,
            message = OutgoingWebSocketMessage(
                type = OutgoingWebSocketMessageType.NON_PAYER_REMOVED,
                payload = objectMapper.writeValueAsString(
                    LobbyParticipantDto(
                        hangoutId = event.hangoutId,
                        userId = event.userId,
                        displayName = event.displayName
                    )
                )
            )
        )
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onPaymentReceived(event: HangoutPaymentReceivedEvent) {
        broadcastToHangout(
            hangoutId = event.hangoutId,
            message = OutgoingWebSocketMessage(
                type = OutgoingWebSocketMessageType.PAYMENT_RECEIVED,
                payload = objectMapper.writeValueAsString(
                    LobbyParticipantDto(
                        hangoutId = event.hangoutId,
                        userId = event.userId,
                        displayName = event.displayName
                    )
                )
            )
        )
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onPaymentDeadlineResolved(event: HangoutPaymentDeadlineResolvedEvent) {
        broadcastToHangout(
            hangoutId = event.hangoutId,
            message = OutgoingWebSocketMessage(
                type = OutgoingWebSocketMessageType.PAYMENT_DEADLINE_RESOLVED,
                payload = objectMapper.writeValueAsString(
                    LobbyHangoutDto(hangoutId = event.hangoutId)
                )
            )
        )
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onPayoutOutcome(event: HangoutPayoutOutcomeEvent) {
        sendToUser(
            userId = event.hostId,
            message = OutgoingWebSocketMessage(
                type = OutgoingWebSocketMessageType.PAYOUT_OUTCOME,
                payload = objectMapper.writeValueAsString(
                    LobbyPayoutDto(
                        hangoutId = event.hangoutId,
                        succeeded = event.succeeded
                    )
                )
            )
        )
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onParticipantLeft(event: HangoutParticipantLeftEvent) {
        // Unwire the leaver's sockets FIRST so they don't receive their own "left" message; the remaining attendees still do.
        unsubscribeUserFromHangout(event.userId, event.hangoutId)
        broadcastToHangout(
            hangoutId = event.hangoutId,
            message = OutgoingWebSocketMessage(
                type = OutgoingWebSocketMessageType.PARTICIPANT_LEFT,
                payload = objectMapper.writeValueAsString(
                    LobbyParticipantDto(
                        hangoutId = event.hangoutId,
                        userId = event.userId,
                        displayName = event.displayName
                    )
                )
            )
        )
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onHangoutUpdated(event: HangoutUpdatedEvent) {
        broadcastToHangout(
            hangoutId = event.hangoutId,
            message = OutgoingWebSocketMessage(
                type = OutgoingWebSocketMessageType.HANGOUT_UPDATED,
                payload = objectMapper.writeValueAsString(
                    LobbyHostActionDto(
                        hangoutId = event.hangoutId,
                        hostDisplayName = event.hostDisplayName
                    )
                )
            )
        )
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onHangoutCompleted(event: HangoutCompletedEvent) {
        // Terminal: tell everyone FIRST, then tear the whole hangout out of the maps.
        broadcastToHangout(
            hangoutId = event.hangoutId,
            message = OutgoingWebSocketMessage(
                type = OutgoingWebSocketMessageType.HANGOUT_COMPLETED,
                payload = objectMapper.writeValueAsString(
                    LobbyHostActionDto(
                        hangoutId = event.hangoutId,
                        hostDisplayName = event.hostDisplayName
                    )
                )
            )
        )
        purgeHangout(event.hangoutId)
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onHangoutCancelled(event: HangoutCancelledEvent) {
        // Terminal: tell everyone FIRST, then tear the whole hangout out of the maps.
        broadcastToHangout(
            hangoutId = event.hangoutId,
            message = OutgoingWebSocketMessage(
                type = OutgoingWebSocketMessageType.HANGOUT_CANCELLED,
                payload = objectMapper.writeValueAsString(
                    LobbyHostActionDto(
                        hangoutId = event.hangoutId,
                        hostDisplayName = event.hostDisplayName
                    )
                )
            )
        )
        purgeHangout(event.hangoutId)
    }

    // Add a now-attending user's live sockets to this hangout.
    private fun subscribeUserToHangout(userId: UserId, hangoutId: HangoutId) {
        connectionLock.write {
            val sessionIds = userToSessions[userId] ?: return@write
            userHangoutIds.compute(userId) { _, ids ->
                (ids ?: mutableSetOf()).apply { add(hangoutId) }
            }
            sessionIds.forEach { sessionId ->
                hangoutToSessions.compute(hangoutId) { _, sessions ->
                    (sessions ?: mutableSetOf()).apply { add(sessionId) }
                }
            }
        }
    }

    // Remove a leaving user's sockets from this hangout and take their ballot and pin with them.
    private fun unsubscribeUserFromHangout(userId: UserId, hangoutId: HangoutId) {
        var wasViewing = false
        var votesSnapshot: Map<UserId, String>? = null
        var locationDropped = false

        connectionLock.write {
            // Someone out of the hangout gets no say in where it happens, and their pin no
            // longer belongs in the group center. Done before the socket check below, because they
            // may well have left over REST with the app closed and no session left to unwire.
            hangoutVotes[hangoutId]?.let { votes ->
                if (votes.remove(userId) != null) votesSnapshot = HashMap(votes)
            }
            locationDropped = hangoutLocations[hangoutId]?.remove(userId) != null

            val sessionIds = userToSessions[userId] ?: return@write
            userHangoutIds.compute(userId) { _, ids ->
                ids?.apply { remove(hangoutId) }?.takeIf { it.isNotEmpty() }
            }
            sessionIds.forEach { sessionId ->
                hangoutToSessions.compute(hangoutId) { _, sessions ->
                    sessions?.apply { remove(sessionId) }?.takeIf { it.isNotEmpty() }
                }
                // They no longer belong in this lobby, so their dot goes out with them.
                if (sessionViewing.remove(sessionId, hangoutId)) {
                    hangoutViewers.compute(hangoutId) { _, viewers ->
                        viewers?.apply { remove(sessionId) }?.takeIf { it.isNotEmpty() }
                    }
                    wasViewing = true
                }
            }
        }

        if (wasViewing) broadcastPresence(hangoutId)

        // The tally just shrank and the center just moved: everyone still here needs the new numbers.
        votesSnapshot?.let { votes ->
            broadcastToHangout(
                hangoutId = hangoutId,
                message = OutgoingWebSocketMessage(
                    type = OutgoingWebSocketMessageType.VOTE_TALLY,
                    payload = objectMapper.writeValueAsString(
                        VoteTallyDto(hangoutId = hangoutId, votes = votes)
                    )
                )
            )
        }
        if (locationDropped) {
            computeCenter(hangoutId)?.let { center ->
                broadcastToHangout(
                    hangoutId = hangoutId,
                    message = OutgoingWebSocketMessage(
                        type = OutgoingWebSocketMessageType.CENTER_UPDATE,
                        payload = objectMapper.writeValueAsString(center)
                    )
                )
            }
        }
    }

    // A completed/canceled hangout is gone: drop it from every map entirely,
    // including presence (viewers/viewing) so no stale green dots linger.
    private fun purgeHangout(hangoutId: HangoutId) {
        connectionLock.write {
            hangoutToSessions.remove(hangoutId)
            userHangoutIds.entries.removeIf { (_, ids) ->
                ids.remove(hangoutId)
                ids.isEmpty()
            }
            hangoutViewers.remove(hangoutId)
            sessionViewing.values.removeIf { it == hangoutId }
        }
        clearVotingState(hangoutId)
    }

    // Nominate a spot as a voting candidate: fetch its details, dedupe, cap, broadcast.
    private fun handleProposeSpot(userSession: UserSession, hangoutId: HangoutId, spotId: String) {
        if (!attendsHangout(userSession.userId, hangoutId)) {
            sendError(userSession.session, ErrorDto("NOT_ATTENDING", "You are not in this hangout"))
            return
        }
        if (!hangoutService.isVotingOpen(hangoutId)) {
            sendError(userSession.session, ErrorDto("VOTING_CLOSED", "Voting is not open for this hangout"))
            return
        }

        // Fetch canonical spot details OUTSIDE the lock (it's a network/Google call).
        val spot = try {
            spotService.getSpotById(spotId, userSession.userId)
        } catch (_: Exception) {
            sendError(userSession.session, ErrorDto("SPOT_NOT_FOUND", "That spot could not be loaded"))
            return
        }

        val outcome = connectionLock.write {
            val candidates = hangoutCandidates.getOrPut(hangoutId) { LinkedHashMap() }
            when {
                candidates.containsKey(spotId) -> ProposeOutcome.DUPLICATE
                candidates.size >= MAX_CANDIDATES -> ProposeOutcome.FULL
                else -> {
                    candidates[spotId] = spot
                    ProposeOutcome.ADDED
                }
            }
        }

        when (outcome) {
            ProposeOutcome.ADDED -> broadcastToHangout(
                hangoutId = hangoutId,
                message = OutgoingWebSocketMessage(
                    type = OutgoingWebSocketMessageType.CANDIDATE_ADDED,
                    payload = objectMapper.writeValueAsString(
                        CandidateAddedDto(hangoutId = hangoutId, spot = spot.toSpotDto())
                    )
                )
            )

            ProposeOutcome.FULL -> sendError(
                userSession.session,
                ErrorDto("CANDIDATES_FULL", "This hangout already has the maximum number of spots")
            )

            ProposeOutcome.DUPLICATE -> Unit // already on the ballot; nothing to do
        }
    }

    // Host pulls a spot off the ballot (moderation, e.g., someone hogged the slots).
    // Purges any votes for it so a removed spot can never win at close time, then rebroadcasts.
    private fun handleRemoveSpot(userSession: UserSession, hangoutId: HangoutId, spotId: String) {
        if (!hangoutService.isHost(hangoutId, userSession.userId)) {
            sendError(userSession.session, ErrorDto("NOT_HOST", "Only the host can remove a spot"))
            return
        }
        if (!hangoutService.isVotingOpen(hangoutId)) {
            sendError(userSession.session, ErrorDto("VOTING_CLOSED", "Voting is not open for this hangout"))
            return
        }

        val votesSnapshot = connectionLock.write {
            // Not on the ballot: nothing to remove.
            if (hangoutCandidates[hangoutId]?.remove(spotId) == null) return@write null
            val votes = hangoutVotes[hangoutId]
            votes?.values?.removeAll { it == spotId } // drop orphaned votes for the removed spot
            votes?.let { HashMap(it) } ?: emptyMap<UserId, String>()
        }

        if (votesSnapshot == null) {
            sendError(userSession.session, ErrorDto("NOT_A_CANDIDATE", "That spot isn't on the ballot"))
            return
        }

        broadcastToHangout(
            hangoutId = hangoutId,
            message = OutgoingWebSocketMessage(
                type = OutgoingWebSocketMessageType.CANDIDATE_REMOVED,
                payload = objectMapper.writeValueAsString(
                    CandidateRemovedDto(hangoutId = hangoutId, spotId = spotId)
                )
            )
        )
        // Tally may have shrunk (votes for the removed spot are gone): push the fresh count.
        broadcastToHangout(
            hangoutId = hangoutId,
            message = OutgoingWebSocketMessage(
                type = OutgoingWebSocketMessageType.VOTE_TALLY,
                payload = objectMapper.writeValueAsString(
                    VoteTallyDto(hangoutId = hangoutId, votes = votesSnapshot)
                )
            )
        )
    }

    // Record (or change) this attendee's vote, then broadcast the whole tally.
    private fun handleCastVote(userSession: UserSession, hangoutId: HangoutId, spotId: String) {
        if (!attendsHangout(userSession.userId, hangoutId)) {
            sendError(userSession.session, ErrorDto("NOT_ATTENDING", "You are not in this hangout"))
            return
        }

        val votesSnapshot = connectionLock.write {
            // You can only vote for a spot that's actually a candidate.
            if (hangoutCandidates[hangoutId]?.containsKey(spotId) != true) return@write null
            val votes = hangoutVotes.getOrPut(hangoutId) { mutableMapOf() }
            votes[userSession.userId] = spotId // overwrites any previous vote (changeable)
            HashMap(votes) // copy to broadcast outside the lock
        }

        if (votesSnapshot == null) {
            sendError(userSession.session, ErrorDto("NOT_A_CANDIDATE", "That spot isn't up for voting"))
            return
        }

        broadcastToHangout(
            hangoutId = hangoutId,
            message = OutgoingWebSocketMessage(
                type = OutgoingWebSocketMessageType.VOTE_TALLY,
                payload = objectMapper.writeValueAsString(
                    VoteTallyDto(hangoutId = hangoutId, votes = votesSnapshot)
                )
            )
        )
    }

    // Host ends the round. The winner is always the highest-voted candidate
    // The host only breaks a genuine tie (by passing chosenSpotId, which must be a co-leader).
    private fun handleCloseVoting(userSession: UserSession, hangoutId: HangoutId, chosenSpotId: String?) {
        if (!hangoutService.isHost(hangoutId, userSession.userId)) {
            sendError(userSession.session, ErrorDto("NOT_HOST", "Only the host can close voting"))
            return
        }
        if (!hangoutService.isVotingOpen(hangoutId)) {
            sendError(userSession.session, ErrorDto("VOTING_CLOSED", "Voting is not open for this hangout"))
            return
        }

        // Who's winning? The spotId(s) with the most votes.
        val leaders = connectionLock.read {
            val picks = hangoutVotes[hangoutId]?.values ?: emptyList()
            if (picks.isEmpty()) {
                emptySet()
            } else {
                val counts = picks.groupingBy { it }.eachCount()
                val topCount = counts.values.max()
                counts.filterValues { it == topCount }.keys.toSet()
            }
        }

        if (leaders.isEmpty()) {
            sendError(userSession.session, ErrorDto("NO_VOTES", "No votes have been cast yet"))
            return
        }

        val winner: String = when {
            // Host is resolving a tie: their pick must be one of the tied leaders.
            chosenSpotId != null -> {
                if (chosenSpotId !in leaders) {
                    sendError(userSession.session, ErrorDto("INVALID_CHOICE", "Pick one of the tied spots"))
                    return
                }
                chosenSpotId
            }
            // Clear single winner.
            leaders.size == 1 -> leaders.first()
            // Tie, and the host hasn't chosen yet: ask them to break it.
            else -> {
                broadcastToHangout(
                    hangoutId = hangoutId,
                    message = OutgoingWebSocketMessage(
                        type = OutgoingWebSocketMessageType.VOTING_TIE,
                        payload = objectMapper.writeValueAsString(
                            VotingTieDto(hangoutId = hangoutId, tiedSpotIds = leaders.toList())
                        )
                    )
                )
                return
            }
        }

        try {
            hangoutService.chooseSpot(hostId = userSession.userId, hangoutId = hangoutId, spotId = winner)
        } catch (e: Exception) {
            logger.warn("Failed to lock spot for hangout $hangoutId", e)
            sendError(userSession.session, ErrorDto("LOCK_FAILED", "Could not lock the spot; please try again"))
            return
        }
        clearVotingState(hangoutId)
    }

    // Voting is over for this hangout: drop the in-memory ballot (the lobby lives on).
    private fun clearVotingState(hangoutId: HangoutId) {
        connectionLock.write {
            hangoutCandidates.remove(hangoutId)
            hangoutVotes.remove(hangoutId)
            hangoutLocations.remove(hangoutId)
        }
    }

    // Store an attendee's one-off location and re-broadcast the averaged group center.
    private fun handleLocationShare(
        userSession: UserSession,
        hangoutId: HangoutId,
        latitude: Double,
        longitude: Double
    ) {
        if (!attendsHangout(userSession.userId, hangoutId)) {
            sendError(userSession.session, ErrorDto("NOT_ATTENDING", "You are not in this hangout"))
            return
        }
        if (!hangoutService.isVotingOpen(hangoutId)) {
            sendError(userSession.session, ErrorDto("VOTING_CLOSED", "Voting is not open for this hangout"))
            return
        }

        connectionLock.write {
            hangoutLocations.getOrPut(hangoutId) { mutableMapOf() }[userSession.userId] =
                Coordinates(latitude, longitude)
        }

        val center = computeCenter(hangoutId) ?: return
        broadcastToHangout(
            hangoutId = hangoutId,
            message = OutgoingWebSocketMessage(
                type = OutgoingWebSocketMessageType.CENTER_UPDATE,
                payload = objectMapper.writeValueAsString(center)
            )
        )
    }

    private fun attendsHangout(userId: UserId, hangoutId: HangoutId): Boolean =
        connectionLock.read { userHangoutIds[userId]?.contains(hangoutId) == true }

    // Client opened a lobby screen. Mark this socket as viewing that hangout and announce the refreshed presence list.
    private fun handleEnterLobby(userSession: UserSession, hangoutId: HangoutId) {
        val sessionId = userSession.session.id
        var previous: HangoutId? = null

        val allowed = connectionLock.write {
            if (userHangoutIds[userSession.userId]?.contains(hangoutId) != true) return@write false

            // Moving focus from another lobby? Drop this socket out of the old one first.
            previous = sessionViewing.put(sessionId, hangoutId)
            if (previous != null && previous != hangoutId) {
                hangoutViewers.compute(previous) { _, viewers ->
                    viewers?.apply { remove(sessionId) }?.takeIf { it.isNotEmpty() }
                }
            }
            hangoutViewers.compute(hangoutId) { _, viewers ->
                (viewers ?: mutableSetOf()).apply { add(sessionId) }
            }
            true
        }

        if (!allowed) {
            sendError(
                userSession.session,
                ErrorDto(
                    code = "NOT_ATTENDING",
                    message = "You are not in this hangout"
                )
            )
            return
        }

        // Refresh dots for the lobby left behind (if any) and the one just entered.
        previous?.let { if (it != hangoutId) broadcastPresence(it) }
        broadcastPresence(hangoutId)

        // Catch this joiner up on any voting already in progress.
        sendVotingSnapshot(userSession, hangoutId)
    }

    // Send the whole ballot to a single joiner so they see exactly what everyone already in the lobby sees.
    private fun sendVotingSnapshot(userSession: UserSession, hangoutId: HangoutId) {
        val candidates: List<Spot>
        val votes: Map<UserId, String>
        connectionLock.read {
            candidates = hangoutCandidates[hangoutId]?.values?.toList() ?: emptyList()
            votes = hangoutVotes[hangoutId]?.let { HashMap(it) } ?: emptyMap()
        }
        val center = computeCenter(hangoutId)

        sendJson(
            userSession = userSession,
            messageJson = objectMapper.writeValueAsString(
                OutgoingWebSocketMessage(
                    type = OutgoingWebSocketMessageType.VOTING_SNAPSHOT,
                    payload = objectMapper.writeValueAsString(
                        VotingSnapshotDto(
                            hangoutId = hangoutId,
                            candidates = candidates.map { it.toSpotDto() },
                            votes = votes,
                            latitude = center?.latitude,
                            longitude = center?.longitude
                        )
                    )
                )
            )
        )
    }

    // Average of every shared location for this hangout, or null if none shared yet.
    private fun computeCenter(hangoutId: HangoutId): CenterUpdateDto? {
        val coordinates = connectionLock.read {
            hangoutLocations[hangoutId]?.values?.toList()
        } ?: return null
        if (coordinates.isEmpty()) return null

        return CenterUpdateDto(
            hangoutId = hangoutId,
            latitude = coordinates.map { it.latitude }.average(),
            longitude = coordinates.map { it.longitude }.average()
        )
    }

    // Client left the lobby screen. Drop this socket from the viewers and refresh dots.
    private fun handleLeaveLobby(userSession: UserSession, hangoutId: HangoutId) {
        val sessionId = userSession.session.id

        val wasViewing = connectionLock.write {
            if (sessionViewing[sessionId] != hangoutId) return@write false
            sessionViewing.remove(sessionId)
            hangoutViewers.compute(hangoutId) { _, viewers ->
                viewers?.apply { remove(sessionId) }?.takeIf { it.isNotEmpty() }
            }
            true
        }

        if (wasViewing) broadcastPresence(hangoutId)
    }

    // Recompute who is present in a lobby and push the whole set to it.
    private fun broadcastPresence(hangoutId: HangoutId) {
        val presentUserIds = connectionLock.read {
            hangoutViewers[hangoutId]?.mapNotNull { sessions[it]?.userId }?.toSet() ?: emptySet()
        }
        broadcastToHangout(
            hangoutId = hangoutId,
            message = OutgoingWebSocketMessage(
                type = OutgoingWebSocketMessageType.PRESENCE_UPDATE,
                payload = objectMapper.writeValueAsString(
                    PresenceDto(
                        hangoutId = hangoutId,
                        presentUserIds = presentUserIds
                    )
                )
            )
        )
    }

    private fun broadcastToHangout(hangoutId: HangoutId, message: OutgoingWebSocketMessage) {
        val sessionIds = connectionLock.read {
            hangoutToSessions[hangoutId]?.toList() ?: emptyList()
        }
        if (sessionIds.isEmpty()) return

        val messageJson = objectMapper.writeValueAsString(message)

        sessionIds.forEach { sessionId ->
            val userSession = connectionLock.read { sessions[sessionId] } ?: return@forEach
            sendJson(userSession, messageJson)
        }
    }

    // Every device this one person has open, rather than the whole room.
    private fun sendToUser(userId: UserId, message: OutgoingWebSocketMessage) {
        val sessionIds = connectionLock.read {
            userToSessions[userId]?.toList() ?: emptyList()
        }
        if (sessionIds.isEmpty()) return

        val messageJson = objectMapper.writeValueAsString(message)

        sessionIds.forEach { sessionId ->
            val userSession = connectionLock.read { sessions[sessionId] } ?: return@forEach
            sendJson(userSession, messageJson)
        }
    }

    // Push already-serialized JSON down one socket, skipping dead/closed ones.
    private fun sendJson(userSession: UserSession, messageJson: String) {
        if (!userSession.session.isOpen) return
        try {
            userSession.session.sendMessage(TextMessage(messageJson))
        } catch (e: Exception) {
            logger.error("Failed sending to session ${userSession.session.id}", e)
        }
    }

    private fun sendError(session: WebSocketSession, error: ErrorDto) {
        val webSocketMessage = objectMapper.writeValueAsString(
            OutgoingWebSocketMessage(
                type = OutgoingWebSocketMessageType.ERROR,
                payload = objectMapper.writeValueAsString(error)
            )
        )

        try {
            session.sendMessage(TextMessage(webSocketMessage))
        } catch (e: Exception) {
            logger.warn("Couldn't send error to session ${session.id}", e)
        }
    }

    private data class UserSession(
        val userId: UserId,
        val session: WebSocketSession,
        val lastPongTimestamp: Long = System.currentTimeMillis()
    )

    private data class Coordinates(
        val latitude: Double,
        val longitude: Double
    )

    private enum class ProposeOutcome { ADDED, DUPLICATE, FULL }
}