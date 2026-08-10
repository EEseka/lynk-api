package com.eeseka.lynk.hangout.domain.exception

class HangoutParticipantNotFoundException(userId: String) :
    RuntimeException("User with id $userId is not a participant of this hangout")
