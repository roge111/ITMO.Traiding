package com.trading.trading

import kotlinx.serialization.Serializable

@Serializable
data class AccountSummary(
    val username: String,
    val balance: Double,
    val portfolioValue: Double,
    val totalAssets: Double
)

@Serializable
data class Holding(
    val quoteName: String,
    val quantity: Int,
    val avgPrice: Double,
    val currentPrice: Double,
    val marketValue: Double,
    val profit: Double
)

@Serializable
data class TradeResult(
    val message: String,
    val balance: Double,
    val holding: Holding?
)

@Serializable
data class PricePoint(
    val price: Double,
    val timestamp: String
)

@Serializable
data class Candle(
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val timestamp: String
)
