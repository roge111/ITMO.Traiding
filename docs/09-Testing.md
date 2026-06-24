# 09 — Тестирование

#docs #testing

## Обзор тестового покрытия

| Тип теста | Компонент | Файл | Статус |
|---|---|---|---|
| Unit-тесты | Go server (parseQuoteLine) | `server/go/main_test.go` | ✅ Есть |
| Integration тест | Gateway (Ktor testApplication) | `gateway/src/test/kotlin/ServerTest.kt` | ✅ Есть |
| E2E тест | Весь стек | `scripts/integration-test.sh` | ✅ Есть |
| Unit-тесты (Android) | — | — | ❌ Нет |
| Unit-тесты (Gateway) | — | — | ❌ Нет |

---

## Go — Unit-тесты

**Файл:** `server/go/main_test.go`

Тестируется функция `parseQuoteLine()` — парсинг CSV строк котировок.

### Как запустить

```bash
cd server/go
go test ./...

# С verbose выводом:
go test -v ./...

# Один конкретный тест:
go test -run TestParseQuoteLine ./...
```

### Что тестируется

```go
// TestParseQuoteLine — успешный парсинг
// Входные данные: "SBER,12345,2026-06-23 12:30:00"
// Ожидается: name=SBER, price=12345, timestamp=2026-06-23T12:30:00

// TestParseQuoteLineRejectsInvalidData — табличный тест негативных сценариев:
// - "" (пустая строка)
// - "SBER,abc,2026-06-23 12:30:00" (нечисловая цена)
// - "SBER,-1,2026-06-23 12:30:00" (отрицательная цена)
// - "SBER,2026-06-23 12:30:00" (только 2 поля)
// - ",12345,2026-06-23 12:30:00" (пустое имя)
// - "SBER,12345,not-a-date" (некорректная дата)
```

---

## Gateway — Integration тест

**Файл:** `gateway/src/test/kotlin/ServerTest.kt`

Использует `ktor-server-testHost` — встроенный тестовый движок Ktor без реального HTTP-сервера.

### Как запустить

```bash
cd gateway
export POSTGRES_PASSWORD=postgres
export CLICKHOUSE_PASSWORD=itmo
./gradlew test
```

> [!WARNING]
> Тест `test root endpoint` проверяет `GET /register → 200 OK`. Однако при запуске без реального PostgreSQL/ClickHouse тест упадёт в момент инициализации `ClickHouseManager` и `DataBaseManager` (они стартуют вместе с `application.yaml` модулями).

### Текущий тест

```kotlin
class ServerTest {
    @Test
    fun `test root endpoint`() = testApplication {
        configure()
        assertEquals(HttpStatusCode.OK, client.get("/register").status)
    }
}
```

> [!NOTE]
> Покрытие тестами Gateway минимальное. Отсутствуют тесты для `/api/quotes`, `/api/trade`, `/api/register`. Это область для улучшения.

---

## E2E — Интеграционный скрипт

**Файл:** `scripts/integration-test.sh`

Полный сквозной тест всего стека.

### Что делает скрипт

1. Поднимает Docker Compose (`postgres` + `clickhouse`)
2. Применяет Flyway-миграции
3. Создаёт тестового пользователя через `/api/register`
4. Записывает котировки из `quotes.log` через Go-инжестор
5. Проверяет агрегаты в ClickHouse
6. Запускает gateway, проверяет `/api/quotes` и `/api/register`

### Как запустить

```bash
export POSTGRES_PASSWORD=postgres
chmod +x scripts/integration-test.sh
./scripts/integration-test.sh
```

**Требования:**
- Docker с Compose
- JDK 20 в PATH
- Свободные порты: 5432, 8123, 9000, 8080
- Go (опционально — если нет, инжестор запустится в Docker-контейнере)

### Переменные окружения скрипта

| Переменная | По умолчанию | Описание |
|---|---|---|
| `POSTGRES_PASSWORD` | `postgres` | Пароль PostgreSQL |
| `CLICKHOUSE_PASSWORD` | `itmo` | Пароль ClickHouse |
| `INTEGRATION_USER` | `integration_user` | Тестовый пользователь |
| `INTEGRATION_PASS` | `password123` | Пароль тестового пользователя |
| `GO_DOCKER_IMAGE` | `golang:1.22-bookworm` | Docker-образ для Go если нет в PATH |

---

## Рекомендации по расширению покрытия

### Gateway (Kotlin)

```kotlin
// Пример теста для /api/quotes (мок ClickHouse):
@Test
fun `test api quotes returns json array`() = testApplication {
    // Использовать dependency injection или мок для QuoteRepository
    client.get("/api/quotes").apply {
        assertEquals(HttpStatusCode.OK, status)
        assertEquals(ContentType.Application.Json, contentType())
    }
}

// Тест для /api/trade:
@Test
fun `test trade buy reduces balance`() = testApplication {
    // Подготовить пользователя в тестовой БД
    // Выполнить POST /api/trade
    // Проверить изменение баланса через GET /api/account
}
```

### Android

Для Android рекомендуется:
- `@Test` + `MockWebServer` (OkHttp) для `ApiClient`
- Compose `ComposeTestRule` для UI-тестов

### Go

Можно добавить тесты для:
- `processSnapshot()` — проверка дедупликации
- `saveQuote()` — интеграционный тест с реальным ClickHouse (testcontainers)

---

## Связанные документы

- [[05-Development-Guide]]
- [[03-Setup-and-Installation]]
