package com.eeseka.lynk.notification.service.util

// Paystack settles in naira, so this is the currency of the money itself, not of the device.
const val NAIRA_SYMBOL = "₦"

fun Long.toNairaString(): String {
    val naira = this / 100
    val kobo = this % 100

    val groupedNaira = naira.toString()
        .reversed()
        .chunked(3)
        .joinToString(",")
        .reversed()

    val koboPart = if (kobo == 0L) "" else ".${kobo.toString().padStart(2, '0')}"

    return "$NAIRA_SYMBOL$groupedNaira$koboPart"
}