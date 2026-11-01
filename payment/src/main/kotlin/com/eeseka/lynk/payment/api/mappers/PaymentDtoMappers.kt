package com.eeseka.lynk.payment.api.mappers

import com.eeseka.lynk.payment.api.dto.BankAccountDto
import com.eeseka.lynk.payment.api.dto.BankDto
import com.eeseka.lynk.payment.api.dto.PaymentInitializationDto
import com.eeseka.lynk.payment.api.dto.PaymentSettingsDto
import com.eeseka.lynk.payment.api.dto.PayoutAccountDto
import com.eeseka.lynk.payment.domain.model.Bank
import com.eeseka.lynk.payment.domain.model.BankAccount
import com.eeseka.lynk.payment.domain.model.PaymentInitialization
import com.eeseka.lynk.payment.domain.model.PaymentSettings
import com.eeseka.lynk.payment.domain.model.PayoutAccount

fun PayoutAccount.toPayoutAccountDto(): PayoutAccountDto {
    return PayoutAccountDto(
        bankName = bankName,
        accountNumberLast4 = accountNumberLast4,
        accountHolderName = accountHolderName
    )
}

fun Bank.toBankDto(): BankDto {
    return BankDto(
        name = name,
        code = code,
        logoUrl = logoUrl
    )
}

fun BankAccount.toBankAccountDto(): BankAccountDto {
    return BankAccountDto(
        accountNumber = accountNumber,
        accountHolderName = accountHolderName,
        bankCode = bankCode
    )
}

fun PaymentInitialization.toPaymentInitializationDto(): PaymentInitializationDto {
    return PaymentInitializationDto(
        authorizationUrl = authorizationUrl,
        reference = reference,
        amountKobo = amountKobo,
        netAmountKobo = netAmountKobo
    )
}

fun PaymentSettings.toPaymentSettingsDto(): PaymentSettingsDto {
    return PaymentSettingsDto(
        hangoutId = hangoutId,
        totalCostKobo = totalCostKobo,
        costPerPersonKobo = costPerPersonKobo,
        paymentDeadline = paymentDeadline,
        payoutAccount = payoutAccount.toPayoutAccountDto()
    )
}