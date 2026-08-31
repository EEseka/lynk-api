package com.eeseka.lynk.payment.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import kotlin.math.ceil
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PaystackFeesTest {

    @ParameterizedTest(name = "a host share of {0} kobo is charged {1} kobo")
    @CsvSource(
        // Under ₦2,500 the charge carries the 1.5% only.
        "0, 0",
        "1, 2",
        "100000, 101523",
        // Over it the ₦100 joins the 1.5%.
        "250000, 263960",
        "2000000, 2040610",
    )
    fun `charges the host share plus Paystack's cut`(netKobo: Long, expectedChargeKobo: Long) {
        assertEquals(expectedChargeKobo, PaystackFees.grossUpKobo(netKobo))
    }

    @Test
    fun `adds the flat fee only once the charge itself crosses the threshold`() {
        assertEquals(249_999L, PaystackFees.grossUpKobo(246_249L))
        assertEquals(260_153L, PaystackFees.grossUpKobo(246_250L))
    }

    @Test
    fun `stops adding fee once the cut reaches the cap`() {
        // ₦125,000 is charged exactly ₦2,000 in fees, which is the cap but has not passed it.
        assertEquals(12_700_000L, PaystackFees.grossUpKobo(12_500_000L))

        // ₦200,000 would owe more than the cap, so the fee flattens to ₦2,000.
        assertEquals(20_200_000L, PaystackFees.grossUpKobo(20_000_000L))
    }

    @ParameterizedTest(name = "a host share of {0} kobo survives Paystack's cut")
    @ValueSource(longs = [1, 50_000, 100_000, 246_249, 246_250, 249_999, 2_000_000, 12_500_000, 20_000_000])
    fun `leaves the host with at least the share they were promised`(netKobo: Long) {
        val charge = PaystackFees.grossUpKobo(netKobo)

        val landed = charge - paystackFeeKobo(charge)

        assertTrue(
            landed >= netKobo,
            "charging $charge kobo left the host $landed kobo, short of $netKobo"
        )
    }

    /**
     * Paystack's local card pricing: 1.5% of the charge, plus ₦100 once the charge reaches ₦2,500,
     * capped at ₦2,000. Rounded up, because a fee rounded in our favor would hide a shortfall.
     */
    private fun paystackFeeKobo(chargeKobo: Long): Long {
        val percentage = ceil(chargeKobo * 0.015).toLong()
        val flat = if (chargeKobo >= 250_000L) 10_000L else 0L

        return minOf(percentage + flat, 200_000L)
    }
}
