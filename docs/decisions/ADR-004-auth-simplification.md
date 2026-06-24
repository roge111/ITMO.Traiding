# ADR-004: Упрощённая авторизация без JWT и сессий

#docs #adr #auth #security

**Статус:** Принято (MVP)  
**Дата:** 2024  
**Участники:** Команда ITMO.Trading

---

## Контекст

Android-приложение обращается к Gateway для торговых операций от имени пользователя. Нужен механизм идентификации пользователя в API-запросах.

Это учебный проект (ITMO). Акцент на демонстрации функциональности, а не production-grade безопасности.

## Проблема

Как идентифицировать пользователя в API-запросах после успешного входа?

## Рассмотренные варианты

### Вариант A: JWT-токены
- После login сервер выдаёт подписанный JWT
- Клиент передаёт `Authorization: Bearer <token>` в каждом запросе
- **+** Stateless, масштабируемо, индустриальный стандарт
- **+** Токен содержит срок действия, roles
- **−** Требует: генерацию секрета, middleware для проверки, refresh-логику
- **−** Overhead для MVP без требований масштабирования

### Вариант B: Cookie-сессии (Ktor Sessions)
- После login сервер создаёт server-side сессию, клиент получает cookie
- **+** Встроено в Ktor
- **+** Удобно для браузера
- **−** Android OkHttp требует явной настройки CookieJar
- **−** Server-side state (нужно хранилище сессий или sticky sessions)

### Вариант C: Login в query/form параметрах ✓
- Клиент хранит `username` в памяти после успешного login
- Каждый защищённый запрос передаёт `?login=username` как query param или form field
- Сервер не проверяет подпись — доверяет переданному username
- **+** Нулевая сложность реализации
- **+** Прозрачность запросов для отладки
- **−** Нет защиты: любой может передать чужой username

## Решение

**Принят вариант C** для MVP. Username сохраняется в памяти Android-приложения и передаётся как параметр в каждый защищённый запрос.

```kotlin
// Android ApiClient.kt
suspend fun getAccount(login: String): ApiResult<AccountSummary> {
    val request = Request.Builder()
        .url("${BASE_URL}/api/account?login=$login")
        .get().build()
    ...
}

suspend fun trade(login: String, quoteName: String, quantity: Int, side: String): ... {
    val body = FormBody.Builder()
        .add("login", login)
        .add("quoteName", quoteName)
        // ...
```

## Последствия

**Положительные:**
- Минимальная сложность — работает из коробки
- Легко тестировать через curl: `curl http://localhost:8080/api/account?login=alice`
- Нет хранения состояния на сервере

**Отрицательные / риски:**
- **Нет защиты от подмены identity** — любой клиент может запросить данные любого пользователя
- **Нет срока действия "сессии"** — username остаётся валидным бесконечно
- **Нет logout** — выход из приложения не инвалидирует доступ
- Пароли хэшируются через BCrypt (это правильно), но post-login не даёт никаких гарантий

> [!WARNING]
> Этот подход **неприемлем для production**. При переходе к боевой системе необходимо внедрить JWT или session-based auth с проверкой подписи.

## Миграционный путь (если понадобится)

1. Добавить `sessions` table в PostgreSQL
2. `POST /api/login` → создаёт запись сессии, возвращает `session_id`
3. Клиент сохраняет `session_id` (Android: EncryptedSharedPreferences)
4. Gateway middleware проверяет сессию при каждом запросе
5. Или: перейти на JWT — добавить `ktor-server-auth-jwt`, убрать `?login=` параметры

## Связанные документы

- [[../08-Authentication-and-Authorization]] — детали реализации BCrypt
- [[../flows/user-registration-flow]] — flow регистрации и входа
- [[../06-API]] — какие endpoints используют login param
