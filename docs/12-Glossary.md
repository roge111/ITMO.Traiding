# 12 — Глоссарий

#docs #glossary

## Доменные термины

| Термин | Определение |
|---|---|
| **Quote / Котировка** | Биржевой инструмент с текущей ценой, изменением в %, мин/макс ценами за период |
| **Ticker / Тикер** | Краткое буквенное обозначение котировки (SBER, YANDEX, MOEX, ...) |
| **Holding / Позиция** | Текущая позиция пользователя по инструменту: количество акций и средняя цена покупки |
| **Portfolio / Портфель** | Совокупность позиций пользователя |
| **Trade / Сделка** | Операция покупки или продажи акций |
| **Balance / Баланс** | Свободные денежные средства пользователя (стартовый — 1 000 000 у.е.) |
| **Market Impact** | Влияние сделки на цену котировки: BUY повышает цену, SELL снижает |
| **Market Tick** | Симуляция рыночного движения: случайный дрейф цены ±0.6% |
| **Candle / Свеча** | OHLC-свечной бар: Open, High, Low, Close + временная метка |
| **Snapshot / Снимок** | Одноразовое считывание текущего состояния котировок из источника |

## Технические аббревиатуры

| Аббревиатура | Расшифровка |
|---|---|
| **OHLC** | Open, High, Low, Close — параметры свечного графика |
| **BUY** | Покупка — `side` в торговой операции |
| **SELL** | Продажа — `side` в торговой операции |
| **FINAL** | Ключевое слово ClickHouse: применить дедупликацию ReplacingMergeTree перед возвратом |
| **JDBC** | Java Database Connectivity — стандартный Java API для работы с БД |
| **BCrypt** | Алгоритм хэширования паролей с встроенной солью |
| **MVP** | Minimum Viable Product — минимально жизнеспособный продукт |
| **DDL** | Data Definition Language — SQL для создания/изменения схем (CREATE, ALTER, DROP) |
| **DML** | Data Manipulation Language — SQL для работы с данными (INSERT, UPDATE, DELETE) |
| **ORM** | Object-Relational Mapping — Exposed используется как ORM для PostgreSQL |
| **DSN** | Data Source Name — строка подключения к БД |
| **HikariCP** | High-performance connection pool для JDBC |

## Внутренние понятия

| Понятие | Описание |
|---|---|
| **seen map** | In-memory дедупликация в Go-сервере: `map[quoteName]"price@unixTimestamp"` |
| **version** | UInt64-счётчик версий в ClickHouse, увеличивается при каждом изменении котировки |
| **Flyway migration** | SQL-файл вида `V{N}__{description}.sql` для версионирования схемы PostgreSQL |
| **Fat JAR / Shadow JAR** | JAR-архив со всеми зависимостями (Gradle task `buildFatJar`) |
| **entrypoint.sh** | Docker entrypoint: применяет Flyway миграции, затем запускает JAR |
| **FlywayMigrateApp** | Standalone Kotlin-класс для применения миграций как Gradle task или из entrypoint |
| **configureRouting** | Ktor-функция расширения Application, регистрирует HTTP маршруты |
| **tradingRoutes** | Функция-расширение, регистрирует торговые маршруты `/api/account`, `/api/trade`, и др. |
| **QuoteRepository** | Kotlin object для чтения котировок из ClickHouse |
| **TradingRepository** | Kotlin object для торговой бизнес-логики (account, portfolio, trade, candles) |
| **ApiClient** | Kotlin object в Android-приложении, HTTP-клиент на OkHttp + корутины |
| **ApiResult** | Sealed interface: `Success<T>` или `Error(message)` |
| **10.0.2.2** | IP-адрес хост-машины с точки зрения Android Emulator |
| **itmo_traiding_system** | Имя базы данных PostgreSQL (с опечаткой "traiding" в исходном коде) |

## Названия модулей

| Модуль | Путь | Язык |
|---|---|---|
| `generateQuotes_kernel` | `server/drivers/` | C (Linux kernel) |
| `itmo.trading` (Go module) | `server/go/` | Go |
| `com.trading` (Ktor module) | `gateway/` | Kotlin |
| `com.trading.android` | `android/` | Kotlin (Android) |

## Тикеры котировок

Генерируются kernel module (`generateQuotes_kernel.c`):

| Тикер | Прототип |
|---|---|
| GAZ | Газпром |
| YANDEX | Яндекс |
| SBER | Сбербанк |
| LUKOIL | Лукойл |
| ROSNEFT | Роснефть |
| VTBR | ВТБ |
| MOEX | Московская биржа |

---

## Связанные документы

- [[00-Overview]]
- [[01-Architecture]]
- [[07-Database]]
