package com.eeseka.lynk.hangout.domain.model

// Who gets a push notif for a new note. Everyone sees the note either way; the host's always go to everyone.
enum class HangoutNoteAudience {
    EVERYONE,
    HOST
}
