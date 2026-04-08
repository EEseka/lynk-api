package com.eeseka.lynk.common.domain.events.user

import com.eeseka.lynk.common.domain.events.LynkEvent
import com.eeseka.lynk.common.domain.type.UserId
import java.time.Instant
import java.util.*

sealed class UserEvent(
    override val eventId: String = UUID.randomUUID().toString(),
    override val exchange: String = UserEventConstants.USER_EXCHANGE,
    override val occurredAt: Instant = Instant.now()
) : LynkEvent {

    data class Created(
        val userId: UserId,
        val email: String,
        override val eventKey: String = UserEventConstants.USER_CREATED_KEY
    ) : UserEvent(), LynkEvent

    data class ProfileCompleted(
        val userId: UserId,
        val email: String,
        val username: String,
        val displayName: String,
        override val eventKey: String = UserEventConstants.USER_PROFILE_COMPLETED_KEY
    ) : UserEvent(), LynkEvent

    data class ProfileUpdated(
        val userId: UserId,
        override val eventKey: String = UserEventConstants.USER_PROFILE_UPDATED_KEY
    ) : UserEvent(), LynkEvent

    data class Deleted(
        val userId: UserId,
        override val eventKey: String = UserEventConstants.USER_DELETED_KEY
    ) : UserEvent(), LynkEvent
}