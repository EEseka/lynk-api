package com.eeseka.lynk.hangout.domain.exception

class HangoutNoteNotFoundException(noteId: String) : RuntimeException("Note with ID $noteId not found")
