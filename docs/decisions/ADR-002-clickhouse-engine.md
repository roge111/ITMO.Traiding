# ADR-002: ReplacingMergeTree для таблицы котировок

#docs #adr #database #clickhouse

**Статус:** Принято  
**Дата:** 2024  
**Участники:** Команда ITMO.Trading

---

## Контекст

В ClickHouse необходимо хранить текущее состояние каждой котировки (7 тикеров). Котировки обновляются в среднем каждые 3 секунды, Go-инжестор вставляет новую версию при изменении цены.

ClickHouse — append-only движок. Обычный `UPDATE` не поддерживается без специальных механизмов.

## Проблема

Как хранить **текущую** котировку (одну строку на тикер) при использовании append-only хранилища?

## Рассмотренные варианты

### Вариант A: MergeTree + SELECT с MAX(version)
- Каждое изменение — новая строка
- Текущее состояние: `SELECT ... WHERE version = (SELECT MAX(version) ...)`
- **+** Простая схема
- **−** Дорогие запросы (подзапрос), нет поддержки из коробки

### Вариант B: ReplacingMergeTree(version) ✓
- Каждое изменение — новая строка с увеличенным `version`
- ClickHouse фоново мержит строки, оставляя только с максимальным `version`
- Текущее состояние: `SELECT ... FINAL`
- **+** Чтение через `SELECT FINAL` — встроенный механизм
- **+** Запись через обычный INSERT — нет overhead

### Вариант C: CollapsingMergeTree / AggregatingMergeTree
- Подходит для более сложных агрегаций
- **−** Избыточная сложность для данного use-case

### Вариант D: Dictionary + периодическая полная перезапись
- **−** Не масштабируется, потеря истории

## Решение

**Принят вариант B.** Таблица `quotes` использует `ReplacingMergeTree(version)`.

```sql
CREATE TABLE IF NOT EXISTS quotes (
    quote_name  String,
    price       Float64,
    min_price   Float64,
    max_price   Float64,
    pct_change  Float64,
    last_update DateTime,
    version     UInt64
) ENGINE = ReplacingMergeTree(version)
ORDER BY quote_name;
```

Go-инжестор:
1. Читает текущую версию: `SELECT version FROM quotes FINAL WHERE quote_name = ?`
2. Вставляет новую: `INSERT INTO quotes VALUES (name, price, ..., version+1)`

Gateway читает: `SELECT * FROM quotes FINAL ORDER BY quote_name`

## Последствия

**Положительные:**
- `SELECT FINAL` гарантирует актуальность данных без агрегации в коде
- INSERT быстрый — просто добавление строки
- Версионирование встроено в движок

**Отрицательные:**
- `SELECT FINAL` может быть медленным при большом объёме неслитых данных (в данном проекте — 7 тикеров, не проблема)
- Между фоновыми мержами старые версии физически присутствуют в хранилище
- Необходимость передавать `version` при каждом INSERT усложняет Go-код (лишний SELECT перед INSERT)

## История в отдельной таблице

Для истории цен используется отдельная таблица `quotes_history` на обычном `MergeTree` — туда Go вставляет каждое изменение без дедупликации, что позволяет строить графики и свечи.

## Связанные документы

- [[../07-Database]] — полная схема таблиц ClickHouse
- [[ADR-001-dual-database]] — почему ClickHouse, а не PostgreSQL
- [[../modules/go-server]] — saveQuote() алгоритм версионирования
