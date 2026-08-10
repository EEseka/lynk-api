package com.eeseka.lynk.common.api.exception_handling

import com.eeseka.lynk.common.domain.exception.GuestActionNotAllowedException
import com.eeseka.lynk.common.domain.exception.RateLimitException
import com.eeseka.lynk.common.domain.exception.UnauthorizedException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.HandlerMethodValidationException

@RestControllerAdvice
class CommonExceptionHandler {

    @ExceptionHandler(GuestActionNotAllowedException::class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    fun onGuestActionNotAllowed(e: GuestActionNotAllowedException) = mapOf(
        "code" to "GUEST_ACTION_NOT_ALLOWED",
        "message" to e.message
    )

    @ExceptionHandler(UnauthorizedException::class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    fun onUnauthorized(e: UnauthorizedException) = mapOf(
        "code" to "UNAUTHORIZED",
        "message" to e.message
    )

    @ExceptionHandler(RateLimitException::class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    fun onRateLimitExceeded(e: RateLimitException) = mapOf(
        "code" to "RATE_LIMIT_EXCEEDED",
        "message" to e.message
    )

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun onRequestBodyInvalid(
        e: MethodArgumentNotValidException
    ): ResponseEntity<Map<String, Any>> {
        val errors = e.bindingResult.allErrors.map {
            it.defaultMessage ?: "Invalid value"
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