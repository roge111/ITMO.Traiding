package com.trading.database

import org.flywaydb.core.Flyway

/**
 * Применяет Flyway-миграции к PostgreSQL (идемпотентно).
 * Вызывается перед открытием соединения в [DataBaseManager] и из задачи Gradle [flywayMigrate].
 */
object PostgresMigrationRunner {

    fun ensureMigrated(jdbcUrl: String, user: String, password: String) {
        Flyway.configure()
            .dataSource(jdbcUrl, user, password)
            .locations("classpath:db/migration")
            .load()
            .migrate()
    }
}
