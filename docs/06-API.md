# 06 — API Reference

#docs #api

Все endpoints реализованы в Kotlin/Ktor Gateway (порт 8080).

> [!NOTE]
> Авторизация в торговых endpoints передаётся через параметр `login` (query или form field). JWT/сессии не реализованы (MVP ограничение).

---

## Котировки

### GET /quotes

Возвращает HTML-таблицу текущих котировок.

| | |
|---|---|
| **Method** | `GET` |
| **Path** | `/quotes` |
| **Авторизация** | Нет |
| **Response** | `text/html` |
| **Реализация** | `gateway/src/main/kotlin/Routing.kt` |

**Пример:** `http://localhost:8080/quotes`

---

### GET /api/quotes

Возвращает список текущих котировок в JSON.

| | |
|---|---|
| **Method** | `GET` |
| **Path** | `/api/quotes` |
| **Авторизация** | Нет |
| **Response** | `application/json` — массив объектов `Quote` |
| **Реализация** | `gateway/src/main/kotlin/Routing.kt` → `QuoteRepository.getQuotesDB()` |
| **Источник данных** | `SELECT * FROM quotes FINAL ORDER BY quote_name` (ClickHouse) |

**Response Body:**
```json
[
  {
    "name": "SBER",
    "price": 12345.0,
    "percentageChange": 1.23,
    "minCost": 10000,
    "maxCost": 15000
  }
]
```

**Ошибки:** нет (возвращает пустой массив если данных нет)

---

### GET /api/quotes/{name}/history

История цен для котировки (последние 30 записей).

| | |
|---|---|
| **Method** | `GET` |
| **Path** | `/api/quotes/{name}/history` |
| **Параметры** | `name` (path) — тикер котировки |
| **Авторизация** | Нет |
| **Response** | `application/json` — массив `PricePoint` |
| **Реализация** | `TradingRoutes.kt` → `TradingRepository.history()` |
| **Источник данных** | `quotes_history` (ClickHouse), последние 30 записей |

**Response Body:**
```json
[
  { "price": 12300.0, "timestamp": "2026-06-23 12:30:00.0" },
  { "price": 12345.0, "timestamp": "2026-06-23 12:31:00.0" }
]
```

**Ошибки:**
- `400 Bad Request` — `name` не указан
- `400 Bad Request` — ошибка чтения из ClickHouse

---

### GET /api/quotes/{name}/candles

Свечной график (OHLC) для котировки. Синтезируется из `quotes_history`.

| | |
|---|---|
| **Method** | `GET` |
| **Path** | `/api/quotes/{name}/candles` |
| **Параметры** | `name` (path) — тикер котировки |
| **Авторизация** | Нет |
| **Response** | `application/json` — массив `Candle` |
| **Реализация** | `TradingRoutes.kt` → `TradingRepository.candles()` |

**Response Body:**
```json
[
  {
    "open": 12300.0,
    "high": 12400.0,
    "low": 12200.0,
    "close": 12345.0,
    "timestamp": "2026-06-23 12:31:00.0"
  }
]
```

> [!NOTE]
> Свечи синтезируются: каждая пара соседних точек истории разбивается на 4 субсвечи. При единственной точке — одна свеча с ±1% spread.

**Ошибки:**
- `400 Bad Request` — `name` не указан

---

## Аккаунт и портфель

### GET /api/account

Информация об аккаунте пользователя: баланс, стоимость портфеля.

| | |
|---|---|
| **Method** | `GET` |
| **Path** | `/api/account?login={username}` |
| **Query параметры** | `login` — имя пользователя |
| **Авторизация** | `login` query param (MVP) |
| **Response** | `application/json` — объект `AccountSummary` |
| **Реализация** | `TradingRoutes.kt` → `TradingRepository.account()` |

**Response Body:**
```json
{
  "username": "alice",
  "balance": 985000.0,
  "portfolioValue": 24690.0,
  "totalAssets": 1009690.0
}
```

**Ошибки:**
- `400 Bad Request` — `login` не указан или пользователь не найден

---

### GET /api/portfolio

Список позиций портфеля пользователя.

| | |
|---|---|
| **Method** | `GET` |
| **Path** | `/api/portfolio?login={username}` |
| **Query параметры** | `login` — имя пользователя |
| **Авторизация** | `login` query param (MVP) |
| **Response** | `application/json` — массив `Holding` |
| **Реализация** | `TradingRoutes.kt` → `TradingRepository.portfolio()` |

**Response Body:**
```json
[
  {
    "quoteName": "SBER",
    "quantity": 2,
    "avgPrice": 12300.0,
    "currentPrice": 12345.0,
    "marketValue": 24690.0,
    "profit": 90.0
  }
]
```

**Ошибки:**
- `400 Bad Request` — `login` не указан

---

## Торговые операции

### POST /api/trade

Выполнить торговую операцию (покупка или продажа).

| | |
|---|---|
| **Method** | `POST` |
| **Path** | `/api/trade` |
| **Content-Type** | `application/x-www-form-urlencoded` |
| **Авторизация** | `login` form param (MVP) |
| **Response** | `application/json` — объект `TradeResult` |
| **Реализация** | `TradingRoutes.kt` → `TradingRepository.trade()` |

**Request Body (form fields):**

| Поле | Тип | Обязательно | Описание |
|---|---|---|---|
| `login` | string | Да | Имя пользователя |
| `quoteName` | string | Да | Тикер котировки |
| `quantity` | int | Да | Количество акций (> 0) |
| `side` | string | Да | `BUY` или `SELL` |

**Response Body:**
```json
{
  "message": "BUY completed",
  "balance": 985000.0,
  "holding": {
    "quoteName": "SBER",
    "quantity": 2,
    "avgPrice": 12300.0,
    "currentPrice": 12345.0,
    "marketValue": 24690.0,
    "profit": 90.0
  }
}
```

**Ошибки:**
- `400 Bad Request` — недостаточно параметров, недостаточно баланса, недостаточно акций, котировка не найдена

---

### POST /api/market/tick

Симулировать рыночное движение цены.

| | |
|---|---|
| **Method** | `POST` |
| **Path** | `/api/market/tick` |
| **Content-Type** | `application/x-www-form-urlencoded` |
| **Response** | `application/json` — `{"status": "ok"}` |
| **Реализация** | `TradingRoutes.kt` → `TradingRepository.marketTick()` |

**Request Body (form fields):**

| Поле | Тип | Обязательно | Описание |
|---|---|---|---|
| `quoteName` | string | Нет | Тикер; если пусто — обновить все котировки |

Изменение цены: случайный дрейф ±0.6% от текущей цены.

---

## Авторизация

### GET /register

HTML-форма регистрации.

| | |
|---|---|
| **Method** | `GET` |
| **Path** | `/register` |
| **Response** | `text/html` |
| **Реализация** | `RegisterRoutes.kt` |

---

### POST /api/register

Зарегистрировать нового пользователя.

| | |
|---|---|
| **Method** | `POST` |
| **Path** | `/api/register` |
| **Content-Type** | `application/x-www-form-urlencoded` |
| **Response** | `200 OK` — `"User registered successfully."` |

**Request Body:**

| Поле | Ограничения |
|---|---|
| `login` | 3–50 символов |
| `password` | Минимум 6 символов |

**Ошибки:**
- `400` — невалидный login/password или пользователь уже существует

---

### GET /authorization

HTML-форма входа.

---

### POST /api/login

Авторизоваться (проверить пароль).

| | |
|---|---|
| **Method** | `POST` |
| **Path** | `/api/login` |
| **Content-Type** | `application/x-www-form-urlencoded` |
| **Response** | `200 OK` — `"Login successful"` |

**Ошибки:**
- `401 Unauthorized` — неверный пароль
- `400` — отсутствует login/password

---

## Связанные документы

- [[08-Authentication-and-Authorization]]
- [[07-Database]]
- [[05-Development-Guide]]
- [[flows/trading-flow]]
- [[flows/user-registration-flow]]
