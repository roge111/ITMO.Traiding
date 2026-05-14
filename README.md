# ITMO.Trading System

Система для сбора, обработки и отображения биржевых котировок в реальном времени. Проект состоит из трёх основных компонентов: модуля ядра Linux, Go-сервера для записи котировок в **ClickHouse** и Kotlin/Ktor веб-шлюза. Пользователи и регистрация хранятся в **PostgreSQL**; котировки для `/quotes` читаются и пишутся только в ClickHouse.

## Структура проекта

```
ITMO.Traiding/
├── docker-compose.yml          # Локальный PostgreSQL + ClickHouse
├── gateway/                    # Kotlin/Ktor веб-шлюз
│   ├── src/main/kotlin/
│   │   ├── main.kt            # Точка входа приложения
│   │   ├── Routing.kt         # Маршрутизация HTTP-запросов
│   │   ├── Articles.kt        # (заглушка)
│   │   ├── Http.kt            # Конфигурация HTTP (CORS)
│   │   ├── Resources.kt       # Конфигурация ресурсов
│   │   ├── Serialization.kt   # Конфигурация сериализации
│   │   ├── StatusPages.kt     # Конфигурация страниц ошибок
│   │   ├── Websockets.kt      # Конфигурация WebSockets
│   │   ├── database/
│   │   │   ├── DataBaseManager.kt   # PostgreSQL (пользователи)
│   │   │   └── ClickHouseManager.kt # ClickHouse (котировки, HikariCP)
│   │   ├── quotes/
│   │   │   ├── Quote.kt            # Модель котировки и HTML-утилиты
│   │   │   └── QuoteReposetory.kt  # Репозиторий QuoteRepository → ClickHouse
│   │   └── users/
│   │       ├── Register.kt         # Логика регистрации пользователей
│   │       └── RegisterRoutes.kt   # Маршруты регистрации (HTML форма + API)
│   ├── src/main/resources/
│   │   ├── application.yaml   # Конфигурация Ktor
│   │   └── logback.xml        # Конфигурация логирования
│   ├── build.gradle.kts       # Gradle конфигурация
│   └── README.md              # (пустой)
├── server/                    # Go-сервер для обработки логов
│   ├── go/
│   │   ├── main.go            # Чтение /quotes.log и запись в ClickHouse
│   │   ├── go.mod             # Go модули
│   │   └── go.sum             # Зависимости
│   └── drivers/               # Модуль ядра Linux
│       ├── generateQuotes_kernel.c  # Исходный код модуля ядра
│       ├── Makefile           # Сборка модуля
│       ├── generateQuotes_kernel.ko # Скомпилированный модуль
│       └── quotes.log         # Пример лог-файла (генерируется модулем)
└── README.md                  # Этот файл
```

## Назначение компонентов

### 1. Модуль ядра Linux (`generateQuotes_kernel`)
- **Расположение:** `server/drivers/`
- **Назначение:** Генерирует случайные котировки и записывает их в файл `/quotes.log` в формате `название,цена,время`.
- **Сборка:** `make all` (требуются заголовки ядра)
- **Установка:** `sudo insmod generateQuotes_kernel.ko`
- **Удаление:** `sudo rmmod generateQuotes_kernel`

### 2. Go-сервер (`server/go/main.go`)
- **Расположение:** `server/go/`
- **Назначение:** Читает **новые** строки из `/quotes.log` (сохраняется смещение в файле, чтобы не вставлять повторно одни и те же строки при каждом проходе), парсит записи и вставляет версии строк в **ClickHouse** (`ReplacingMergeTree` по полю `version`).
- **Функциональность:**
  - Подключение к ClickHouse через официальный драйвер `github.com/ClickHouse/clickhouse-go/v2` (протокол native, DSN `clickhouse://`)
  - При старте создаёт БД (если не `default`) и таблицу `quotes`, если их ещё нет
  - Для каждой строки лога: `SELECT ... FROM quotes FINAL WHERE quote_name = ?`, затем расчёт min/max/% и `INSERT` новой версии (без PostgreSQL UPSERT)
- **Запуск:** из каталога модуля: `cd server/go && go run .` (нужны ClickHouse и файл лога; модуль ядра — по сценарию)

### 3. Kotlin/Ktor веб-шлюз (`gateway`)
- **Расположение:** `gateway/`
- **Назначение:** HTTP API: HTML `/quotes` из **ClickHouse**; регистрация пользователей в **PostgreSQL**.
- **Технологии:** Kotlin, Ktor, Exposed, PostgreSQL, ClickHouse JDBC, HikariCP (пул для ClickHouse), BCrypt
- **Функциональность:**
  - `GET /quotes` — HTML-таблица, данные из ClickHouse (`quotes FINAL`)
  - `GET /register`, `POST /api/register` — пользователи в PostgreSQL
  - Хеширование паролей (BCrypt)
- **Запуск:** `cd gateway && ./gradlew run` (порт 8080 по умолчанию)

## Базы данных

### ClickHouse — котировки

Таблица актуального состояния (несколько версий строк на один `quote_name`; актуальная выборка через `FINAL`):

```sql
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
ORDER BY quote_name;
```

Таблица создаётся при старте Go-сервера и при первом подключении шлюза через `ClickHouseManager` (идемпотентно, `IF NOT EXISTS`).

### PostgreSQL — только пользователи

База `itmo_traiding_system`. Таблица `users`:

```sql
CREATE TABLE users (
    user_id SERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    balance INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

Таблицу `quotes` в PostgreSQL использовать не нужно.

## Переменные окружения

### Go-сервер (`server/go`)

| Переменная | По умолчанию |
|------------|----------------|
| `CLICKHOUSE_ADDR` | `localhost:9000` |
| `CLICKHOUSE_DATABASE` | `default` |
| `CLICKHOUSE_USER` | `default` |
| `CLICKHOUSE_PASSWORD` | пусто |

Пароль ClickHouse в коде не задаётся.

### Gateway — ClickHouse (котировки)

| Переменная | По умолчанию |
|------------|----------------|
| `CLICKHOUSE_JDBC_URL` | `jdbc:clickhouse://localhost:8123/default` |
| `CLICKHOUSE_USER` | `default` |
| `CLICKHOUSE_PASSWORD` | пусто |

### Gateway — PostgreSQL (пользователи)

| Переменная | По умолчанию |
|------------|----------------|
| `POSTGRES_JDBC_URL` | `jdbc:postgresql://localhost:5432/itmo_traiding_system` |
| `POSTGRES_USER` | `postgres` |
| `POSTGRES_PASSWORD` | `Gb%v5oVA` |

**Docker Compose** в корне задаёт для PostgreSQL пароль `postgres`. Чтобы шлюз и регистрация подключались к контейнеру, задайте перед запуском gateway, например:

`export POSTGRES_PASSWORD=postgres`

(при необходимости скорректируйте `POSTGRES_JDBC_URL`).

## Требования

- Linux (для модуля ядра)
- PostgreSQL 14+ (пользователи)
- ClickHouse 24.x (котировки; см. `docker-compose.yml`)
- Go 1.22+ (в `go.mod` указана 1.22.2)
- Java 21+ (для Kotlin/Ktor)
- Заголовки ядра Linux (для сборки модуля)

## Настройка и запуск

### 0. Локальные БД через Docker (опционально)

В корне репозитория:

```bash
docker compose up -d
```

Поднимаются PostgreSQL (`5432`) и ClickHouse (`8123` HTTP, `9000` native). Для gateway при использовании этого compose задайте `POSTGRES_PASSWORD=postgres`.

### 1. Настройка PostgreSQL (пользователи)

```bash
sudo -u postgres psql
CREATE DATABASE itmo_traiding_system;
\c itmo_traiding_system

CREATE TABLE users (
    user_id SERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    balance INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### 2. ClickHouse

Достаточно запущенного сервера с доступом по HTTP (8123) и native (9000). Таблица `quotes` создаётся приложениями автоматически в базе из `CLICKHOUSE_DATABASE` / пути JDBC URL (`default`, если не переопределено).

### 3. Сборка и запуск модуля ядра

```bash
cd server/drivers
make all
sudo insmod generateQuotes_kernel.ko
tail -f /quotes.log
```

### 4. Запуск Go-сервера

```bash
cd server/go
go run .
```

Убедитесь, что ClickHouse доступен по переменным окружения выше.

### 5. Запуск веб-шлюза

```bash
cd gateway
./gradlew run
```

Откройте `http://localhost:8080/quotes` или `http://localhost:8080/register`.

## Конфигурация

### Gateway (`application.yaml`)

```yaml
ktor:
  deployment:
    port: 8080
  application:
    modules:
      - com.trading.HttpKt.configureHttp
      - com.trading.SerializationKt.configureSerialization
      - com.trading.WebsocketsKt.configureWebsockets
      - com.trading.StatusPagesKt.configureStatusPages
      - com.trading.ResourcesKt.configureResources
      - com.trading.RoutingKt.configureRouting
      - com.trading.RegisterRoutesKt.module
```

Параметры подключения к БД задаются через переменные окружения (см. выше): PostgreSQL — `DataBaseManager.kt`, ClickHouse — `ClickHouseManager.kt`.

### Go-сервер

Параметры ClickHouse — только через переменные окружения (см. таблицу).

## Разработка

### Gateway
- Gradle с Kotlin DSL; часть версий в `gradle/libs.versions.toml`, часть координат в `build.gradle.kts`
- Разработка: `./gradlew run --continuous`
- Тесты: `./gradlew test`

### Go-сервер
- Go modules
- Зависимость: `github.com/ClickHouse/clickhouse-go/v2`

### Модуль ядра
- Права суперпользователя для `insmod`
- Отладка: `dmesg | tail`

## Возможные улучшения

1. **Миграции:** Flyway/Liquibase для PostgreSQL и/или версионируемые DDL для ClickHouse
2. **API:** JSON, фильтрация и пагинация для котировок
3. **Веб-интерфейс:** SPA для визуализации
4. **Мониторинг:** Prometheus/Grafana

## Авторы

Проект разработан в рамках учебного курса ИТМО.

## Лицензия

MIT
