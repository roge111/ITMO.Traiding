package com.trading.trading

import com.trading.database.ClickHouseManager
import java.sql.Connection
import java.sql.DriverManager
import kotlin.math.roundToInt
import kotlin.random.Random

object TradingRepository {
    private val postgresUrl = System.getenv("POSTGRES_JDBC_URL")?.takeIf { it.isNotBlank() }
        ?: "jdbc:postgresql://localhost:5432/itmo_traiding_system"
    private val postgresUser = System.getenv("POSTGRES_USER")?.takeIf { it.isNotBlank() } ?: "postgres"
    private val postgresPassword = System.getenv("POSTGRES_PASSWORD") ?: "postgres"

    private fun postgresConnection(): Connection =
        DriverManager.getConnection(postgresUrl, postgresUser, postgresPassword)

    fun account(username: String): AccountSummary {
        var balance: Double? = null
        postgresConnection().use { conn ->
            conn.usePrepared(
                "SELECT balance FROM users WHERE username = ?",
                username
            ) { rs ->
                if (!rs.next()) error("User not found")
                balance = rs.getDouble("balance")
            }
        }
        val actualBalance = balance ?: error("User not found")
        val portfolioValue = portfolio(username).sumOf { it.marketValue }
        return AccountSummary(username, actualBalance, portfolioValue, actualBalance + portfolioValue)
    }

    fun portfolio(username: String): List<Holding> {
        val prices = currentPrices()
        val result = mutableListOf<Holding>()
        postgresConnection().use { conn ->
            conn.usePrepared(
                """
                SELECT p.quote_name, p.quantity, p.avg_price
                FROM portfolio p
                JOIN users u ON u.user_id = p.user_id
                WHERE u.username = ? AND p.quantity > 0
                ORDER BY p.quote_name
                """.trimIndent(),
                username
            ) { rs ->
                while (rs.next()) {
                    val name = rs.getString("quote_name")
                    val quantity = rs.getInt("quantity")
                    val avgPrice = rs.getDouble("avg_price")
                    val currentPrice = prices[name] ?: avgPrice
                    result.add(
                        Holding(
                            quoteName = name,
                            quantity = quantity,
                            avgPrice = avgPrice,
                            currentPrice = currentPrice,
                            marketValue = currentPrice * quantity,
                            profit = (currentPrice - avgPrice) * quantity
                        )
                    )
                }
            }
        }
        return result
    }

    fun trade(username: String, quoteName: String, quantity: Int, side: String): TradeResult {
        require(quantity > 0) { "Quantity must be positive" }
        val normalizedSide = side.uppercase()
        require(normalizedSide == "BUY" || normalizedSide == "SELL") { "Side must be BUY or SELL" }

        val price = currentPrice(quoteName) ?: error("Quote $quoteName not found")
        val total = price * quantity

        val conn = postgresConnection()
        conn.autoCommit = false
        try {
            val user = conn.queryOne(
                "SELECT user_id, balance FROM users WHERE username = ? FOR UPDATE",
                username
            ) ?: error("User not found")
            val userId = user.getInt("user_id")
            val balance = user.getDouble("balance")
            val currentHolding = conn.queryOne(
                "SELECT quantity, avg_price FROM portfolio WHERE user_id = ? AND quote_name = ? FOR UPDATE",
                userId,
                quoteName
            )
            val oldQuantity = currentHolding?.getInt("quantity") ?: 0
            val oldAvgPrice = currentHolding?.getDouble("avg_price") ?: 0.0

            val (newBalance, newQuantity, newAvgPrice) = if (normalizedSide == "BUY") {
                if (balance < total) error("Not enough balance")
                val updatedQuantity = oldQuantity + quantity
                val updatedAvg = ((oldAvgPrice * oldQuantity) + total) / updatedQuantity
                Triple(balance - total, updatedQuantity, updatedAvg)
            } else {
                if (oldQuantity < quantity) error("Not enough shares")
                Triple(balance + total, oldQuantity - quantity, oldAvgPrice)
            }

            conn.executeUpdate(
                "UPDATE users SET balance = ? WHERE user_id = ?",
                newBalance,
                userId
            )
            conn.executeUpdate(
                """
                INSERT INTO portfolio (user_id, quote_name, quantity, avg_price, updated_at)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT (user_id, quote_name)
                DO UPDATE SET quantity = EXCLUDED.quantity,
                              avg_price = EXCLUDED.avg_price,
                              updated_at = CURRENT_TIMESTAMP
                """.trimIndent(),
                userId,
                quoteName,
                newQuantity,
                if (newQuantity == 0) 0.0 else newAvgPrice
            )
            conn.executeUpdate(
                """
                INSERT INTO trades (user_id, quote_name, side, quantity, price, total)
                VALUES (?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                userId,
                quoteName,
                normalizedSide,
                quantity,
                price,
                total
            )
            conn.commit()

            val impactedPrice = applyTradeImpact(quoteName, normalizedSide, quantity)
            val holding = if (newQuantity > 0) {
                Holding(
                    quoteName = quoteName,
                    quantity = newQuantity,
                    avgPrice = newAvgPrice,
                    currentPrice = impactedPrice,
                    marketValue = impactedPrice * newQuantity,
                    profit = (impactedPrice - newAvgPrice) * newQuantity
                )
            } else {
                null
            }
            return TradeResult("$normalizedSide completed", newBalance, holding)
        } catch (e: Exception) {
            conn.rollback()
            throw e
        } finally {
            conn.autoCommit = true
            conn.close()
        }
    }

    fun marketTick(quoteName: String? = null) {
        val names = if (quoteName != null) listOf(quoteName) else currentPrices().keys.toList()
        names.forEach { name ->
            val drift = Random.nextDouble(-0.006, 0.006)
            appendMarketPrice(name, drift)
        }
    }

    fun candles(quoteName: String): List<Candle> {
        val rows = quoteHistoryRows(quoteName)
        if (rows.isEmpty()) return emptyList()

        return if (rows.size == 1) {
            val row = rows.single()
            val spread = (row.last * 0.01).coerceAtLeast(1.0)
            listOf(Candle(row.last - spread, row.last + spread, row.last - spread, row.last, row.timestamp))
        } else {
            rows.zipWithNext().flatMap { (from, to) ->
                buildList {
                    val steps = 4
                    val delta = to.last - from.last
                    for (step in 0 until steps) {
                        val startRatio = step.toDouble() / steps
                        val endRatio = (step + 1).toDouble() / steps
                        val open = from.last + delta * startRatio
                        val close = from.last + delta * endRatio
                        val wick = (kotlin.math.abs(delta) * 0.18 + close * 0.002).coerceAtLeast(1.0)
                        add(
                            Candle(
                                open = open,
                                high = maxOf(open, close) + wick,
                                low = minOf(open, close) - wick,
                                close = close,
                                timestamp = to.timestamp
                            )
                        )
                    }
                }
            }
        }
    }

    fun history(quoteName: String): List<PricePoint> {
        return quoteHistoryRows(quoteName).map {
            PricePoint(price = it.last, timestamp = it.timestamp)
        }
    }

    private fun quoteHistoryRows(quoteName: String): List<QuoteHistoryRow> {
        val sql = """
            SELECT price, happened_at, version
            FROM quotes_history
            WHERE quote_name = ?
            ORDER BY version DESC
            LIMIT 30
        """.trimIndent()
        val rows = mutableListOf<QuoteHistoryRow>()
        ClickHouseManager.getConnection().use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setString(1, quoteName)
                ps.executeQuery().use { rs ->
                    while (rs.next()) {
                        val last = rs.getDouble("price")
                        rows.add(
                            QuoteHistoryRow(
                                last = last,
                                timestamp = rs.getTimestamp("happened_at").toString()
                            )
                        )
                    }
                }
            }
        }
        return rows.reversed()
    }

    private fun currentPrice(quoteName: String): Double? {
        ClickHouseManager.getConnection().use { conn ->
            conn.prepareStatement(
                "SELECT last_cost FROM quotes FINAL WHERE quote_name = ? LIMIT 1"
            ).use { ps ->
                ps.setString(1, quoteName)
                ps.executeQuery().use { rs ->
                    return if (rs.next()) rs.getDouble("last_cost") else null
                }
            }
        }
    }

    private fun currentPrices(): Map<String, Double> {
        val prices = mutableMapOf<String, Double>()
        ClickHouseManager.getConnection().use { conn ->
            conn.prepareStatement("SELECT quote_name, last_cost FROM quotes FINAL").use { ps ->
                ps.executeQuery().use { rs ->
                    while (rs.next()) prices[rs.getString("quote_name")] = rs.getDouble("last_cost")
                }
            }
        }
        return prices
    }

    private fun applyTradeImpact(quoteName: String, side: String, quantity: Int): Double {
        val direction = if (side == "BUY") 1 else -1
        val impact = (0.002 + quantity.coerceAtMost(50) * 0.00015) * direction
        return appendMarketPrice(quoteName, impact)
    }

    private fun appendMarketPrice(quoteName: String, relativeChange: Double): Double {
        ClickHouseManager.getConnection().use { conn ->
            conn.prepareStatement(
                """
                SELECT last_cost, min_cost, max_cost, created_at, version
                FROM quotes FINAL
                WHERE quote_name = ?
                LIMIT 1
                """.trimIndent()
            ).use { ps ->
                ps.setString(1, quoteName)
                ps.executeQuery().use { rs ->
                    if (!rs.next()) error("Quote $quoteName not found")

                    val lastCost = rs.getInt("last_cost")
                    val minCost = rs.getInt("min_cost")
                    val maxCost = rs.getInt("max_cost")
                    val createdAt = rs.getTimestamp("created_at")
                    val version = rs.getLong("version") + 1
                    val nextPrice = (lastCost * (1.0 + relativeChange))
                        .roundToInt()
                        .coerceAtLeast(1)
                    val nextMin = minOf(minCost, nextPrice)
                    val nextMax = maxOf(maxCost, nextPrice)
                    val percent = (nextPrice - lastCost).toDouble() / lastCost * 100.0

                    conn.prepareStatement(
                        """
                        INSERT INTO quotes
                            (quote_name, last_cost, min_cost, max_cost, percentage_change, created_at, updated_at, version)
                        VALUES (?, ?, ?, ?, ?, ?, now(), ?)
                        """.trimIndent()
                    ).use { insert ->
                        insert.setString(1, quoteName)
                        insert.setInt(2, nextPrice)
                        insert.setInt(3, nextMin)
                        insert.setInt(4, nextMax)
                        insert.setDouble(5, percent)
                        insert.setTimestamp(6, createdAt)
                        insert.setLong(7, version)
                        insert.executeUpdate()
                    }

                    conn.prepareStatement(
                        """
                        INSERT INTO quotes_history (quote_name, price, happened_at, version)
                        VALUES (?, ?, now(), ?)
                        """.trimIndent()
                    ).use { insert ->
                        insert.setString(1, quoteName)
                        insert.setInt(2, nextPrice)
                        insert.setLong(3, version)
                        insert.executeUpdate()
                    }
                    return nextPrice.toDouble()
                }
            }
        }
    }
}

private fun Connection.usePrepared(
    sql: String,
    vararg params: Any,
    block: (java.sql.ResultSet) -> Unit
) {
    prepareStatement(sql).use { statement ->
        params.forEachIndexed { index, param -> statement.setObject(index + 1, param) }
        statement.executeQuery().use(block)
    }
}

private fun Connection.queryOne(sql: String, vararg params: Any): java.sql.ResultSet? {
    val statement = prepareStatement(sql)
    params.forEachIndexed { index, param -> statement.setObject(index + 1, param) }
    val rs = statement.executeQuery()
    return if (rs.next()) CachedRow(rs).also {
        rs.close()
        statement.close()
    } else {
        rs.close()
        statement.close()
        null
    }
}

private fun Connection.executeUpdate(sql: String, vararg params: Any) {
    prepareStatement(sql).use { statement ->
        params.forEachIndexed { index, param -> statement.setObject(index + 1, param) }
        statement.executeUpdate()
    }
}

private data class QuoteHistoryRow(
    val last: Double,
    val timestamp: String
)

private class CachedRow(rs: java.sql.ResultSet) : java.sql.ResultSet by rs {
    private val values = mutableMapOf<String, Any?>()

    init {
        val meta = rs.metaData
        for (i in 1..meta.columnCount) {
            values[meta.getColumnLabel(i)] = rs.getObject(i)
        }
    }

    override fun getInt(columnLabel: String): Int = (values[columnLabel] as Number).toInt()
    override fun getDouble(columnLabel: String): Double = (values[columnLabel] as Number).toDouble()
}
