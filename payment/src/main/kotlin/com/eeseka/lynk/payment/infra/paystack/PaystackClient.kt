package com.eeseka.lynk.payment.infra.paystack

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.payment.domain.exception.BankAccountNotResolvedException
import com.eeseka.lynk.payment.domain.exception.PayoutRejectedException
import com.eeseka.lynk.payment.domain.exception.PaystackRateLimitedException
import com.eeseka.lynk.payment.domain.exception.RefundRejectedException
import com.eeseka.lynk.payment.domain.exception.PaystackUnavailableException
import com.eeseka.lynk.payment.domain.model.Bank
import com.eeseka.lynk.payment.domain.model.BankAccount
import com.eeseka.lynk.payment.domain.model.ProviderTransaction
import com.eeseka.lynk.payment.domain.model.ProviderTransactionStatus
import com.eeseka.lynk.payment.infra.paystack.dto.PaystackAccount
import com.eeseka.lynk.payment.infra.paystack.dto.PaystackCheckout
import com.eeseka.lynk.payment.infra.paystack.dto.PaystackBank
import com.eeseka.lynk.payment.infra.paystack.dto.PaystackOutcome
import com.eeseka.lynk.payment.infra.paystack.dto.PaystackResponse
import com.eeseka.lynk.payment.infra.paystack.dto.PaystackTransactionStatus
import com.eeseka.lynk.payment.infra.paystack.dto.PaystackTransferRecipient
import com.eeseka.lynk.payment.infra.paystack.mappers.toBank
import com.eeseka.lynk.payment.infra.paystack.mappers.toBankAccount
import com.eeseka.lynk.payment.infra.paystack.mappers.toProviderTransaction
import com.eeseka.lynk.payment.infra.paystack.mappers.toProviderTransfer
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.body

@Component
class PaystackClient(
    private val paystackRestClient: RestClient
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Cacheable(
        value = ["paystack_banks"],
        unless = "#result.isEmpty()"
    )
    fun getBanks(): List<Bank> {
        val response = try {
            paystackRestClient.get()
                .uri("/bank?country=nigeria&currency=NGN")
                .retrieve()
                .body<PaystackResponse<List<PaystackBank>>>()
        } catch (e: RestClientException) {
            throw PaystackUnavailableException("Could not reach Paystack to list banks: ${e.message}")
        }

        if (response?.status != true || response.data == null) {
            throw PaystackUnavailableException(response?.message ?: "Paystack returned no bank list")
        }

        return response.data
            .filter { it.active != false }
            .map { it.toBank(logoUrl = null) }
            .sortedBy { it.name }
            .distinctBy { it.code }
    }

    fun initializeCharge(
        email: String,
        amountKobo: Long,
        reference: String,
        hangoutId: HangoutId,
        userId: UserId
    ): String {
        val body = mapOf(
            "email" to email,
            "amount" to amountKobo,
            "reference" to reference,
            "currency" to "NGN",
            "metadata" to mapOf(
                "hangoutId" to hangoutId.toString(),
                "userId" to userId.toString()
            )
        )

        val response = try {
            paystackRestClient.post()
                .uri("/transaction/initialize")
                .body(body)
                .retrieve()
                .onStatus({ it.value() == 401 || it.value() == 403 }) { _, response ->
                    throw PaystackUnavailableException("Paystack rejected our credentials: ${response.statusCode}")
                }
                .onStatus({ it.value() == 429 }) { _, _ ->
                    logger.warn("Paystack throttled charge initialization for payment {}", reference)
                    throw PaystackRateLimitedException("Paystack throttled the charge initialization")
                }
                .onStatus({ it.is4xxClientError }) { _, _ -> }
                .body<PaystackResponse<PaystackCheckout>>()
        } catch (e: RestClientException) {
            throw PaystackUnavailableException("Could not reach Paystack to start the payment: ${e.message}")
        }

        if (response?.status != true || response.data == null) {
            logger.error("Paystack would not start payment {}: {}", reference, response?.message)
            throw PaystackUnavailableException(response?.message ?: "Paystack would not start that payment")
        }

        return response.data.authorizationUrl
    }

    fun resolveAccount(accountNumber: String, bankCode: String): BankAccount {
        val response = try {
            paystackRestClient.get()
                .uri("/bank/resolve?account_number={accountNumber}&bank_code={bankCode}", accountNumber, bankCode)
                .retrieve()
                .onStatus({ it.value() == 401 || it.value() == 403 }) { _, response ->
                    throw PaystackUnavailableException("Paystack rejected our credentials: ${response.statusCode}")
                }
                .onStatus({ it.value() == 429 }) { _, _ ->
                    logger.warn("Paystack throttled account resolution at bank {}", bankCode)
                    throw PaystackRateLimitedException("Paystack throttled the account resolution")
                }
                .onStatus({ it.is4xxClientError }) { _, _ -> }
                .body<PaystackResponse<PaystackAccount>>()
        } catch (e: RestClientException) {
            throw PaystackUnavailableException("Could not reach Paystack to resolve the account: ${e.message}")
        }

        if (response?.status != true || response.data == null) {
            logger.warn("Paystack could not resolve an account at bank {}: {}", bankCode, response?.message)
            throw BankAccountNotResolvedException(response?.message ?: "Paystack could not resolve that account number")
        }

        return response.data.toBankAccount(bankCode)
    }


    fun verifyTransaction(reference: String): ProviderTransaction {
        val response = try {
            paystackRestClient.get()
                .uri("/transaction/verify/{reference}", reference)
                .retrieve()
                .onStatus({ it.value() == 401 || it.value() == 403 }) { _, response ->
                    throw PaystackUnavailableException("Paystack rejected our credentials: ${response.statusCode}")
                }
                .onStatus({ it.is4xxClientError }) { _, _ -> }
                .body<PaystackResponse<PaystackTransactionStatus>>()
        } catch (e: RestClientException) {
            throw PaystackUnavailableException("Could not reach Paystack to verify a payment: ${e.message}")
        }

        val transaction = response?.data
        if (response?.status != true || transaction == null) {
            logger.warn("Could not verify payment {}, treating it as unknown: {}", reference, response?.message)
            return ProviderTransaction(
                status = ProviderTransactionStatus.UNKNOWN,
                paidAmountKobo = null
            )
        }

        return transaction.toProviderTransaction()
    }


    fun createTransferRecipient(bankAccount: BankAccount): String {
        val body = mapOf(
            "type" to "nuban",
            "name" to bankAccount.accountName,
            "account_number" to bankAccount.accountNumber,
            "bank_code" to bankAccount.bankCode,
            "currency" to "NGN"
        )

        val response = try {
            paystackRestClient.post()
                .uri("/transferrecipient")
                .body(body)
                .retrieve()
                .onStatus({ it.value() == 401 || it.value() == 403 }) { _, response ->
                    throw PaystackUnavailableException("Paystack rejected our credentials: ${response.statusCode}")
                }
                .onStatus({ it.is4xxClientError }) { _, _ -> }
                .body<PaystackResponse<PaystackTransferRecipient>>()
        } catch (e: RestClientException) {
            throw PaystackUnavailableException("Could not reach Paystack to save the payout account: ${e.message}")
        }

        if (response?.status != true || response.data == null) {
            logger.error(
                "Paystack would not accept a payout account at bank {}: {}",
                bankAccount.bankCode,
                response?.message
            )
            throw BankAccountNotResolvedException(
                response?.message ?: "Paystack would not accept that account for payouts"
            )
        }

        return response.data.recipientCode
    }

    fun transfer(recipientCode: String, amountKobo: Long, reference: String, reason: String) {
        val body = mapOf(
            "source" to "balance",
            "recipient" to recipientCode,
            "amount" to amountKobo,
            "reference" to reference,
            "reason" to reason
        )

        val response = try {
            paystackRestClient.post()
                .uri("/transfer")
                .body(body)
                .retrieve()
                .onStatus({ it.value() == 401 || it.value() == 403 }) { _, response ->
                    throw PaystackUnavailableException("Paystack rejected our credentials: ${response.statusCode}")
                }
                .onStatus({ it.is4xxClientError }) { _, _ -> }
                .body<PaystackOutcome>()
        } catch (e: RestClientException) {
            throw PaystackUnavailableException("Could not reach Paystack to pay the host: ${e.message}")
        }

        if (response?.status != true) {
            logger.error("Paystack refused transfer {}: {}", reference, response?.message)
            throw PayoutRejectedException(response?.message ?: "Paystack refused the transfer")
        }
    }

    fun verifyTransfer(reference: String): ProviderTransaction {
        val response = try {
            paystackRestClient.get()
                .uri("/transfer/verify/{reference}", reference)
                .retrieve()
                .onStatus({ it.value() == 401 || it.value() == 403 }) { _, response ->
                    throw PaystackUnavailableException("Paystack rejected our credentials: ${response.statusCode}")
                }
                .onStatus({ it.is4xxClientError }) { _, _ -> }
                .body<PaystackResponse<PaystackTransactionStatus>>()
        } catch (e: RestClientException) {
            throw PaystackUnavailableException("Could not reach Paystack to verify a payout: ${e.message}")
        }

        val transfer = response?.data
        if (response?.status != true || transfer == null) {
            logger.warn("Could not verify payout {}, treating it as unknown: {}", reference, response?.message)
            return ProviderTransaction(
                status = ProviderTransactionStatus.UNKNOWN,
                paidAmountKobo = null
            )
        }

        return transfer.toProviderTransfer()
    }

    fun refund(transactionReference: String, netAmountKobo: Long) {
        val body = mapOf(
            "transaction" to transactionReference,
            "amount" to netAmountKobo
        )

        val response = try {
            paystackRestClient.post()
                .uri("/refund")
                .body(body)
                .retrieve()
                .onStatus({ it.value() == 401 || it.value() == 403 }) { _, response ->
                    throw PaystackUnavailableException("Paystack rejected our credentials: ${response.statusCode}")
                }
                .onStatus({ it.is4xxClientError }) { _, _ -> }
                .body<PaystackOutcome>()
        } catch (e: RestClientException) {
            throw PaystackUnavailableException("Could not reach Paystack to refund: ${e.message}")
        }

        if (response?.status != true) {
            logger.error("Paystack would not refund {}: {}", transactionReference, response?.message)
            throw RefundRejectedException(response?.message ?: "Paystack would not accept that refund")
        }
    }
}