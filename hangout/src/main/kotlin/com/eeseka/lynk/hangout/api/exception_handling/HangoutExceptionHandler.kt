package com.eeseka.lynk.hangout.api.exception_handling

import com.eeseka.lynk.hangout.domain.exception.HangoutAccessDeniedException
import com.eeseka.lynk.hangout.domain.exception.HangoutIllegalArgumentException
import com.eeseka.lynk.hangout.domain.exception.HangoutIllegalStateException
import com.eeseka.lynk.hangout.domain.exception.HangoutNotFoundException
import com.eeseka.lynk.hangout.domain.exception.HangoutUserNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.HandlerMethodValidationException

@RestControllerAdvice
class HangoutExceptionHandler {

    @ExceptionHandler(HangoutNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun onHangoutNotFound(e: HangoutNotFoundException) = mapOf(
        "code" to "HANGOUT_NOT_FOUND",
        "message" to e.message
    )

    @ExceptionHandler(HangoutUserNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun onHangoutUserNotFound(e: HangoutUserNotFoundException) = mapOf(
        "code" to "HANGOUT_USER_NOT_FOUND",
        "message" to e.message
    )

    @ExceptionHandler(HangoutAccessDeniedException::class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    fun onHangoutAccessDenied(e: HangoutAccessDeniedException) = mapOf(
        "code" to "HANGOUT_ACCESS_DENIED",
        "message" to e.message
    )

    @ExceptionHandler(HangoutIllegalStateException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun onHangoutIllegalState(e: HangoutIllegalStateException) = mapOf(
        "code" to "HANGOUT_ILLEGAL_STATE",
        "message" to e.message
    )

    @ExceptionHandler(HangoutIllegalArgumentException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun onHangoutIllegalArgument(e: HangoutIllegalArgumentException) = mapOf(
        "code" to "HANGOUT_ILLEGAL_ARGUMENT",
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