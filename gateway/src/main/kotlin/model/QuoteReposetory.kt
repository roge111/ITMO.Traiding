package com.trading.model

object QuoteRepository {
    private val quotes = mutableListOf(
        Quote("YANDEX", 100.0, -50, 70, 200),
        Quote("GAZ", 200.0, 60, 40, 300)
    )

    fun getAll(): List<Quote> = quotes.toList()
}