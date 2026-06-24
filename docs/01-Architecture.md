# 01 — Архитектура

#docs #architecture

## Обзор компонентов

Система состоит из 4 независимых компонентов, связанных через HTTP API и напрямую через базы данных.

```mermaid
graph TB
    subgraph "Linux Host"
        KM["Linux Kernel Module<br/>generateQuotes_kernel.ko<br/>/dev/itmo_quotes"]
        GO["Go Server<br/>server/go/main.go<br/>:9000 (ClickHouse native)"]
    end

    subgraph "Databases (Docker)"
        CH["ClickHouse<br/>:8123 HTTP / :9000 native<br/>quotes + quotes_history"]
        PG["PostgreSQL<br/>:5432<br/>users + portfolio + trades"]
    end

    subgraph "Gateway (JVM)"
        KTOR["Kotlin/Ktor<br/>gateway/<br/>:8080"]
    end

    subgraph "Clients"
        AND["Android App<br/>Jetpack Compose<br/>10.0.2.2:8080"]
        BR["Browser<br/>localhost:8080"]
    end

    KM -->|"open/read/close<br/>CSV lines"| GO
    GO -->|"INSERT (native protocol)"| CH
    KTOR -->|"JDBC HTTP :8123<br/>SELECT FINAL"| CH
    KTOR -->|"JDBC :5432<br/>users, portfolio, trades"| PG
    AND -->|"OkHttp / HTTP"| KTOR
    BR -->|"HTTP"| KTOR
```

## Компоненты

### 1. Linux Kernel Module (`server/drivers/`)

Реализован как misc-устройство `/dev/itmo_quotes`. Содержит массив из 7 котировок (GAZ, YANDEX, SBER, LUKOIL, ROSNEFT, VTBR, MOEX). Фоновый kthread (`generator`) каждые 3 секунды обновляет случайную котировку. При `open()` формирует текстовый снимок всех котировок в формате `NAME,PRICE,TIMESTAMP` и сохраняет его как `file->private_data`. `read()` копирует данные в userspace. `release()` освобождает память снимка.

**Источник:** `server/drivers/generateQuotes_kernel.c`

### 2. Go Server (`server/go/`)

Читает котировки из источника (символьное устройство или файл), парсит CSV и записывает в ClickHouse. Дедупликация через in-memory `seen map[string]string` — ключ `name`, значение `price@unixTimestamp`. Запись происходит только при изменении цены или временной метки.

**Источник:** `server/go/main.go`

### 3. Kotlin/Ktor Gateway (`gateway/`)

HTTP-сервер на Netty. Два соединения с БД:
- ClickHouse — через JDBC HTTP + HikariCP (пул 4 соединения) — только котировки
- PostgreSQL — через прямой JDBC (без пула на уровне gateway, `DriverManager.getConnection()`) — пользователи, портфель, сделки

**Источник:** `gateway/src/main/kotlin/`

### 4. Android App (`android/`)

Нативное приложение на Kotlin + Jetpack Compose. HTTP-клиент на OkHttp с корутинами. `BASE_URL = http://10.0.2.2:8080` (эмулятор → хост).

**Источник:** `android/app/src/main/java/com/trading/android/`

---

## Data Flow

### Путь котировки (от kernel до UI)

```mermaid
sequenceDiagram
    participant KM as Kernel Module
    participant GO as Go Server
    participant CH as ClickHouse
    participant KTOR as Ktor Gateway
    participant APP as Android/Browser

    loop Каждые 3 сек
        KM->>KM: update_random_quote()
    end

    loop Каждую секунду
        GO->>KM: open(/dev/itmo_quotes)
        KM->>GO: snapshot CSV (7 строк)
        GO->>KM: close()
        GO->>GO: parseQuoteLine() + дедупликация
        GO->>CH: INSERT INTO quotes (ReplacingMergeTree)
        GO->>CH: INSERT INTO quotes_history (MergeTree)
    end

    APP->>KTOR: GET /api/quotes
    KTOR->>CH: SELECT * FROM quotes FINAL
    CH->>KTOR: список котировок
    KTOR->>APP: JSON array
```

### Путь торговой операции

```mermaid
sequenceDiagram
    participant APP as Android
    participant KTOR as Ktor Gateway
    participant PG as PostgreSQL
    participant CH as ClickHouse

    APP->>KTOR: POST /api/trade (login, quoteName, quantity, side)
    KTOR->>CH: SELECT last_cost FROM quotes FINAL WHERE quote_name = ?
    CH->>KTOR: текущая цена
    KTOR->>PG: BEGIN TRANSACTION
    KTOR->>PG: SELECT balance FROM users FOR UPDATE
    KTOR->>PG: SELECT quantity FROM portfolio FOR UPDATE
    KTOR->>PG: UPDATE users SET balance = ?
    KTOR->>PG: INSERT/UPDATE portfolio
    KTOR->>PG: INSERT INTO trades
    KTOR->>PG: COMMIT
    KTOR->>CH: INSERT INTO quotes (новая цена с impact)
    KTOR->>CH: INSERT INTO quotes_history
    KTOR->>APP: TradeResult (message, balance, holding)
```

---

## Архитектурные слои

| Слой | Компоненты | Технология |
|---|---|---|
| **Data Source** | `generateQuotes_kernel.c` | Linux C |
| **Data Pipeline** | `server/go/main.go` | Go |
| **Storage** | ClickHouse, PostgreSQL | SQL |
| **API** | `gateway/src/main/kotlin/` | Kotlin/Ktor |
| **Client** | `android/app/` | Android/Compose |
| **Infrastructure** | `docker-compose.yml`, Dockerfiles | Docker |

---

## Важные архитектурные решения

### Разделение БД по ответственности

ClickHouse хранит **только котировки** (time-series). PostgreSQL хранит **только данные пользователей** (users, portfolio, trades). Это разделение позволяет масштабировать каждую базу независимо.

См. [[decisions/ADR-001-dual-database]]

### ReplacingMergeTree для котировок

Таблица `quotes` использует `ReplacingMergeTree(version)`. При запросе `SELECT FINAL` ClickHouse автоматически возвращает только актуальную версию котировки. Go-сервер каждый раз вставляет новую строку с увеличенным `version`.

См. [[decisions/ADR-002-clickhouse-engine]]

### Go как отдельный инжестор

Go-сервис изолирован от gateway. Это позволяет перезапускать инжестор независимо и избавляет JVM-приложение от блокирующего I/O с устройством ядра.

См. [[decisions/ADR-003-go-ingestor]]

### Упрощённая авторизация

Торговые API принимают `login` как query/form параметр без JWT/сессий. Это учебное ограничение MVP.

См. [[decisions/ADR-004-auth-simplification]]

---

## Deployment Diagram

```mermaid
graph TB
    subgraph "docker-compose.yml"
        PG_C["postgres:16<br/>5432:5432"]
        CH_C["clickhouse/clickhouse-server:24.8<br/>8123:8123, 9000:9000"]
        GO_C["go-server (custom image)<br/>зависит от clickhouse"]
        GW_C["gateway (custom image)<br/>8080:8080<br/>зависит от postgres+clickhouse"]
    end
    GO_C --> CH_C
    GW_C --> PG_C
    GW_C --> CH_C
```

---

## Связанные документы

- [[00-Overview]]
- [[07-Database]]
- [[06-API]]
- [[10-Deployment]]
- [[decisions/ADR-001-dual-database]]
