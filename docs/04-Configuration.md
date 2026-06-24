# 04 — Конфигурация

#docs #configuration

## Конфигурационные файлы

| Файл | Компонент | Назначение |
|---|---|---|
| `docker-compose.yml` | Infrastructure | Сервисы, порты, volumes, env-переменные |
| `gateway/src/main/resources/application.yaml` | Gateway | Ktor: порт, модули |
| `gateway/src/main/resources/logback.xml` | Gateway | Настройки логирования |
| `gateway/gradle/libs.versions.toml` | Gateway | Версии Gradle-зависимостей |
| `gateway/gradle.properties` | Gateway | JVM heap для Gradle |
| `server/go/go.mod` | Go server | Go-модуль, версия, зависимости |
| `android/app/build.gradle.kts` | Android | `BASE_URL`, compileSdk, minSdk |

---

## Переменные окружения — Go Server

**Файл источника:** `server/go/main.go`, функции `getenv()`, `quoteSourcePath()`

| Переменная | По умолчанию | Обязательна | Описание |
|---|---|---|---|
| `CLICKHOUSE_ADDR` | `localhost:9000` | Нет | Адрес ClickHouse (native protocol) |
| `CLICKHOUSE_DATABASE` | `default` | Нет | Имя базы данных |
| `CLICKHOUSE_USER` | `default` | Нет | Пользователь ClickHouse |
| `CLICKHOUSE_PASSWORD` | `""` | Нет* | Пароль ClickHouse |
| `QUOTES_SOURCE_PATH` | `/dev/itmo_quotes` | Нет | Путь к источнику котировок |
| `QUOTES_LOG_PATH` | — | Нет | Устаревший псевдоним для `QUOTES_SOURCE_PATH` |
| `PROCESS_EXISTING_AND_EXIT` | — | Нет | `true`/`1` — обработать файл один раз и выйти |

> [!NOTE]
> `CLICKHOUSE_PASSWORD` обязателен при использовании docker-compose из репозитория (пароль `itmo`).

---

## Переменные окружения — Ktor Gateway

**Файл источника:** `gateway/src/main/kotlin/database/ClickHouseManager.kt`, `DataBaseManager.kt`, `TradingRepository.kt`

### PostgreSQL

| Переменная | По умолчанию | Обязательна | Описание |
|---|---|---|---|
| `POSTGRES_JDBC_URL` | `jdbc:postgresql://localhost:5432/itmo_traiding_system` | Нет | JDBC URL |
| `POSTGRES_USER` | `postgres` | Нет | Пользователь PostgreSQL |
| `POSTGRES_PASSWORD` | `postgres` | Нет* | Пароль PostgreSQL |

### ClickHouse

| Переменная | По умолчанию | Обязательна | Описание |
|---|---|---|---|
| `CLICKHOUSE_JDBC_URL` | `jdbc:clickhouse://localhost:8123/default` | Нет | JDBC URL (HTTP протокол) |
| `CLICKHOUSE_USER` | `default` | Нет | Пользователь ClickHouse |
| `CLICKHOUSE_PASSWORD` | `""` | Нет* | Пароль ClickHouse |

### Flyway (только при запуске в Docker через `entrypoint.sh`)

| Переменная | По умолчанию | Описание |
|---|---|---|
| `FLYWAY_LOCATIONS` | `classpath:db/migration` | Путь к миграциям |

---

## Конфигурация Docker Compose

**Файл:** `docker-compose.yml`

```yaml
services:
  postgres:
    image: postgres:16
    container_name: itmo-trading-postgres
    environment:
      POSTGRES_DB: itmo_traiding_system
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    ports: ["5432:5432"]

  clickhouse:
    image: clickhouse/clickhouse-server:24.8
    container_name: itmo-trading-clickhouse
    environment:
      CLICKHOUSE_USER: default
      CLICKHOUSE_PASSWORD: itmo
    ports: ["8123:8123", "9000:9000"]

  go-server:
    environment:
      CLICKHOUSE_ADDR: clickhouse:9000
      CLICKHOUSE_PASSWORD: itmo
      QUOTES_SOURCE_PATH: /app/quotes.log
    volumes:
      - ./server/drivers/quotes.log:/app/quotes.log:ro

  gateway:
    environment:
      POSTGRES_JDBC_URL: jdbc:postgresql://postgres:5432/itmo_traiding_system
      CLICKHOUSE_JDBC_URL: jdbc:clickhouse://clickhouse:8123/default
      CLICKHOUSE_PASSWORD: itmo
      POSTGRES_PASSWORD: postgres
    ports: ["8080:8080"]
```

---

## Конфигурация Ktor (`application.yaml`)

**Файл:** `gateway/src/main/resources/application.yaml`

```yaml
ktor:
  deployment:
    port: 8080
  application:
    modules:
      - com.trading.HttpKt.configureHttp           # CORS
      - com.trading.SerializationKt.configureSerialization  # JSON
      - com.trading.WebsocketsKt.configureWebsockets
      - com.trading.StatusPagesKt.configureStatusPages
      - com.trading.ResourcesKt.configureResources
      - com.trading.RoutingKt.configureRouting      # /quotes, /api/quotes
      - com.trading.users.RegisterRoutesKt.module   # /register, /api/register, /api/login
```

> [!NOTE]
> Trading routes (`/api/trade`, `/api/account`, `/api/portfolio`) регистрируются через `RoutingKt.configureRouting()`, который вызывает `tradingRoutes()`.

---

## Конфигурация Android

**Файл:** `android/app/build.gradle.kts`

```kotlin
defaultConfig {
    applicationId = "com.trading.android"
    minSdk = 26
    targetSdk = 34
    compileSdk = 34
    buildConfigField("String", "BASE_URL", "\"http://10.0.2.2:8080\"")
}
```

`BASE_URL` — URL backend для Android Emulator. Для реального устройства замените на IP хост-машины.

---

## Связанные документы

- [[03-Setup-and-Installation]]
- [[10-Deployment]]
- [[07-Database]]
