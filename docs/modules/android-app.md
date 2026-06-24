# Модуль: Android App

#docs #module #android

## Назначение

Нативное Android-приложение на Kotlin + Jetpack Compose. Предоставляет пользователю интерфейс для просмотра котировок, управления портфелем и совершения торговых операций.

## Расположение

```
android/app/src/main/java/com/trading/android/
├── MainActivity.kt   # Compose UI: экраны Login, Quotes, Quote Details
├── ApiClient.kt      # HTTP-клиент на OkHttp + корутины
└── Quote.kt          # Data classes: Quote, Holding, Candle, AccountSummary, ...
```

## Технологии

| Технология | Версия | Назначение |
|---|---|---|
| Kotlin | — | Язык разработки |
| Jetpack Compose | BOM 2024.02.00 | UI фреймворк |
| Material3 | — | Design system |
| OkHttp | 4.12.0 | HTTP-клиент |
| Kotlin Coroutines | 1.7.3 | Асинхронность |
| compileSdk / targetSdk | 34 | Android API level |
| minSdk | 26 | Минимальный Android 8.0 |

## Конфигурация

**Файл:** `android/app/build.gradle.kts`

```kotlin
buildConfigField("String", "BASE_URL", "\"http://10.0.2.2:8080\"")
```

`10.0.2.2` — alias хост-машины в Android Emulator.

## ApiClient

Singleton (`object`). Все методы — `suspend fun` (выполняются в корутинах).

| Метод | HTTP | Path | Описание |
|---|---|---|---|
| `login()` | POST | `/api/login` | Авторизация |
| `register()` | POST | `/api/register` | Регистрация |
| `getQuotes()` | GET | `/api/quotes` | Список котировок |
| `getAccount()` | GET | `/api/account?login=` | Баланс аккаунта |
| `getPortfolio()` | GET | `/api/portfolio?login=` | Портфель |
| `getHistory()` | GET | `/api/quotes/{name}/history` | История цен |
| `getCandles()` | GET | `/api/quotes/{name}/candles` | Свечной график |
| `trade()` | POST | `/api/trade` | Торговая операция |
| `tickMarket()` | POST | `/api/market/tick` | Симуляция рынка |

Все запросы используют `suspendCancellableCoroutine` для интеграции OkHttp callbacks с Kotlin Coroutines.

## Data Classes

```kotlin
data class Quote(name, price, percentageChange, minCost, maxCost)
data class AccountSummary(username, balance, portfolioValue, totalAssets)
data class Holding(quoteName, quantity, avgPrice, currentPrice, marketValue, profit)
data class TradeResult(message, balance, holding: Holding?)
data class PricePoint(price, timestamp)
data class Candle(open, high, low, close, timestamp)
```

Эти классы зеркалируют JSON-ответы gateway.

## UI-экраны (MainActivity.kt)

Навигация через `var screen` state variable:

| Экран | Триггер | Описание |
|---|---|---|
| Login | Старт | Форма входа и регистрации |
| Quotes | После входа | Список котировок с ценами |
| Quote Details | Клик на котировку | История, свечи, торговля |

Паттерн загрузки данных:
```kotlin
LaunchedEffect(Unit) {
    val result = ApiClient.getQuotes()
    when (result) {
        is ApiResult.Success -> quotes = result.value
        is ApiResult.Error -> errorMessage = result.message
    }
}
```

## Авторизация в Android

После успешного `login()` / `register()` username сохраняется в переменной состояния и передаётся в каждый последующий запрос как параметр `login=`. Никакого токена или cookie не сохраняется.

## Сетевые права (AndroidManifest.xml)

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

Разрешение `INTERNET` объявлено для доступа к API.

## Edge Cases

- Сетевые ошибки возвращают `ApiResult.Error("Ошибка сети: ...")` — отображаются пользователю
- При 4xx/5xx ответах — тело ответа преобразуется в сообщение об ошибке
- `suspendCancellableCoroutine` + `invokeOnCancellation { call.cancel() }` — корректная отмена при уходе с экрана

## Связанные документы

- [[../06-API]] — API которые использует клиент
- [[../08-Authentication-and-Authorization]] — механизм auth
- [[../03-Setup-and-Installation]] — как запустить в эмуляторе
