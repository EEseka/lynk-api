package com.eeseka.lynk.user.api.exception_handling

import com.eeseka.lynk.common.domain.exception.InvalidTokenException
import com.eeseka.lynk.user.domain.exception.StorageException
import com.eeseka.lynk.user.domain.exception.InvalidProfilePictureException
import com.eeseka.lynk.user.domain.exception.UserAlreadyExistsException
import com.eeseka.lynk.user.domain.exception.UserNotFoundException
import com.eeseka.lynk.user.domain.exception.UsernameAlreadySetException
import org.springframework.http.HttpStatus
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

    @ExceptionHandler(UsernameAlreadySetException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun onUsernameAlreadySet(e: UsernameAlreadySetException) = mapOf(
        "code" to "USERNAME_ALREADY_SET",
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
}