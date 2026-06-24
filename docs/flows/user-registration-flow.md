# Flow: Регистрация и вход пользователя

#docs #flow #auth

## Цель сценария

Новый пользователь регистрируется в системе и получает доступ к торговым операциям.

## Участники

| Участник | Компонент |
|---|---|
| Пользователь | Android App / Browser |
| Gateway (Ktor) | `gateway/src/main/kotlin/users/` |
| PostgreSQL | Таблица `users` |

## Регистрация

```mermaid
sequenceDiagram
    participant U as Пользователь
    participant APP as Android/Browser
    participant GW as Ktor Gateway
    participant R as Register.kt
    participant PG as PostgreSQL

    U->>APP: Ввести логин + пароль
    APP->>GW: POST /api/register<br/>(form: login, password)
    GW->>R: register(login, password)
    
    R->>R: Проверить login: 3-50 символов
    alt Невалидный login
        R->>GW: "Login must contain from 3 to 50 characters."
        GW->>APP: 400 Bad Request
    end
    
    R->>R: Проверить password >= 6 символов
    alt Слишком короткий пароль
        R->>GW: "Password must contain at least 6 characters."
        GW->>APP: 400 Bad Request
    end
    
    R->>PG: SELECT 1 FROM users WHERE username = ?
    alt Пользователь уже существует
        R->>GW: "The user is already registered."
        GW->>APP: 400 Bad Request
    end
    
    R->>R: BCrypt.hashpw(password, gensalt())
    R->>PG: INSERT INTO users (username, password_hash)
    PG->>R: OK (balance = 1000000.00 по умолчанию)
    R->>GW: "User registered successfully."
    GW->>APP: 200 OK
    APP->>APP: Перейти на экран авторизации
```

## Вход (авторизация)

```mermaid
sequenceDiagram
    participant U as Пользователь
    participant APP as Android/Browser
    participant GW as Ktor Gateway
    participant A as Authorization.kt
    participant PG as PostgreSQL

    U->>APP: Ввести логин + пароль
    APP->>GW: POST /api/login<br/>(form: login, password)
    GW->>A: authorization(login, password)
    A->>PG: SELECT password_hash FROM users WHERE username = ?
    
    alt Пользователь не найден
        PG->>A: пустой ResultSet
        A->>GW: false
        GW->>APP: 401 Unauthorized "Invalid login or password"
    end
    
    A->>A: BCrypt.checkpw(password, storedHash)
    
    alt Неверный пароль
        A->>GW: false
        GW->>APP: 401 Unauthorized
    end
    
    A->>GW: true
    GW->>APP: 200 OK "Login successful"
    APP->>APP: Сохранить username в памяти
    APP->>APP: Перейти на экран котировок
```

## Файлы участвующие в flow

| Файл | Роль |
|---|---|
| `gateway/src/main/kotlin/users/RegisterRoutes.kt` | HTTP endpoints `/register`, `/api/register`, `/authorization`, `/api/login` |
| `gateway/src/main/kotlin/users/Register.kt` | Бизнес-логика регистрации |
| `gateway/src/main/kotlin/users/Authorization.kt` | Проверка пароля через BCrypt |
| `gateway/src/main/kotlin/database/DataBaseManager.kt` | PostgreSQL JDBC соединение |
| `gateway/src/main/resources/db/migration/V1__create_users.sql` | Схема таблицы users |
| `android/app/src/main/java/com/trading/android/ApiClient.kt` | `register()`, `login()` |
| `android/app/src/main/java/com/trading/android/MainActivity.kt` | Login экран |

## Ошибки и альтернативные пути

| Сбой | HTTP-код | Сообщение |
|---|---|---|
| Login < 3 или > 50 символов | 400 | "Login must contain from 3 to 50 characters." |
| Password < 6 символов | 400 | "Password must contain at least 6 characters." |
| Username уже занят | 400 | "The user is already registered." |
| Неверный пароль при входе | 401 | "Invalid login or password" |
| PostgreSQL недоступен | 500 / Exception | Исключение логируется |

## Открытые вопросы

1. После регистрации нет автоматического входа — пользователь перенаправляется на форму входа.
2. Нет подтверждения email.
3. Нет возможности сменить пароль.

## Связанные документы

- [[../08-Authentication-and-Authorization]]
- [[../06-API]]
- [[../07-Database]]
