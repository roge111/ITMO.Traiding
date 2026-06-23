plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(ktorLibs.plugins.ktor)
    alias(libs.plugins.kotlin.serialization)
}

group = "com.trading"
version = "1.0.0-SNAPSHOT"

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

kotlin {
    jvmToolchain(20)
}

dependencies {
    implementation(ktorLibs.server.callLogging)
    implementation(ktorLibs.server.config.yaml)
    implementation(ktorLibs.server.contentNegotiation)
    implementation(ktorLibs.server.core)
    implementation(ktorLibs.server.cors)
    implementation(ktorLibs.server.netty)
    implementation(ktorLibs.server.resources)
    implementation(ktorLibs.server.statusPages)
    implementation(ktorLibs.server.websockets)
    implementation(libs.logback.classic)
    
    // Зависимости для Exposed - работа с БД
    implementation("org.jetbrains.exposed:exposed-core:0.56.0")
    implementation("org.jetbrains.exposed:exposed-dao:0.56.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.56.0")
    implementation("org.jetbrains.exposed:exposed-java-time:0.56.0")

    // Драйвер PostgreSQL
    implementation("org.postgresql:postgresql:42.7.1")

    // Миграции схемы PostgreSQL (users и др.)
    implementation("org.flywaydb:flyway-core:10.21.0")
    implementation("org.flywaydb:flyway-database-postgresql:10.21.0")

    // ClickHouse JDBC (котировки), транспорт HTTP
    implementation("com.clickhouse:clickhouse-jdbc:0.6.5:http")
    implementation("org.apache.httpcomponents.client5:httpclient5:5.3.1")
    implementation("org.apache.httpcomponents.core5:httpcore5:5.2.4")

    // Пул соединений (рекомендуется)
    implementation("com.zaxxer:HikariCP:5.1.0")

    implementation("org.mindrot:jbcrypt:0.4")

    implementation("io.ktor:ktor-serialization-kotlinx-json:3.4.1")

    testImplementation(kotlin("test"))
    testImplementation(ktorLibs.server.testHost)
}

tasks.register<JavaExec>("flywayMigrate") {
    group = "database"
    description = "Применить Flyway-миграции к PostgreSQL (POSTGRES_JDBC_URL, POSTGRES_USER, POSTGRES_PASSWORD)"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.trading.database.FlywayMigrateApp")
}
