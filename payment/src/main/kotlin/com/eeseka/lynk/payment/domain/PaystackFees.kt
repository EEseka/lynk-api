package com.eeseka.lynk.payment.domain

import kotlin.math.ceil

/**
 * Paystack's cut on a card charge: 1.5% of the charge, plus a flat ₦100 once the charge reaches
 * ₦2,500, and never more than ₦2,000 in total.
 */
object PaystackFees {
    private const val PERCENTAGE_RATE = 0.015
    private const val FLAT_FEE_KOBO = 10_000L
    private const val FLAT_FEE_THRESHOLD_KOBO = 250_000L
    private const val FEE_CAP_KOBO = 200_000L

    /**
     * Given what the host must receive, returns what to charge the card so exactly that many lands.
     *
     * Whether the ₦100 applies is decided by the charge, not by the host's share, so a share just
     * under ₦2,500 can cross the line once the fee sits on top of it. That is why the charge is
     * worked out twice — first without the ₦100, then again with it if the first answer crossed.
     */
    fun grossUpKobo(netKobo: Long): Long {
        val withoutFlatFee = chargeCoveringPercentageFee(netKobo)

        val charge = if (withoutFlatFee >= FLAT_FEE_THRESHOLD_KOBO) {
            chargeCoveringPercentageFee(netKobo + FLAT_FEE_KOBO)
        } else {
            withoutFlatFee
        }

        // Paystack stops charging above the cap, so above it the fee is a flat amount.
        return if (charge - netKobo > FEE_CAP_KOBO) netKobo + FEE_CAP_KOBO else charge
    }

    /**
     * Paystack takes its 1.5% out of the charge, so the charge is [amountKobo] divided by the share
     * that survives the fee.
     */
    private fun chargeCoveringPercentageFee(amountKobo: Long): Long =
        ceil(amountKobo / (1 - PERCENTAGE_RATE)).toLong()
}