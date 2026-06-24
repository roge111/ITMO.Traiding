# Flow: Путь котировки от генерации до UI

#docs #flow

## Цель сценария

Котировка, сгенерированная Linux kernel module, должна появиться в Android-приложении пользователя.

## Участники

| Участник | Компонент |
|---|---|
| Kernel Module | `server/drivers/generateQuotes_kernel.c` |
| Go Ingestor | `server/go/main.go` |
| ClickHouse | База данных (tables: `quotes`, `quotes_history`) |
| Ktor Gateway | `gateway/src/main/kotlin/` |
| Android App | `android/app/` |

## Пошаговое описание

```mermaid
sequenceDiagram
    participant KM as Kernel Module
    participant DEV as /dev/itmo_quotes
    participant GO as Go Ingestor
    participant CH as ClickHouse
    participant GW as Ktor Gateway
    participant APP as Android App

    loop Каждые 3 секунды
        KM->>KM: update_random_quote()<br/>случайная цена 1000-500000
    end

    loop Каждую секунду
        GO->>DEV: os.Open("/dev/itmo_quotes")
        DEV->>GO: snapshot: 7 CSV строк
        GO->>DEV: os.Close()
        
        loop Для каждой строки
            GO->>GO: parseQuoteLine(line)<br/>NAME,PRICE,DATETIME
            GO->>GO: Проверить seen map<br/>signature = price@timestamp
            alt Новая/изменённая котировка
                GO->>CH: SELECT FINAL WHERE quote_name = ?
                CH->>GO: текущие данные (или ErrNoRows)
                GO->>GO: вычислить min, max, %change, version+1
                GO->>CH: INSERT INTO quotes (версионирование)
                GO->>CH: INSERT INTO quotes_history
                GO->>GO: seen[name] = signature
            else Не изменилась
                GO->>GO: skip
            end
        end
    end

    APP->>GW: GET /api/quotes
    GW->>CH: SELECT * FROM quotes FINAL ORDER BY quote_name
    CH->>GW: список актуальных котировок
    GW->>APP: JSON array [Quote, ...]
    APP->>APP: Отобразить список котировок
```

## Файлы и модули

| Шаг | Файл | Функция |
|---|---|---|
| Генерация | `server/drivers/generateQuotes_kernel.c` | `update_random_quote()`, `generator()` |
| Чтение | `server/go/main.go` | `readSnapshot()` |
| Парсинг | `server/go/main.go` | `parseQuoteLine()` |
| Дедупликация | `server/go/main.go` | `processSnapshot()` |
| Запись | `server/go/main.go` | `saveQuote()`, `saveQuoteHistory()` |
| Чтение API | `gateway/src/main/kotlin/quotes/QuoteReposetory.kt` | `getQuotesDB()` |
| HTTP endpoint | `gateway/src/main/kotlin/Routing.kt` | `configureRouting()` |
| Android UI | `android/app/src/main/java/com/trading/android/` | `ApiClient.getQuotes()` |

## Временные характеристики

- Kernel: обновляет **одну случайную** котировку каждые 3 секунды
- Go-инжестор: опрашивает источник каждую **1 секунду**
- Android: обновляет список при каждом запуске экрана котировок (`LaunchedEffect(Unit)`)

## Ошибки и альтернативные пути

| Сбой | Поведение |
|---|---|
| Kernel module не загружен | Go: `open /dev/itmo_quotes: no such file` → логирует и ждёт 1 сек |
| Некорректная строка CSV | Go: логирует предупреждение, пропускает строку |
| ClickHouse недоступен | Go: `log.Printf` + retry через 1 сек |
| Цена не изменилась | Go: seen map → строка пропускается (нет записи в CH) |
| Gateway не запущен | Android: `ApiResult.Error("Ошибка сети")` |

## Связанные документы

- [[../modules/kernel-module]]
- [[../modules/go-server]]
- [[../modules/gateway]]
- [[../07-Database]]
- [[../01-Architecture]]
