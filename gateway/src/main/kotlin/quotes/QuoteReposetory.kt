package com.trading.quotes

import com.trading.database.DataBaseManager
import java.sql.ResultSet

object QuoteRepository {
    private val quotes = mutableListOf<Quote>()
    private val db = DataBaseManager()

    init {
        db.connect()
    }

    fun getAll(): List<Quote> = quotes.toList()

    fun getQuotesDB(): List<Quote> {
        quotes.clear()  // очищаем старые данные
        val rs = db.query("SELECT * FROM quotes")
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
        rs.close()
        return quotes.toList()
    }
}