package com.eeseka.lynk.payment.infra.database.mappers

import com.eeseka.lynk.payment.domain.model.PayoutAccount
import com.eeseka.lynk.payment.infra.database.entities.HangoutPayoutAccountEntity

fun HangoutPayoutAccountEntity.toPayoutAccount(): PayoutAccount {
    return PayoutAccount(
        bankName = bankName,
        accountNumberLast4 = accountNumberLast4,
        accountHolderName = accountHolderName
    )
}
