# Модуль: Kotlin/Ktor Gateway

#docs #module #kotlin

## Назначение

HTTP-сервер, предоставляющий REST API для котировок (из ClickHouse) и операций пользователей (из PostgreSQL). Точка входа для Android-клиента и браузера.

## Расположение

```
gateway/src/main/kotlin/
├── main.kt                         # EngineMain — точка входа
├── Routing.kt                      # GET /quotes, GET /api/quotes
├── Http.kt                         # CORS конфигурация
├── Serialization.kt                # ContentNegotiation (JSON)
├── Websockets.kt                   # WebSocket поддержка (конфиг)
├── StatusPages.kt                  # HTTP error handling
├── Resources.kt                    # Статические ресурсы
├── Articles.kt                     # Заглушка, не используется
├── database/
│   ├── DataBaseManager.kt          # PostgreSQL JDBC + Flyway
│   ├── ClickHouseManager.kt        # ClickHouse HikariCP pool + DDL
│   ├── PostgresMigrationRunner.kt  # Flyway wrapper
│   └── FlywayMigrateApp.kt         # Standalone CLI для миграций
├── quotes/
│   ├── Quote.kt                    # Data class + HTML helpers
│   └── QuoteReposetory.kt          # SELECT из ClickHouse
├── trading/
│   ├── TradingModels.kt            # Serializable data classes
│   ├── TradingRepository.kt        # Бизнес-логика trading
│   └── TradingRoutes.kt            # HTTP routes /api/*
└── users/
    ├── Register.kt                 # Регистрация + BCrypt
    ├── Authorization.kt            # Авторизация + BCrypt
    └── RegisterRoutes.kt           # HTML forms + /api/register, /api/login
```

## Ключевые компоненты

### ClickHouseManager

Singleton (`object`), инициализируется при первом обращении. Создаёт HikariCP пул (4 соединения), при инициализации создаёт таблицы `quotes` и `quotes_history` (idempotent `CREATE TABLE IF NOT EXISTS`).

```kotlin
object ClickHouseManager {
    private val dataSource: HikariDataSource  // пул
    fun getConnection(): Connection = dataSource.connection
}
```

### DataBaseManager

Класс (не singleton), создаётся по одному экземпляру в `Register` и `Authorization`. Запускает Flyway-миграции при `connect()`.

> [!WARNING]
> `DataBaseManager` создаёт прямое JDBC-соединение через `DriverManager.getConnection()` без пула. `TradingRepository` также создаёт соединения через `DriverManager`. Это потенциальное узкое место при высокой нагрузке.

### TradingRepository

Singleton (`object`). Содержит всю торговую бизнес-логику:

| Метод | Описание |
|---|---|
| `account(username)` | Баланс + стоимость портфеля |
| `portfolio(username)` | Список позиций с текущими ценами |
| `trade(username, quoteName, quantity, side)` | Транзакция BUY/SELL |
| `history(quoteName)` | Последние 30 цен из quotes_history |
| `candles(quoteName)` | Синтез OHLC из истории |
| `marketTick(quoteName?)` | Симуляция рыночного движения |

## Конфигурация Ktor-модулей

Порядок загрузки модулей из `application.yaml`:
1. `configureHttp` — CORS
2. `configureSerialization` — JSON
3. `configureWebsockets` — WS
4. `configureStatusPages` — ошибки
5. `configureResources` — статика
6. `configureRouting` — маршруты quotes + tradingRoutes()
7. `RegisterRoutesKt.module` — регистрация + авторизация

## Зависимости (build.gradle.kts)

| Библиотека | Назначение |
|---|---|
| `ktor-server-netty` | HTTP-сервер |
| `exposed-core/dao/jdbc/java-time` | ORM для PostgreSQL |
| `postgresql` | JDBC драйвер PostgreSQL |
| `flyway-core + flyway-database-postgresql` | Миграции |
| `clickhouse-jdbc:http` | JDBC ClickHouse через HTTP |
| `httpclient5` | HTTP-транспорт для ClickHouse JDBC |
| `HikariCP` | Connection pool для ClickHouse |
| `jbcrypt` | Хэширование паролей |
| `kotlinx-serialization-json` | JSON сериализация |

## Публичный API

Все endpoints задокументированы в [[../06-API]].

## Связанные документы

- [[../06-API]] — полный API reference
- [[../07-Database]] — схемы БД
- [[../08-Authentication-and-Authorization]] — auth логика
- [[../05-Development-Guide]] — как добавить endpoint
