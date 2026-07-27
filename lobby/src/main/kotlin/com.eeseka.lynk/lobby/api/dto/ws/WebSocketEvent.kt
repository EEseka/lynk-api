package com.eeseka.lynk.lobby.api.dto.ws

enum class IncomingWebSocketMessageType {
    ENTER_LOBBY,
    LEAVE_LOBBY,
    LOCATION_SHARE,
    PROPOSE_SPOT,
    REMOVE_SPOT,
    CAST_VOTE,
    CLOSE_VOTING
}

enum class OutgoingWebSocketMessageType {
    PARTICIPANT_INVITED,
    RSVP_UPDATED,
    INVITE_WITHDRAWN,
    PARTICIPANT_LEFT,
    HANGOUT_UPDATED,
    HANGOUT_COMPLETED,
    HANGOUT_CANCELLED,
    PRESENCE_UPDATE,
    VOTING_SNAPSHOT,
    CANDIDATE_ADDED,
    CANDIDATE_REMOVED,
    VOTE_TALLY,
    CENTER_UPDATE,
    VOTING_TIE,
    ERROR
}

data class IncomingWebSocketMessage(
    val type: IncomingWebSocketMessageType,
    val payload: String
)

data class OutgoingWebSocketMessage(
    val type: OutgoingWebSocketMessageType,
    val payload: String
)