package com.trading.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.sql.Connection
import java.sql.Statement
import javax.sql.DataSource

object ClickHouseManager {

    private val dataSource: HikariDataSource

    init {
        val jdbcUrl = System.getenv("CLICKHOUSE_JDBC_URL")?.takeIf { it.isNotBlank() }
            ?: "jdbc:clickhouse://localhost:8123/default"
        val user = System.getenv("CLICKHOUSE_USER")?.takeIf { it.isNotBlank() } ?: "default"
        val password = System.getenv("CLICKHOUSE_PASSWORD") ?: ""

        val cfg = HikariConfig().apply {
            this.jdbcUrl = jdbcUrl
            username = user
            this.password = password
            driverClassName = "com.clickhouse.jdbc.ClickHouseDriver"
            maximumPoolSize = 4
            poolName = "clickhouse-quotes"
        }
        dataSource = HikariDataSource(cfg)
        ensureQuotesTable()
    }

    fun getDataSource(): DataSource = dataSource

    fun getConnection(): Connection = dataSource.connection

    private fun ensureQuotesTable() {
        val ddl = """
            CREATE TABLE IF NOT EXISTS quotes (
                quote_name String,
                last_cost Int32,
                min_cost Int32,
                max_cost Int32,
                percentage_change Float64,
                created_at DateTime DEFAULT now(),
                updated_at DateTime DEFAULT now(),
                version UInt64
            )
            ENGINE = ReplacingMergeTree(version)
            ORDER BY quote_name
        """.trimIndent()
        dataSource.connection.use { conn ->
            conn.createStatement().use { st: Statement ->
                st.execute(ddl)
            }
        }
    }
}
