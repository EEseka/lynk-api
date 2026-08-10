package com.eeseka.lynk.payment.api.exception_handling

import com.eeseka.lynk.payment.domain.exception.BankAccountNotResolvedException
import com.eeseka.lynk.payment.domain.exception.PaymentAccessDeniedException
import com.eeseka.lynk.payment.domain.exception.PaymentIllegalArgumentException
import com.eeseka.lynk.payment.domain.exception.PaymentIllegalStateException
import com.eeseka.lynk.payment.domain.exception.PaystackRateLimitedException
import com.eeseka.lynk.payment.domain.exception.PaystackUnavailableException
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class PaymentExceptionHandler {

    @ExceptionHandler(BankAccountNotResolvedException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun onBankAccountNotResolved(e: BankAccountNotResolvedException) = mapOf(
        "code" to "BANK_ACCOUNT_NOT_RESOLVED",
        "message" to e.message
    )

    @ExceptionHandler(PaystackRateLimitedException::class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    fun onPaystackRateLimited(e: PaystackRateLimitedException) = mapOf(
        "code" to "PAYSTACK_RATE_LIMITED",
        "message" to e.message
    )

    @ExceptionHandler(PaystackUnavailableException::class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    fun onPaystackUnavailable(e: PaystackUnavailableException) = mapOf(
        "code" to "PAYSTACK_UNAVAILABLE",
        "message" to e.message
    )

    @ExceptionHandler(PaymentIllegalStateException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun onPaymentIllegalState(e: PaymentIllegalStateException) = mapOf(
        "code" to "PAYMENT_ILLEGAL_STATE",
        "message" to e.message
    )

    @ExceptionHandler(PaymentIllegalArgumentException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun onPaymentIllegalArgument(e: PaymentIllegalArgumentException) = mapOf(
        "code" to "PAYMENT_ILLEGAL_ARGUMENT",
        "message" to e.message
    )

    @ExceptionHandler(PaymentAccessDeniedException::class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    fun onPaymentAccessDenied(e: PaymentAccessDeniedException) = mapOf(
        "code" to "PAYMENT_ACCESS_DENIED",
        "message" to e.message
    )
}
