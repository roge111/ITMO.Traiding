# 08 — Аутентификация и авторизация

#docs #auth

## Обзор

В проекте реализована **упрощённая авторизация** без JWT/сессий. Это учебное ограничение MVP.

| Механизм | Статус |
|---|---|
| BCrypt хэширование паролей | ✅ Реализовано |
| Сессии / cookies | ❌ Не реализовано |
| JWT / Bearer tokens | ❌ Не реализовано |
| OAuth | ❌ Не реализовано |
| Middleware/guards на endpoints | ❌ Не реализовано |
| Роли / права доступа | ❌ Не реализовано |

> [!WARNING]
> Торговые API принимают `login` как открытый параметр без верификации сессии. Любой знающий имя пользователя может выполнять операции от его имени. Для production необходимо добавить JWT или сессионный токен.

---

## Регистрация

**Файл:** `gateway/src/main/kotlin/users/Register.kt`

### Алгоритм

```mermaid
flowchart TD
    A[POST /api/register] --> B{login 3-50 символов?}
    B -- нет --> C[400: Login must be 3-50 chars]
    B -- да --> D{password >= 6 символов?}
    D -- нет --> E[400: Password must be 6+ chars]
    D -- да --> F{Пользователь существует?}
    F -- да --> G[400: User already registered]
    F -- нет --> H[BCrypt.hashpw(password, gensalt)]
    H --> I[INSERT INTO users (username, password_hash)]
    I --> J[200: User registered successfully]
```

### Код

```kotlin
// Register.kt
fun register(login: String, password: String): String {
    if (login.length !in 3..50) return "Login must contain from 3 to 50 characters."
    if (password.length < 6) return "Password must contain at least 6 characters."
    if (checkUser(login)) return "The user is already registered."

    val hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt())
    db.execute("INSERT INTO users (username, password_hash) VALUES (?, ?)", login, hashedPassword)
    return "User registered successfully."
}
```

**Начальный баланс:** При регистрации `balance` не задаётся явно — используется дефолт PostgreSQL `1 000 000.00` (после миграции V3).

---

## Авторизация (вход)

**Файл:** `gateway/src/main/kotlin/users/Authorization.kt`

### Алгоритм

```kotlin
// Authorization.kt
fun authorization(login: String, password: String): Boolean {
    // SELECT password_hash FROM users WHERE username = ?
    // BCrypt.checkpw(password, storedHash)
    return checkUser(login, password)
}
```

При успехе gateway возвращает `200 OK` с текстом `"Login successful"`. Сессия не создаётся.

---

## Android-клиент

**Файл:** `android/app/src/main/java/com/trading/android/ApiClient.kt`

```kotlin
// Регистрация
suspend fun register(username: String, password: String): ApiResult<Unit>

// Вход
suspend fun login(username: String, password: String): ApiResult<Unit>
```

Оба метода отправляют `POST` с `application/x-www-form-urlencoded`. После успешного входа Android хранит `username` в памяти и передаёт его в последующих запросах.

---

## Торговые endpoints — авторизация

Все торговые endpoints принимают `login` как обязательный параметр:

```
GET  /api/account?login=alice
GET  /api/portfolio?login=alice
POST /api/trade  (form: login=alice, ...)
```

Параметр `login` не верифицируется через токен/сессию. Это означает:
- **Вектор уязвимости:** перебор логинов → доступ к чужому портфелю
- **Рекомендация для production:** добавить JWT Bearer token или session cookie

---

## Хранение паролей

Библиотека: `org.mindrot:jbcrypt:0.4`

- `BCrypt.hashpw(password, BCrypt.gensalt())` — хэширование при регистрации
- `BCrypt.checkpw(password, hash)` — проверка при входе
- Соль генерируется автоматически и встроена в хэш
- Раунды по умолчанию: 10 (workFactor)

---

## Открытые вопросы

1. **Нет logout** — API `/api/logout` не реализован. Клиент просто забывает `username`.
2. **Нет rate limiting** — нет защиты от брутфорса пароля.
3. **Нет password reset** — смена пароля не реализована.
4. **Следующий шаг MVP** — добавить JWT и middleware-валидацию перед каждым торговым endpoint.

---

## Связанные документы

- [[06-API]]
- [[07-Database]]
- [[decisions/ADR-004-auth-simplification]]
