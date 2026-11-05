package com.eeseka.lynk.lobby.domain.model

import com.eeseka.lynk.common.domain.type.UserId

sealed interface RemoveOutcome {
    data class Removed(val votes: Map<UserId, String>) : RemoveOutcome
    data object NotACandidate : RemoveOutcome
    data object NotAllowed : RemoveOutcome
}
