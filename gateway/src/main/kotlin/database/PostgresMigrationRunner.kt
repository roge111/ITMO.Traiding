package com.trading.database

import org.flywaydb.core.Flyway

/**
 * Применяет Flyway-миграции к PostgreSQL (идемпотентно).
 * Вызывается перед открытием соединения в [DataBaseManager] и из задачи Gradle [flywayMigrate].
 */
object PostgresMigrationRunner {

    fun ensureMigrated(jdbcUrl: String, user: String, password: String) {
        val locations = System.getenv("FLYWAY_LOCATIONS")
            ?.split(",")?.map { it.trim() }?.toTypedArray()
            ?: arrayOf("classpath:db/migration")
        Flyway.configure()
            .dataSource(jdbcUrl, user, password)
            .locations(*locations)
            .baselineOnMigrate(true)
            .baselineVersion("0")
            .load()
            .migrate()
    }
}
