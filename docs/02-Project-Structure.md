# 02 — Структура проекта

#docs #structure

## Дерево директорий

```
ITMO.Traiding/
├── docker-compose.yml              # Локальный PostgreSQL + ClickHouse + сервисы
├── README.md                       # Общий README проекта
├── CLAUDE.md                       # Инструкции для Claude Code
├── scripts/
│   └── integration-test.sh         # Сквозной интеграционный тест
├── server/
│   ├── drivers/                    # Linux kernel module
│   │   ├── generateQuotes_kernel.c # Исходник модуля ядра
│   │   ├── Makefile                # Сборка .ko файла
│   │   └── quotes.log              # Тестовые котировки (без kernel)
│   └── go/                         # Go-инжестор котировок
│       ├── Dockerfile              # Multi-stage: golang:1.22 → debian:slim
│       ├── main.go                 # Основная логика инжестора
│       ├── main_test.go            # Unit-тесты parseQuoteLine
│       ├── go.mod                  # Модуль itmo.trading, Go 1.22.2
│       └── go.sum
├── gateway/                        # Kotlin/Ktor backend
│   ├── Dockerfile                  # Multi-stage: eclipse-temurin:20-jdk → jre
│   ├── entrypoint.sh               # Flyway migrate → java -jar
│   ├── build.gradle.kts            # Gradle Kotlin DSL
│   ├── settings.gradle.kts
│   ├── gradle.properties           # JVM heap settings
│   ├── gradle/
│   │   ├── libs.versions.toml      # Версии зависимостей
│   │   └── wrapper/
│   ├── src/
│   │   ├── main/
│   │   │   ├── kotlin/
│   │   │   │   ├── main.kt         # Точка входа (EngineMain)
│   │   │   │   ├── Routing.kt      # GET /quotes, GET /api/quotes
│   │   │   │   ├── Http.kt         # CORS настройки
│   │   │   │   ├── Serialization.kt# ContentNegotiation (JSON)
│   │   │   │   ├── Websockets.kt   # WebSocket поддержка
│   │   │   │   ├── StatusPages.kt  # Обработка HTTP-ошибок
│   │   │   │   ├── Resources.kt    # Статические ресурсы
│   │   │   │   ├── Articles.kt     # Заглушка (не используется)
│   │   │   │   ├── database/
│   │   │   │   │   ├── DataBaseManager.kt         # PostgreSQL JDBC
│   │   │   │   │   ├── ClickHouseManager.kt       # ClickHouse JDBC + HikariCP
│   │   │   │   │   ├── PostgresMigrationRunner.kt # Flyway runner
│   │   │   │   │   └── FlywayMigrateApp.kt        # Standalone CLI для миграций
│   │   │   │   ├── quotes/
│   │   │   │   │   ├── Quote.kt           # Data class + HTML helpers
│   │   │   │   │   └── QuoteReposetory.kt # SELECT из ClickHouse
│   │   │   │   ├── trading/
│   │   │   │   │   ├── TradingModels.kt   # Serializable data classes
│   │   │   │   │   ├── TradingRepository.kt # BL: account, portfolio, trade
│   │   │   │   │   └── TradingRoutes.kt   # GET/POST /api/* маршруты
│   │   │   │   └── users/
│   │   │   │       ├── Register.kt        # Логика регистрации + BCrypt
│   │   │   │       ├── Authorization.kt   # Проверка пароля
│   │   │   │       └── RegisterRoutes.kt  # HTML формы + /api/register, /api/login
│   │   │   └── resources/
│   │   │       ├── application.yaml       # Ktor config (port 8080, modules)
│   │   │       ├── logback.xml            # Логирование
│   │   │       └── db/migration/
│   │   │           ├── V1__create_users.sql
│   │   │           ├── V3__portfolio_and_trades.sql
│   │   │           ├── V4__restore_demo_balance.sql
│   │   │           └── V5__portfolio_avg_price_compatibility.sql
│   │   └── test/kotlin/
│   │       └── ServerTest.kt              # Ktor testApplication
│   └── README.md                          # (пустой)
└── android/                        # Android-приложение
    ├── build.gradle.kts            # Root Gradle config
    ├── settings.gradle.kts
    └── app/
        ├── build.gradle.kts        # App config (compileSdk=34, BASE_URL)
        └── src/main/
            ├── AndroidManifest.xml
            ├── java/com/trading/android/
            │   ├── MainActivity.kt  # Compose UI: Login, Quotes, Details
            │   ├── ApiClient.kt     # OkHttp + корутины
            │   └── Quote.kt         # Data classes (Quote, Holding, Candle...)
            └── res/values/
                ├── colors.xml
                ├── strings.xml
                └── themes.xml
```

---

## Назначение ключевых файлов

| Путь | Назначение | Критичность |
|---|---|---|
| `docker-compose.yml` | Поднимает PostgreSQL, ClickHouse, Go-сервер, gateway | 🔴 Критично |
| `server/go/main.go` | Data pipeline: kernel → ClickHouse | 🔴 Критично |
| `gateway/src/main/kotlin/trading/TradingRepository.kt` | Вся торговая бизнес-логика | 🔴 Критично |
| `gateway/src/main/kotlin/database/ClickHouseManager.kt` | Пул соединений ClickHouse, DDL таблиц | 🔴 Критично |
| `gateway/src/main/kotlin/database/DataBaseManager.kt` | PostgreSQL соединение + Flyway запуск | 🔴 Критично |
| `gateway/src/main/resources/db/migration/V1__create_users.sql` | Схема таблицы users | 🟠 Важно |
| `gateway/src/main/resources/db/migration/V3__portfolio_and_trades.sql` | Схема portfolio + trades | 🟠 Важно |
| `gateway/src/main/kotlin/Routing.kt` | HTTP маршруты quotes | 🟠 Важно |
| `gateway/src/main/kotlin/trading/TradingRoutes.kt` | HTTP маршруты trading | 🟠 Важно |
| `gateway/src/main/kotlin/users/RegisterRoutes.kt` | HTTP маршруты auth | 🟠 Важно |
| `android/app/src/main/java/com/trading/android/ApiClient.kt` | Android HTTP-клиент | 🟡 Умеренно |
| `server/drivers/generateQuotes_kernel.c` | Генератор котировок (только Linux) | 🟡 Умеренно |
| `scripts/integration-test.sh` | E2E тест | 🟢 Служебно |
| `gateway/entrypoint.sh` | Docker entrypoint: migrate → start | 🟢 Служебно |

---

## Критичные части проекта

> [!WARNING]
> Следующие части проекта являются точками отказа всей системы:

1. **ClickHouseManager.kt** — инициализируется при старте gateway. Если ClickHouse недоступен — gateway не стартует.
2. **DataBaseManager.kt** — запускает Flyway миграции при первом подключении. Если миграции не применены — таблицы users/portfolio/trades не существуют.
3. **TradingRepository.trade()** — выполняет транзакцию PostgreSQL + запись в ClickHouse. Частичный сбой оставит данные в несогласованном состоянии (нет распределённой транзакции).

---

## Связанные документы

- [[01-Architecture]]
- [[05-Development-Guide]]
- [[10-Deployment]]
