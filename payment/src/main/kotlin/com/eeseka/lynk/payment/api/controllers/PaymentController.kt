package com.eeseka.lynk.payment.api.controllers

import com.eeseka.lynk.common.api.config.UserRateLimit
import com.eeseka.lynk.common.api.util.requestUserId
import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.payment.api.dto.BankAccountDto
import com.eeseka.lynk.payment.api.dto.BankDto
import com.eeseka.lynk.payment.api.dto.ChangeDeadlineRequest
import com.eeseka.lynk.payment.api.dto.DeadlineDecisionRequest
import com.eeseka.lynk.payment.api.dto.EnablePaymentsRequest
import com.eeseka.lynk.payment.api.dto.PaymentInitializationDto
import com.eeseka.lynk.payment.api.dto.PaymentSettingsDto
import com.eeseka.lynk.payment.api.dto.PaymentVerificationDto
import com.eeseka.lynk.payment.api.mappers.toBankAccountDto
import com.eeseka.lynk.payment.api.mappers.toBankDto
import com.eeseka.lynk.payment.api.mappers.toPaymentInitializationDto
import com.eeseka.lynk.payment.api.mappers.toPaymentSettingsDto
import com.eeseka.lynk.payment.service.BankAccountService
import com.eeseka.lynk.payment.service.PaymentDeadlineService
import com.eeseka.lynk.payment.service.PaymentInitiationService
import com.eeseka.lynk.payment.service.PaymentSetupService
import com.eeseka.lynk.payment.service.PaymentVerificationService
import com.eeseka.lynk.payment.service.PayoutService
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.TimeUnit

@Validated
@RestController
@RequestMapping("/api/payments")
class PaymentController(
    private val bankAccountService: BankAccountService,
    private val paymentSetupService: PaymentSetupService,
    private val paymentInitiationService: PaymentInitiationService,
    private val paymentVerificationService: PaymentVerificationService,
    private val paymentDeadlineService: PaymentDeadlineService,
    private val payoutService: PayoutService
) {
    @GetMapping("/banks")
    fun getBanks(): List<BankDto> {
        return bankAccountService.getBanks().map { it.toBankDto() }
    }

    @GetMapping("/bank-account")
    @UserRateLimit(
        requests = 5,
        duration = 1L,
        unit = TimeUnit.HOURS
    )
    fun resolveBankAccount(
        @RequestParam @Pattern(regexp = "\\d{10}", message = "An account number must be 10 digits")
        accountNumber: String,
        @RequestParam @NotBlank
        bankCode: String
    ): BankAccountDto {
        return bankAccountService.resolveAccount(
            accountNumber = accountNumber,
            bankCode = bankCode
        ).toBankAccountDto()
    }

    @PatchMapping("/hangouts/{hangoutId}")
    fun enablePayments(
        @PathVariable hangoutId: HangoutId,
        @Valid @RequestBody body: EnablePaymentsRequest
    ): PaymentSettingsDto {
        return paymentSetupService.enablePayments(
            hostId = requestUserId,
            hangoutId = hangoutId,
            totalCostKobo = body.totalCostKobo,
            paymentDeadline = body.paymentDeadline,
            accountNumber = body.accountNumber,
            bankCode = body.bankCode
        ).toPaymentSettingsDto()
    }

    @PostMapping("/hangouts/{hangoutId}/initialize")
    fun initializePayment(
        @PathVariable hangoutId: HangoutId
    ): PaymentInitializationDto {
        return paymentInitiationService.initializePayment(
            userId = requestUserId,
            hangoutId = hangoutId
        ).toPaymentInitializationDto()
    }

    @PostMapping("/hangouts/{hangoutId}/verify")
    fun verifyPayment(
        @PathVariable hangoutId: HangoutId
    ): PaymentVerificationDto {
        return PaymentVerificationDto(
            status = paymentVerificationService.verifyLatestPayment(
                userId = requestUserId,
                hangoutId = hangoutId
            )
        )
    }

    @PatchMapping("/hangouts/{hangoutId}/deadline")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun changeDeadline(
        @PathVariable hangoutId: HangoutId,
        @Valid @RequestBody body: ChangeDeadlineRequest
    ) {
        paymentDeadlineService.changeDeadline(
            hostId = requestUserId,
            hangoutId = hangoutId,
            newDeadline = body.newDeadline
        )
    }

    @PostMapping("/hangouts/{hangoutId}/deadline-decision")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun decideAtDeadline(
        @PathVariable hangoutId: HangoutId,
        @Valid @RequestBody body: DeadlineDecisionRequest
    ) {
        paymentDeadlineService.applyDeadlineDecision(
            hostId = requestUserId,
            hangoutId = hangoutId,
            decision = body.decision,
            newDeadline = body.newDeadline
        )
    }

    @PostMapping("/hangouts/{hangoutId}/retry-payout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun retryPayout(
        @PathVariable hangoutId: HangoutId
    ) {
        payoutService.retryPayout(
            hostId = requestUserId,
            hangoutId = hangoutId
        )
    }
}