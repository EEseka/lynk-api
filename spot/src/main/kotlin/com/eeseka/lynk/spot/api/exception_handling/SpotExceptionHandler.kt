package com.eeseka.lynk.spot.api.exception_handling

import com.eeseka.lynk.spot.domain.exception.SpotNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.HandlerMethodValidationException

@RestControllerAdvice
class SpotExceptionHandler {

    @ExceptionHandler(SpotNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun onSpotNotFound(e: SpotNotFoundException) = mapOf(
        "code" to "SPOT_NOT_FOUND",
        "message" to e.message
    )

    @ExceptionHandler(HandlerMethodValidationException::class)
    fun onRequestParamInvalid(
        e: HandlerMethodValidationException
    ): ResponseEntity<Map<String, Any>> {

        val errors = e.parameterValidationResults.flatMap { result ->
            val paramName = result.methodParameter.parameterName ?: "parameter"

            result.resolvableErrors.map { err ->
                "$paramName ${err.defaultMessage ?: "Invalid value"}"
            }
        }

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(
                mapOf(
                    "code" to "VALIDATION_ERROR",
                    "errors" to errors
                )
            )
    }
}