# Flow: Покупка и продажа акций

#docs #flow #trading

## Цель сценария

Авторизованный пользователь совершает торговую операцию — покупает или продаёт акции.

## Участники

| Участник | Компонент |
|---|---|
| Пользователь | Android App |
| Gateway (Ktor) | `gateway/src/main/kotlin/trading/` |
| TradingRepository | `TradingRepository.kt` |
| PostgreSQL | Таблицы `users`, `portfolio`, `trades` |
| ClickHouse | Таблица `quotes` (текущие цены) |

## Покупка (BUY)

```mermaid
sequenceDiagram
    participant U as Пользователь
    participant APP as Android App
    participant GW as Ktor Gateway
    participant TR as TradingRepository
    participant PG as PostgreSQL
    participant CH as ClickHouse

    U->>APP: Выбрать котировку → ввести количество → Buy
    APP->>GW: POST /api/trade<br/>(login, quoteName, quantity, side=BUY)
    GW->>TR: trade(username, quoteName, quantity, "BUY")
    
    TR->>CH: SELECT price FROM quotes FINAL<br/>WHERE quote_name = ?
    CH->>TR: currentPrice
    
    TR->>PG: BEGIN TRANSACTION
    TR->>PG: SELECT balance FROM users WHERE username = ?
    PG->>TR: balance
    
    TR->>TR: total = price × quantity
    alt Недостаточно средств
        TR->>PG: ROLLBACK
        TR->>GW: "Insufficient funds"
        GW->>APP: 400 Bad Request
    end
    
    TR->>PG: UPDATE users SET balance = balance - total
    TR->>PG: INSERT INTO trades (username, quoteName, BUY, quantity, price, total)
    TR->>PG: INSERT OR UPDATE portfolio<br/>(пересчёт avg_price)
    TR->>PG: COMMIT
    
    TR->>TR: Вернуть новый баланс + обновлённый Holding
    TR->>GW: TradeResult(message, balance, holding)
    GW->>APP: 200 OK + JSON
    APP->>APP: Обновить отображение баланса и портфеля
```

## Продажа (SELL)

```mermaid
sequenceDiagram
    participant U as Пользователь
    participant APP as Android App
    participant GW as Ktor Gateway
    participant TR as TradingRepository
    participant PG as PostgreSQL
    participant CH as ClickHouse

    U->>APP: Выбрать котировку → ввести количество → Sell
    APP->>GW: POST /api/trade<br/>(login, quoteName, quantity, side=SELL)
    GW->>TR: trade(username, quoteName, quantity, "SELL")
    
    TR->>CH: SELECT price FROM quotes FINAL<br/>WHERE quote_name = ?
    CH->>TR: currentPrice
    
    TR->>PG: BEGIN TRANSACTION
    TR->>PG: SELECT quantity FROM portfolio<br/>WHERE username = ? AND quote_name = ?
    PG->>TR: ownedQuantity
    
    alt Недостаточно акций
        TR->>PG: ROLLBACK
        TR->>GW: "Insufficient shares"
        GW->>APP: 400 Bad Request
    end
    
    TR->>TR: total = price × quantity
    TR->>PG: UPDATE users SET balance = balance + total
    TR->>PG: INSERT INTO trades (username, quoteName, SELL, quantity, price, total)
    
    alt quantity == ownedQuantity
        TR->>PG: DELETE FROM portfolio WHERE ...
    else quantity < ownedQuantity
        TR->>PG: UPDATE portfolio SET quantity = quantity - ?
    end
    
    TR->>PG: COMMIT
    
    TR->>GW: TradeResult(message, balance, holding=null if sold all)
    GW->>APP: 200 OK + JSON
    APP->>APP: Обновить отображение
```

## Просмотр портфеля

```mermaid
sequenceDiagram
    participant APP as Android App
    participant GW as Ktor Gateway
    participant TR as TradingRepository
    participant PG as PostgreSQL
    participant CH as ClickHouse

    APP->>GW: GET /api/portfolio?login=username
    GW->>TR: portfolio(username)
    
    TR->>PG: SELECT * FROM portfolio WHERE username = ?
    PG->>TR: список позиций
    
    loop Для каждой позиции
        TR->>CH: SELECT price FROM quotes FINAL<br/>WHERE quote_name = ?
        CH->>TR: currentPrice
        TR->>TR: marketValue = quantity × currentPrice<br/>profit = marketValue - (quantity × avgPrice)
    end
    
    TR->>GW: List<Holding>
    GW->>APP: 200 OK + JSON array
    APP->>APP: Отобразить портфель с P&L
```

## Файлы участвующие в flow

| Файл | Роль |
|---|---|
| `gateway/src/main/kotlin/trading/TradingRoutes.kt` | HTTP endpoint `POST /api/trade` |
| `gateway/src/main/kotlin/trading/TradingRepository.kt` | Транзакционная бизнес-логика |
| `gateway/src/main/kotlin/trading/TradingModels.kt` | `TradeResult`, `Holding`, `AccountSummary` |
| `gateway/src/main/kotlin/database/DataBaseManager.kt` | PostgreSQL JDBC соединение |
| `gateway/src/main/kotlin/database/ClickHouseManager.kt` | ClickHouse соединение (текущие цены) |
| `android/app/src/main/java/com/trading/android/ApiClient.kt` | `trade()`, `getPortfolio()`, `getAccount()` |
| `android/app/src/main/java/com/trading/android/MainActivity.kt` | Quote Details экран с формой торговли |

## Алгоритм пересчёта avg_price при покупке

```
Если позиция уже существует:
  newQty = existingQty + buyQty
  newAvgPrice = (existingQty × existingAvgPrice + buyQty × currentPrice) / newQty
  UPDATE portfolio SET quantity = newQty, avg_price = newAvgPrice

Если позиции нет:
  INSERT INTO portfolio (username, quote_name, quantity, avg_price)
  VALUES (?, ?, buyQty, currentPrice)
```

## Ошибки и альтернативные пути

| Сбой | HTTP-код | Сообщение |
|---|---|---|
| Недостаточно средств | 400 | "Insufficient funds" |
| Недостаточно акций | 400 | "Insufficient shares" |
| Котировка не найдена | 400 | "Quote not found" |
| Неверный side | 400 | Ошибка парсинга |
| PostgreSQL недоступен | 500 | Exception |
| ClickHouse недоступен | 500 | Exception |

## Открытые вопросы

1. Нет частичного выполнения ордеров — только полная покупка/продажа.
2. Цена фиксируется в момент запроса к ClickHouse, нет защиты от проскальзывания.
3. Нет истории ордеров в UI — только в таблице `trades`.

## Связанные документы

- [[../06-API]] — описание POST /api/trade
- [[../07-Database]] — схема portfolio и trades
- [[../modules/gateway]] — TradingRepository
- [[../modules/android-app]] — trade() метод
