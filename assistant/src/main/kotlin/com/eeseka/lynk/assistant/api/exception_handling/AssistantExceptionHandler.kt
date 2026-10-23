package com.eeseka.lynk.assistant.api.exception_handling

import com.eeseka.lynk.assistant.domain.exception.AssistantAnswerInProgressException
import com.eeseka.lynk.assistant.domain.exception.AssistantDisabledException
import com.eeseka.lynk.assistant.domain.exception.AssistantIllegalArgumentException
import com.eeseka.lynk.assistant.domain.exception.AssistantNotAttendingException
import com.eeseka.lynk.assistant.domain.exception.AssistantVotingClosedException
import com.eeseka.lynk.assistant.domain.exception.DiscoverChatExpiredException
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class AssistantExceptionHandler {

    @ExceptionHandler(AssistantDisabledException::class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    fun onAssistantDisabled(e: AssistantDisabledException) = mapOf(
        "code" to "ASSISTANT_DISABLED",
        "message" to e.message
    )

    @ExceptionHandler(AssistantAnswerInProgressException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun onAnswerInProgress(e: AssistantAnswerInProgressException) = mapOf(
        "code" to "ANSWER_IN_PROGRESS",
        "message" to e.message
    )

    @ExceptionHandler(AssistantVotingClosedException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun onVotingClosed(e: AssistantVotingClosedException) = mapOf(
        "code" to "VOTING_CLOSED",
        "message" to e.message
    )

    @ExceptionHandler(AssistantNotAttendingException::class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    fun onNotAttending(e: AssistantNotAttendingException) = mapOf(
        "code" to "NOT_ATTENDING",
        "message" to e.message
    )

    @ExceptionHandler(DiscoverChatExpiredException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun onDiscoverChatExpired(e: DiscoverChatExpiredException) = mapOf(
        "code" to "CHAT_EXPIRED",
        "message" to e.message
    )

    @ExceptionHandler(AssistantIllegalArgumentException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun onIllegalArgument(e: AssistantIllegalArgumentException) = mapOf(
        "code" to "ASSISTANT_ILLEGAL_ARGUMENT",
        "message" to e.message
    )
}
