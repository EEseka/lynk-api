package com.eeseka.lynk.notification.api.exception_handling

import com.eeseka.lynk.notification.domain.exception.InvalidDeviceTokenException
import com.eeseka.lynk.notification.domain.exception.NotificationNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class NotificationExceptionHandler {

    @ExceptionHandler(InvalidDeviceTokenException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun onInvalidDeviceToken(e: InvalidDeviceTokenException) = mapOf(
        "code" to "INVALID_DEVICE_TOKEN",
        "message" to e.message
    )

    @ExceptionHandler(NotificationNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun onNotificationNotFound(e: NotificationNotFoundException) = mapOf(
        "code" to "NOTIFICATION_NOT_FOUND",
        "message" to e.message
    )
}