package com.eeseka.lynk.spot.api.exception_handling

import com.eeseka.lynk.spot.domain.exception.SpotNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class SpotExceptionHandler {

    @ExceptionHandler(SpotNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun onSpotNotFound(e: SpotNotFoundException) = mapOf(
        "code" to "SPOT_NOT_FOUND",
        "message" to e.message
    )
}