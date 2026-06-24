# 00 — Обзор проекта

#docs #overview

## Что делает проект

ITMO.Trading — учебная система для сбора, обработки и отображения биржевых котировок в реальном времени. Система позволяет:

- Генерировать котировки через Linux kernel module (`/dev/itmo_quotes`)
- Собирать котировки в time-series БД (ClickHouse)
- Просматривать котировки, историю цен и свечные графики через REST API
- Регистрироваться, пополнять виртуальный баланс и торговать акциями
- Управлять портфелем через Android-приложение

## Для кого предназначен

Проект разработан в рамках учебного курса ИТМО. Аудитория:

- Студенты, изучающие системное программирование Linux
- Разработчики, изучающие Kotlin/Ktor, ClickHouse, Android
- Команды, знакомящиеся с микросервисной архитектурой

## Основные возможности

| Возможность | Где реализовано |
|---|---|
| Генерация котировок (7 тикеров: GAZ, YANDEX, SBER, LUKOIL, ROSNEFT, VTBR, MOEX) | `server/drivers/generateQuotes_kernel.c` |
| Запись котировок в ClickHouse с версионированием | `server/go/main.go` |
| REST API котировок (JSON и HTML) | `gateway/src/main/kotlin/Routing.kt` |
| Регистрация и авторизация пользователей | `gateway/src/main/kotlin/users/` |
| Торговля (BUY/SELL) с транзакциями | `gateway/src/main/kotlin/trading/TradingRepository.kt` |
| История цен и свечные графики | `gateway/src/main/kotlin/trading/TradingRepository.kt` |
| Симуляция рыночных движений | `TradingRepository.marketTick()` |
| Android-клиент (Jetpack Compose) | `android/app/` |

## Высокоуровневое описание работы

```
Kernel Module (/dev/itmo_quotes)
  │   Генерирует 7 котировок каждые 3 секунды
  ▼
Go Server (server/go/main.go)
  │   Читает снимок котировок (open→read→close)
  │   Парсит CSV: NAME,PRICE,DATETIME
  │   Дедупликация через in-memory map
  │   Записывает в ClickHouse
  ▼
ClickHouse
  │   quotes (ReplacingMergeTree) — актуальные котировки
  │   quotes_history (MergeTree) — полная история
  ▼
Ktor Gateway (gateway/, порт 8080)
  │   GET /api/quotes → читает quotes FINAL
  │   POST /api/trade → транзакция в PostgreSQL + update ClickHouse
  ▼
Android App / Browser
    Отображает котировки, портфель, графики
```

## Состояние (MVP)

> [!NOTE]
> Проект находится в статусе MVP. Реализован минимальный сквозной сценарий.

**Реализовано:**
- Полный data pipeline от kernel module до Android UI
- Регистрация / авторизация (BCrypt, без JWT)
- Покупка и продажа акций с обновлением баланса
- История цен и свечи (синтезируются из `quotes_history`)
- Docker Compose для локальной разработки

**Не входит в MVP:**
- JWT/сессии — авторизация передаёт `login` в query params (учебное ограничение)
- Redis/KeyDB для кэширования
- OpenTelemetry / Prometheus / Grafana
- Имитатор нагрузки (10 000 клиентов)
- React Native клиент

## Основные технологии

| Слой | Технологии |
|---|---|
| Kernel module | C, Linux kernel API, kthread, mutex, miscdevice |
| Data ingestion | Go 1.22, `clickhouse-go/v2` (native protocol) |
| Backend API | Kotlin, Ktor 3.x, Netty, Kotlin Serialization |
| PostgreSQL ORM | Exposed 0.56 + raw JDBC + Flyway 10 |
| ClickHouse client | ClickHouse JDBC 0.6.5 + HikariCP |
| Password hashing | jBCrypt 0.4 |
| Mobile | Kotlin, Jetpack Compose, OkHttp 4, API 34 |
| Infrastructure | Docker, Docker Compose, Gradle (Kotlin DSL) |
| Testing | Go `testing`, Ktor `testApplication`, bash |

---

## Связанные документы

- [[01-Architecture]]
- [[03-Setup-and-Installation]]
- [[06-API]]
- [[07-Database]]
