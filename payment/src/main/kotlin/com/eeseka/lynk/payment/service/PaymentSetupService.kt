package com.eeseka.lynk.payment.service

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.hangout.service.HangoutService
import com.eeseka.lynk.payment.domain.model.BankAccount
import com.eeseka.lynk.payment.domain.model.PaymentSettings
import com.eeseka.lynk.payment.infra.database.entities.HangoutPayoutAccountEntity
import com.eeseka.lynk.payment.infra.database.repositories.HangoutPayoutAccountRepository
import com.eeseka.lynk.payment.infra.paystack.PaystackClient
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class PaymentSetupService(
    private val paystackClient: PaystackClient,
    private val bankAccountService: BankAccountService,
    private val hangoutPayoutAccountRepository: HangoutPayoutAccountRepository,
    private val hangoutService: HangoutService
) {

    /**
     * Deliberately not transactional as a whole: three of the steps below are calls out to Paystack,
     * and holding a database connection open across them would tie up the pool for as long as Paystack
     * takes to answer. Each step that writes is transactional on its own.
     */
    fun enablePayments(
        hostId: UserId,
        hangoutId: HangoutId,
        totalCostKobo: Long,
        paymentDeadline: Instant,
        accountNumber: String,
        bankCode: String
    ): PaymentSettings {
        hangoutService.assertPaymentsCanBeEnabled(
            hostId = hostId,
            hangoutId = hangoutId,
            totalCostKobo = totalCostKobo,
            paymentDeadline = paymentDeadline
        )

        val bankAccount = bankAccountService.resolveAccount(
            accountNumber = accountNumber,
            bankCode = bankCode
        )

        val recipientCode = paystackClient.createTransferRecipient(bankAccount)

        val bankName = bankAccountService.getBanks()
            .firstOrNull { it.code == bankAccount.bankCode }
            ?.name

        val payoutAccount = savePayoutAccount(
            hangoutId = hangoutId,
            hostId = hostId,
            recipientCode = recipientCode,
            bankName = bankName,
            bankAccount = bankAccount
        )

        // Last, because this is what tells everyone payments are on. If anything above had failed, there
        // would be nothing to take back.
        val costPerPersonKobo = hangoutService.enablePayments(
            hostId = hostId,
            hangoutId = hangoutId,
            totalCostKobo = totalCostKobo,
            paymentDeadline = paymentDeadline
        )

        return PaymentSettings(
            hangoutId = hangoutId,
            totalCostKobo = totalCostKobo,
            costPerPersonKobo = costPerPersonKobo,
            paymentDeadline = paymentDeadline,
            bankName = payoutAccount.bankName,
            accountNumberLast4 = payoutAccount.accountNumberLast4,
            accountHolderName = payoutAccount.accountHolderName
        )
    }

    /**
     * Updates the row rather than always inserting, because a hangout holds at most one payout account
     * and an earlier attempt that got this far but no further would otherwise block every retry on a
     * duplicate hangout id.
     */
    private fun savePayoutAccount(
        hangoutId: HangoutId,
        hostId: UserId,
        recipientCode: String,
        bankName: String?,
        bankAccount: BankAccount
    ): HangoutPayoutAccountEntity {
        val existing = hangoutPayoutAccountRepository.findByHangoutId(hangoutId)

        val payoutAccount = existing?.apply {
            this.hostId = hostId
            this.recipientCode = recipientCode
            this.bankName = bankName
            this.accountNumberLast4 = bankAccount.accountNumber.takeLast(4)
            this.accountHolderName = bankAccount.accountName
        } ?: HangoutPayoutAccountEntity(
            hangoutId = hangoutId,
            hostId = hostId,
            recipientCode = recipientCode,
            bankName = bankName,
            accountNumberLast4 = bankAccount.accountNumber.takeLast(4),
            accountHolderName = bankAccount.accountName
        )

        return hangoutPayoutAccountRepository.save(payoutAccount)
    }
}
