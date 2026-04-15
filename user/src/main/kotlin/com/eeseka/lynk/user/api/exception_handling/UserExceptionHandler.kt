package com.eeseka.lynk.user.api.exception_handling

import com.eeseka.lynk.common.domain.exception.InvalidTokenException
import com.eeseka.lynk.common.domain.exception.StorageException
import com.eeseka.lynk.common.domain.exception.UnauthorizedException
import com.eeseka.lynk.user.domain.exception.InvalidProfilePictureException
import com.eeseka.lynk.user.domain.exception.RateLimitException
import com.eeseka.lynk.user.domain.exception.UserAlreadyExistsException
import com.eeseka.lynk.user.domain.exception.UserNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class UserExceptionHandler {

    @ExceptionHandler(UserAlreadyExistsException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun onUserAlreadyExists(e: UserAlreadyExistsException) = mapOf(
        "code" to "USER_EXISTS",
        "message" to e.message
    )

    @ExceptionHandler(UserNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun onUserNotFound(e: UserNotFoundException) = mapOf(
        "code" to "USER_NOT_FOUND",
        "message" to e.message
    )

    @ExceptionHandler(InvalidTokenException::class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    fun onInvalidToken(e: InvalidTokenException) = mapOf(
        "code" to "INVALID_TOKEN",
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

    @ExceptionHandler(InvalidProfilePictureException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun onInvalidProfilePicture(e: InvalidProfilePictureException) = mapOf(
        "code" to "INVALID_PROFILE_PICTURE",
        "message" to e.message
    )

    @ExceptionHandler(StorageException::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun onStorageError(e: StorageException) = mapOf(
        "code" to "STORAGE_ERROR",
        "message" to e.message
    )

    @ExceptionHandler(IllegalArgumentException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun onIllegalArgument(e: IllegalArgumentException) = mapOf(
        "code" to "BAD_REQUEST",
        "message" to (e.message ?: "Invalid request parameters")
    )

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun onValidationException(
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
}