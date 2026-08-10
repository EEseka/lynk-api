package com.eeseka.lynk.payment.infra.paystack.mappers

import com.eeseka.lynk.payment.domain.events.PaymentProviderEvent
import com.eeseka.lynk.payment.domain.model.Bank
import com.eeseka.lynk.payment.domain.model.BankAccount
import com.eeseka.lynk.payment.domain.model.ProviderTransaction
import com.eeseka.lynk.payment.domain.model.ProviderTransactionStatus
import com.eeseka.lynk.payment.infra.paystack.dto.PaystackAccount
import com.eeseka.lynk.payment.infra.paystack.dto.PaystackBank
import com.eeseka.lynk.payment.infra.paystack.dto.PaystackTransactionStatus
import com.eeseka.lynk.payment.infra.paystack.dto.PaystackWebhook

private const val TRANSACTION_SUCCESS = "success"
private const val TRANSACTION_FAILED = "failed"
private const val TRANSACTION_PENDING = "pending"
private const val TRANSACTION_ONGOING = "ongoing"

private const val TRANSFER_STATUS_SUCCESS = "success"
private const val TRANSFER_STATUS_FAILED = "failed"
private const val TRANSFER_STATUS_REVERSED = "reversed"
private const val TRANSFER_STATUS_ABANDONED = "abandoned"
private const val TRANSFER_STATUS_PENDING = "pending"
private const val TRANSFER_STATUS_OTP = "otp"

private const val CHARGE_SUCCESS = "charge.success"
private const val REFUND_PROCESSED = "refund.processed"
private const val REFUND_FAILED = "refund.failed"
private const val TRANSFER_SUCCESS = "transfer.success"
private const val TRANSFER_FAILED = "transfer.failed"
private const val TRANSFER_REVERSED = "transfer.reversed"

fun PaystackBank.toBank(logoUrl: String?): Bank {
    return Bank(
        name = name,
        code = code,
        logoUrl = logoUrl
    )
}

fun PaystackAccount.toBankAccount(bankCode: String): BankAccount {
    return BankAccount(
        accountNumber = accountNumber,
        accountName = accountName,
        bankCode = bankCode
    )
}

fun PaystackTransactionStatus.toProviderTransaction(): ProviderTransaction = ProviderTransaction(
    status = when (status) {
        TRANSACTION_SUCCESS -> ProviderTransactionStatus.SUCCEEDED
        TRANSACTION_FAILED -> ProviderTransactionStatus.FAILED
        TRANSACTION_PENDING, TRANSACTION_ONGOING -> ProviderTransactionStatus.PENDING
        else -> ProviderTransactionStatus.UNKNOWN
    },
    paidAmountKobo = amount
)


fun PaystackTransactionStatus.toProviderTransfer(): ProviderTransaction = ProviderTransaction(
    status = when (status) {
        TRANSFER_STATUS_SUCCESS -> ProviderTransactionStatus.SUCCEEDED
        TRANSFER_STATUS_FAILED, TRANSFER_STATUS_REVERSED, TRANSFER_STATUS_ABANDONED -> ProviderTransactionStatus.FAILED
        TRANSFER_STATUS_PENDING, TRANSFER_STATUS_OTP -> ProviderTransactionStatus.PENDING
        else -> ProviderTransactionStatus.UNKNOWN
    },
    paidAmountKobo = amount
)

fun PaystackWebhook.toPaymentProviderEvent(): PaymentProviderEvent = when (event) {
    CHARGE_SUCCESS -> {
        val reference = data?.reference
        if (reference.isNullOrBlank()) {
            PaymentProviderEvent.Ignored("a $CHARGE_SUCCESS with no reference")
        } else {
            PaymentProviderEvent.ChargeSucceeded(
                reference = reference,
                paidAmountKobo = data.amount
            )
        }
    }

    REFUND_PROCESSED, REFUND_FAILED -> {
        val transactionReference = data?.transactionReference ?: data?.reference
        if (transactionReference.isNullOrBlank()) {
            PaymentProviderEvent.Ignored("a $event with no transaction reference")
        } else {
            PaymentProviderEvent.RefundSettled(
                transactionReference = transactionReference,
                succeeded = event == REFUND_PROCESSED
            )
        }
    }

    TRANSFER_SUCCESS, TRANSFER_FAILED, TRANSFER_REVERSED -> {
        val transferReference = data?.reference
        if (transferReference.isNullOrBlank()) {
            PaymentProviderEvent.Ignored("a $event with no reference")
        } else {
            PaymentProviderEvent.TransferSettled(
                transferReference = transferReference,
                succeeded = event == TRANSFER_SUCCESS,
                reason = data.gatewayResponse
            )
        }
    }

    else -> PaymentProviderEvent.Ignored("an event we do not handle: $event")
}