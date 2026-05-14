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
    jvmToolchain(21)
}

// Добавьте эту строку для версии Ktor
val ktor_version = "3.4.2"

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

    // ClickHouse JDBC (котировки), транспорт HTTP
    implementation("com.clickhouse:clickhouse-jdbc:0.6.5:http")

    // Пул соединений (рекомендуется)
    implementation("com.zaxxer:HikariCP:5.1.0")

    // Хеширование паролей
    implementation("at.favre.lib:bcrypt:0.10.2")
    
    // Аутентификация и Сессии Ktor
    implementation("io.ktor:ktor-server-auth:$ktor_version")
    implementation("io.ktor:ktor-server-sessions:$ktor_version")

    // BCrypt для паролей
    implementation("org.mindrot:jbcrypt:0.4")  // ← исправлено: убрана буква i

    testImplementation(kotlin("test"))
    testImplementation(ktorLibs.server.testHost)
}