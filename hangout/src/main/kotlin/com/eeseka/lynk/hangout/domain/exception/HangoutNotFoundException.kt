package com.eeseka.lynk.hangout.domain.exception

class HangoutNotFoundException(hangoutId: String) : RuntimeException("Hangout with ID $hangoutId not found")