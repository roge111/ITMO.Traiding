package com.trading.android

data class Quote(
    val name: String,
    val price: Double,
    val percentageChange: Double,
    val minCost: Long,
    val maxCost: Long
)