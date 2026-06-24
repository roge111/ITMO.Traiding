# Architecture Decision Records

#docs #adr

Индекс архитектурных решений (ADR) проекта ITMO.Trading.

ADR описывают **почему** было принято то или иное архитектурное решение: контекст, альтернативы, последствия.

| ADR | Решение | Статус |
|---|---|---|
| [[ADR-001-dual-database]] | Разделение хранилищ: ClickHouse (котировки) + PostgreSQL (пользователи) | Принято |
| [[ADR-002-clickhouse-engine]] | ReplacingMergeTree для текущих котировок | Принято |
| [[ADR-003-go-ingestor]] | Отдельный Go-сервис для инжестии котировок | Принято |
| [[ADR-004-auth-simplification]] | Упрощённая авторизация без JWT/сессий | Принято (MVP) |

---

## Связанные документы

- [[../01-Architecture]]
- [[../07-Database]]
