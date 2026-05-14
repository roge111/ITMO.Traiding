package com.trading.quotes

import com.trading.database.ClickHouseManager

object QuoteRepository {
    private val quotes = mutableListOf<Quote>()

    fun getAll(): List<Quote> = quotes.toList()

    fun getQuotesDB(): List<Quote> {
        quotes.clear()
        val sql = """
            SELECT
                quote_name,
                last_cost,
                min_cost,
                max_cost,
                percentage_change,
                created_at,
                updated_at
            FROM quotes FINAL
            ORDER BY quote_name
        """.trimIndent()

        ClickHouseManager.getConnection().use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.executeQuery().use { rs ->
                    while (rs.next()) {
                        val quote = Quote(
                            name = rs.getString("quote_name"),
                            price = rs.getDouble("last_cost"),
                            minCost = rs.getLong("min_cost"),
                            maxCost = rs.getLong("max_cost"),
                            percentageChange = rs.getDouble("percentage_change")
                        )
                        quotes.add(quote)
                    }
                }
            }
        }
        return quotes.toList()
    }
}