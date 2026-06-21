package com.mykart.payment.enums

enum class Channel(val maxAmountInr: Long?) {
    UPI(100_000L),    // ≤₹1L, free
    IMPS(500_000L),   // ≤₹5L, ₹7 fee
    NEFT(null);       // no limit, ₹3 fee

    fun supportsAmount(amountInr: Long): Boolean = maxAmountInr == null || amountInr <= maxAmountInr
}
