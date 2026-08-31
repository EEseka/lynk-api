package com.eeseka.lynk.notification.service.util

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertEquals

class MoneyFormatterTest {

    @ParameterizedTest(name = "{0} kobo reads as {1}")
    @CsvSource(
        // Whole naira drop the kobo entirely rather than showing a bare .00
        "0, ₦0",
        "100, ₦1",
        "1000000, '₦10,000'",
        // Kobos are padded
        "5, ₦0.05",
        "150, ₦1.50",
        "99999, ₦999.99",
        // Grouping starts at four digits and repeats every three
        "100000, '₦1,000'",
        "123456789, '₦1,234,567.89'",
    )
    fun `formats kobo as naira`(kobo: Long, expected: String) {
        assertEquals(expected, kobo.toNairaString())
    }
}
