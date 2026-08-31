package com.eeseka.lynk.support

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.hangout.domain.model.HangoutStatus
import com.eeseka.lynk.hangout.domain.model.HangoutVibe
import com.eeseka.lynk.hangout.domain.model.PaymentState
import com.eeseka.lynk.hangout.domain.model.RsvpStatus
import com.eeseka.lynk.hangout.infra.database.entities.HangoutEntity
import com.eeseka.lynk.hangout.infra.database.entities.HangoutParticipantEntity
import com.eeseka.lynk.hangout.infra.database.entities.HangoutPaymentEntity
import com.eeseka.lynk.hangout.infra.database.entities.HangoutUserEntity
import com.eeseka.lynk.hangout.infra.database.repositories.HangoutParticipantRepository
import com.eeseka.lynk.hangout.infra.database.repositories.HangoutRepository
import com.eeseka.lynk.hangout.infra.database.repositories.HangoutUserRepository
import com.eeseka.lynk.payment.domain.model.PaymentStatus
import com.eeseka.lynk.payment.domain.model.RefundStatus
import com.eeseka.lynk.payment.infra.database.entities.HangoutPayoutAccountEntity
import com.eeseka.lynk.payment.infra.database.entities.PaymentEntity
import com.eeseka.lynk.payment.infra.database.repositories.HangoutPayoutAccountRepository
import com.eeseka.lynk.payment.infra.database.repositories.PaymentRepository
import org.springframework.boot.test.context.TestComponent
import org.springframework.jdbc.core.JdbcTemplate
import java.sql.Timestamp
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

/**
 * Rows for tests to act on, written the short way.
 *
 * Every method saves what it builds, because a test that arranges a hangout is interested in the
 * hangout, not in which repository holds which half of it. Defaults describe the ordinary case, so
 * a test names only the thing it is actually about.
 */
@TestComponent
class TestFixtures(
    private val hangoutUserRepository: HangoutUserRepository,
    private val hangoutRepository: HangoutRepository,
    private val hangoutParticipantRepository: HangoutParticipantRepository,
    private val paymentRepository: PaymentRepository,
    private val hangoutPayoutAccountRepository: HangoutPayoutAccountRepository,
    private val jdbcTemplate: JdbcTemplate
) {

    fun user(displayName: String = "Ada"): HangoutUserEntity {
        val userId = UUID.randomUUID()

        return hangoutUserRepository.save(
            HangoutUserEntity(
                userId = userId,
                email = "$userId@lynk.test",
                username = "user_${userId.toString().take(8)}",
                displayName = displayName
            )
        )
    }

    fun hangout(
        host: HangoutUserEntity,
        status: HangoutStatus = HangoutStatus.SCHEDULED,
        costPerPersonKobo: Long? = 500_000L,
        paymentState: PaymentState = PaymentState.COLLECTING,
        splitHeadcount: Int = 2,
        scheduledAt: Instant = Instant.now().plus(3, ChronoUnit.DAYS),
        paymentDeadline: Instant = Instant.now().plus(2, ChronoUnit.DAYS),
        participantCount: Int = 1
    ): HangoutEntity = hangoutRepository.save(
        HangoutEntity(
            hostId = host.userId,
            name = "Sunday jollof",
            vibe = HangoutVibe.FOOD,
            status = status,
            scheduledAt = scheduledAt,
            maxAttendees = 10,
            chosenSpotId = null,
            participantCount = participantCount,
            payment = costPerPersonKobo?.let {
                HangoutPaymentEntity(
                    totalCostKobo = it * splitHeadcount,
                    costPerPersonKobo = it,
                    splitHeadcount = splitHeadcount,
                    deadline = paymentDeadline,
                    state = paymentState
                )
            }
        )
    )

    fun participant(
        hangout: HangoutEntity,
        user: HangoutUserEntity,
        rsvpStatus: RsvpStatus = RsvpStatus.ATTENDING,
        hasPaid: Boolean = false
    ): HangoutParticipantEntity = hangoutParticipantRepository.save(
        HangoutParticipantEntity(
            hangout = hangout,
            hangoutUser = user,
            rsvpStatus = rsvpStatus,
            hasPaid = hasPaid
        )
    )

    fun payment(
        hangoutId: HangoutId,
        userId: UserId,
        amountKobo: Long = 500_000L,
        status: PaymentStatus = PaymentStatus.PENDING,
        refundStatus: RefundStatus = RefundStatus.NONE,
        paidAt: Instant? = null,
        reference: String = "lynk-${UUID.randomUUID()}"
    ): PaymentEntity = paymentRepository.save(
        PaymentEntity(
            hangoutId = hangoutId,
            userId = userId,
            reference = reference,
            amountKobo = amountKobo,
            netAmountKobo = amountKobo,
            status = status,
            refundStatus = refundStatus,
            paidAt = paidAt
        )
    )

    fun payoutAccount(
        hangout: HangoutEntity,
        host: HangoutUserEntity,
        recipientCode: String = "RCP_test_recipient",
        transferReference: String? = null
    ): HangoutPayoutAccountEntity = hangoutPayoutAccountRepository.save(
        HangoutPayoutAccountEntity(
            hangoutId = hangout.id!!,
            hostId = host.userId,
            recipientCode = recipientCode,
            bankName = "Test Bank",
            accountNumberLast4 = "1234",
            accountHolderName = host.displayName,
            transferReference = transferReference
        )
    )

    /**
     * Moves a payment's timestamps into the past.
     *
     * Hibernate writes `createdAt` and `updatedAt` itself, so a row cannot be born old. The sweeps
     * all key off how long something has been sitting there, and none of them would look at a row
     * made a moment ago — so the aging has to happen in SQL, after the fact.
     */
    fun agePayment(
        payment: PaymentEntity,
        createdAt: Instant? = null,
        updatedAt: Instant? = null,
        paidAt: Instant? = null
    ) {
        jdbcTemplate.update(
            """
            UPDATE payment_service.payments
            SET created_at = COALESCE(?, created_at),
                updated_at = COALESCE(?, updated_at),
                paid_at = COALESCE(?, paid_at)
            WHERE reference = ?
            """.trimIndent(),
            createdAt?.let { Timestamp.from(it) },
            updatedAt?.let { Timestamp.from(it) },
            paidAt?.let { Timestamp.from(it) },
            payment.reference
        )
    }

    /** Same reasoning as [agePayment]: the stuck-payout report only looks at old rows. */
    fun agePayoutAccount(payoutAccount: HangoutPayoutAccountEntity, updatedAt: Instant) {
        jdbcTemplate.update(
            "UPDATE payment_service.hangout_payout_accounts SET updated_at = ? WHERE hangout_id = ?",
            Timestamp.from(updatedAt),
            payoutAccount.hangoutId
        )
    }

    /** Moves a payment deadline, including in the past, which the API itself will not do. */
    fun movePaymentDeadline(hangoutId: HangoutId, deadline: Instant) {
        jdbcTemplate.update(
            "UPDATE hangout_service.hangouts SET payment_deadline = ? WHERE id = ?",
            Timestamp.from(deadline),
            hangoutId
        )
    }

    /** Ages an account for the nightly sweep that clears guests nobody came back to. */
    fun ageUser(userId: UserId, createdAt: Instant) {
        jdbcTemplate.update(
            "UPDATE user_service.users SET created_at = ? WHERE id = ?",
            Timestamp.from(createdAt),
            userId
        )
    }

    /** Pushes every one of somebody's refresh tokens past its expiry. */
    fun expireRefreshTokens(userId: UserId, expiresAt: Instant) {
        jdbcTemplate.update(
            "UPDATE user_service.refresh_tokens SET expires_at = ? WHERE user_id = ?",
            Timestamp.from(expiresAt),
            userId
        )
    }

    /** Ages a notification, for the nightly sweep that clears read ones after 90 days. */
    fun ageNotifications(userId: UserId, createdAt: Instant) {
        jdbcTemplate.update(
            "UPDATE notification_service.notifications SET created_at = ? WHERE user_id = ?",
            Timestamp.from(createdAt),
            userId
        )
    }
}