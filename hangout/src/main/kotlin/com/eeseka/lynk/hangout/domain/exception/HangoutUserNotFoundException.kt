package com.eeseka.lynk.hangout.domain.exception

class HangoutUserNotFoundException(userId: String) : RuntimeException("Hangout user with id $userId not found")