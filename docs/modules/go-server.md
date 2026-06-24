# Модуль: Go Server (Инжестор котировок)

#docs #module #go

## Назначение

Читает котировки из источника (kernel module или файл), парсит CSV-строки и записывает данные в ClickHouse. Реализует дедупликацию через in-memory map.

## Расположение

```
server/go/
├── main.go        # Вся логика инжестора
├── main_test.go   # Unit-тесты parseQuoteLine
├── Dockerfile     # Multi-stage Docker build
├── go.mod         # module itmo.trading, go 1.22.2
└── go.sum
```

## Зависимости

```
github.com/ClickHouse/clickhouse-go/v2  # Native protocol ClickHouse driver
```

## Основные функции

| Функция | Строки | Описание |
|---|---|---|
| `main()` | 268–295 | Точка входа: соединение с CH, основной цикл |
| `openClickHouse()` | 54–86 | Подключение к ClickHouse, проверка Ping, создание схемы |
| `ensureSchema()` | 88–129 | CREATE TABLE IF NOT EXISTS для quotes и quotes_history |
| `parseQuoteLine()` | 131–157 | Парсинг CSV: `NAME,PRICE,DATETIME` → `inputQuote` |
| `readSnapshot()` | 161–184 | open→ReadAll→close, построчный парсинг |
| `saveQuote()` | 186–238 | SELECT FINAL + INSERT с версионированием |
| `processSnapshot()` | 248–266 | Дедупликация через `seen` map + вызов saveQuote |

## Структура данных

```go
type inputQuote struct {
    name      string
    price     int32
    timestamp time.Time
}
```

## Алгоритм дедупликации

```go
// seen map: ключ = quote name, значение = "price@unixTimestamp"
seen := make(map[string]string)

for _, quote := range quotes {
    signature := fmt.Sprintf("%d@%d", quote.price, quote.timestamp.Unix())
    if seen[quote.name] == signature {
        continue  // та же цена и время — пропустить
    }
    saveQuote(db, table, quote)
    seen[quote.name] = signature
}
```

## Алгоритм saveQuote

```
1. SELECT last_cost, min_cost, max_cost, created_at, version 
   FROM quotes FINAL WHERE quote_name = ?

2. Если нет строки (sql.ErrNoRows):
   INSERT с version=1, percentage_change=0.0
   saveQuoteHistory(version=1)

3. Если есть строка:
   Вычислить new_min, new_max, percentage_change
   INSERT с version+1 (старая строка остаётся, CH уберёт при FINAL)
   saveQuoteHistory(version+1)
```

> [!NOTE]
> ReplacingMergeTree не удаляет старые версии немедленно. `SELECT FINAL` выполняет дедупликацию on-the-fly. Фоновое слияние происходит асинхронно.

## Формат входных данных

```
SBER,12345,2026-06-23 12:30:00
YANDEX,98765,2026-06-23 12:31:00
```

- Поля разделены запятой
- Цена — целое число > 0
- Timestamp — формат `2006-01-02 15:04:05` (Go reference time), парсится в local timezone

## Режимы работы

| Режим | Переменная | Поведение |
|---|---|---|
| Непрерывный (production) | — | Читает источник каждую секунду, не завершается |
| Одноразовый (CI/тест) | `PROCESS_EXISTING_AND_EXIT=true` | Обрабатывает один снимок, выходит |

## Как компонент взаимодействует с другими

```
[Kernel Module /dev/itmo_quotes]
    ↓ os.Open() → io.ReadAll() → os.Close()
[Go Server main.go]
    ↓ sql.Open("clickhouse", ...) + INSERT
[ClickHouse quotes + quotes_history tables]
    ↓ читается Gateway через JDBC
[Ktor Gateway]
```

## Edge Cases

- Пустые строки в файле — пропускаются с предупреждением в лог
- Строки с некорректным форматом — пропускаются с лог-сообщением
- Цена ≤ 0 — строка отклоняется (`parseQuoteLine` возвращает ошибку)
- Ошибка INSERT в ClickHouse — логируется, следующие котировки продолжают обрабатываться
- При `PROCESS_EXISTING_AND_EXIT=true` и ошибке чтения — `os.Exit(1)`

## Связанные документы

- [[kernel-module]] — источник данных
- [[../07-Database]] — схема ClickHouse таблиц
- [[../flows/quote-ingestion-flow]] — full flow
- [[../09-Testing]] — unit-тесты
