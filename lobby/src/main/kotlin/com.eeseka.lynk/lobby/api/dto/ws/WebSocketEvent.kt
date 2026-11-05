package com.eeseka.lynk.lobby.api.dto.ws

enum class IncomingWebSocketMessageType {
    ENTER_LOBBY,
    LEAVE_LOBBY,
    LOCATION_SHARE,
    LIVE_LOCATION_SHARE,
    LIVE_LOCATION_STOP,
    PROPOSE_SPOT,
    REMOVE_SPOT,
    CAST_VOTE,
    CLOSE_VOTING
}

enum class OutgoingWebSocketMessageType {
    PARTICIPANT_INVITED,
    RSVP_UPDATED,
    INVITE_WITHDRAWN,
    NON_PAYER_REMOVED,
    PARTICIPANT_LEFT,
    PARTICIPANT_ARRIVED,
    NOTES_CHANGED,
    PAYMENT_RECEIVED,
    PAYMENT_DEADLINE_RESOLVED,
    PAYOUT_OUTCOME,
    HANGOUT_UPDATED,
    HANGOUT_STARTED,
    HANGOUT_COMPLETED,
    HANGOUT_CANCELLED,
    PHOTOS_ADDED,
    PHOTO_DELETED,
    PRESENCE_UPDATE,
    VOTING_SNAPSHOT,
    CANDIDATE_ADDED,
    CANDIDATE_REMOVED,
    VOTE_TALLY,
    CENTER_UPDATE,
    VOTING_TIE,
    LIVE_LOCATION_UPDATE,
    LIVE_LOCATION_STOPPED,
    LIVE_LOCATION_SNAPSHOT,
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