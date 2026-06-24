# ITMO.Trading — Документация

#docs

Главная страница документации по проекту ITMO.Trading — учебной системе сбора и отображения биржевых котировок в реальном времени.

---

## Быстрый старт

```bash
# 1. Поднять базы данных
docker compose up -d postgres clickhouse

# 2. Применить миграции PostgreSQL
cd gateway && export POSTGRES_PASSWORD=postgres && ./gradlew flywayMigrate --no-daemon

# 3. Запустить gateway (порт 8080)
export CLICKHOUSE_PASSWORD=itmo && ./gradlew run

# 4. Загрузить тестовые котировки (в другом терминале)
cd server/go
export CLICKHOUSE_PASSWORD=itmo
export QUOTES_SOURCE_PATH=../drivers/quotes.log
export PROCESS_EXISTING_AND_EXIT=true
go run .
```

После этого откройте: `http://localhost:8080/quotes`

---

## Карта документации

### Основные разделы

| Документ | Описание |
|---|---|
| [[00-Overview]] | Что делает проект, для кого, основные технологии |
| [[01-Architecture]] | Архитектура, компоненты, data flow, Mermaid-диаграммы |
| [[02-Project-Structure]] | Структура директорий, назначение файлов |
| [[03-Setup-and-Installation]] | Требования, установка, запуск локально |
| [[04-Configuration]] | Все env-переменные, конфигурационные файлы |
| [[05-Development-Guide]] | Команды разработки, паттерны, как добавлять функциональность |
| [[06-API]] | Все HTTP endpoints с описанием параметров |
| [[07-Database]] | Схемы PostgreSQL и ClickHouse, миграции |
| [[08-Authentication-and-Authorization]] | Регистрация, авторизация, BCrypt |
| [[09-Testing]] | Тесты: unit, integration, как запускать |
| [[10-Deployment]] | Docker, docker-compose, production |
| [[11-Troubleshooting]] | Частые ошибки и решения |
| [[12-Glossary]] | Термины, аббревиатуры, доменные сущности |

### Модули

| Документ | Описание |
|---|---|
| [[modules/README]] | Индекс модулей |
| [[modules/kernel-module]] | Linux kernel module — генератор котировок |
| [[modules/go-server]] | Go-сервер — инжестор котировок в ClickHouse |
| [[modules/gateway]] | Kotlin/Ktor gateway — REST API |
| [[modules/android-app]] | Android-приложение (Jetpack Compose) |

### User Flows

| Документ | Описание |
|---|---|
| [[flows/README]] | Индекс сценариев |
| [[flows/quote-ingestion-flow]] | Путь котировки: kernel → ClickHouse |
| [[flows/user-registration-flow]] | Регистрация и вход пользователя |
| [[flows/trading-flow]] | Покупка и продажа акций |

### Архитектурные решения (ADR)

| Документ | Описание |
|---|---|
| [[decisions/README]] | Индекс ADR |
| [[decisions/ADR-001-dual-database]] | Разделение ClickHouse (котировки) и PostgreSQL (пользователи) |
| [[decisions/ADR-002-clickhouse-engine]] | Выбор ReplacingMergeTree для котировок |
| [[decisions/ADR-003-go-ingestor]] | Отдельный Go-сервис для инжестии данных |
| [[decisions/ADR-004-auth-simplification]] | Упрощённая авторизация без JWT |

---

## Ключевые сущности проекта

- **Quote** — биржевая котировка (тикер, цена, мин/макс, % изменения)
- **User** — пользователь с балансом 1 000 000 у.е. при регистрации
- **Portfolio** — портфель пользователя (позиции по инструментам)
- **Trade** — операция покупки/продажи
- **Candle** — свечной график (OHLC), синтезируется из истории котировок
- **/dev/itmo_quotes** — символьное устройство ядра Linux, источник котировок

---

## Связанные документы

- [[01-Architecture]]
- [[06-API]]
- [[07-Database]]
