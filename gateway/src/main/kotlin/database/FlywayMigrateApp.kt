package com.trading.database

/**
 * Точка входа для `./gradlew flywayMigrate`.
 */
object FlywayMigrateApp {
    @JvmStatic
    fun main(args: Array<String>) {
        val url = System.getenv("POSTGRES_JDBC_URL")?.takeIf { it.isNotBlank() }
            ?: "jdbc:postgresql://localhost:5432/itmo_traiding_system"
        val user = System.getenv("POSTGRES_USER")?.takeIf { it.isNotBlank() } ?: "postgres"
        val password = System.getenv("POSTGRES_PASSWORD") ?: "Gb%v5oVA"
        PostgresMigrationRunner.ensureMigrated(url, user, password)
        println("Flyway: миграции применены.")
    }
}
