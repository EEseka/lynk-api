package com.eeseka.lynk.payment.service

import com.eeseka.lynk.payment.domain.exception.BankAccountNotResolvedException
import com.eeseka.lynk.payment.domain.model.Bank
import com.eeseka.lynk.payment.domain.model.BankAccount
import com.eeseka.lynk.payment.infra.bank_logo.BankLogoClient
import com.eeseka.lynk.payment.infra.paystack.PaystackClient
import org.springframework.stereotype.Service

@Service
class BankAccountService(
    private val paystackClient: PaystackClient,
    private val bankLogoClient: BankLogoClient
) {
    companion object {
        private const val NUBAN_LENGTH = 10
    }

    fun getBanks(): List<Bank> {
        val logoUrlsByBankCode = bankLogoClient.getLogoUrlsByBankCode()
        return paystackClient.getBanks().map { bank ->
            bank.copy(logoUrl = logoUrlsByBankCode[bank.code])
        }
    }

    fun resolveAccount(accountNumber: String, bankCode: String): BankAccount {
        val trimmedAccountNumber = accountNumber.trim()
        val trimmedBankCode = bankCode.trim()

        if (trimmedAccountNumber.length != NUBAN_LENGTH || !trimmedAccountNumber.all { it.isDigit() }) {
            throw BankAccountNotResolvedException("An account number must be $NUBAN_LENGTH digits")
        }

        return paystackClient.resolveAccount(
            accountNumber = trimmedAccountNumber,
            bankCode = trimmedBankCode
        )
    }
}