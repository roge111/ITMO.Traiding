# Gateway (Kotlin/Ktor веб-шлюз)

Веб-шлюз для системы торговли котировками, написанный на Kotlin с использованием фреймворка Ktor. Предоставляет HTTP API для доступа к котировкам из базы данных и функциональность регистрации/авторизации пользователей.

## Структура проекта

```
gateway/
├── src/main/kotlin/
│   ├── main.kt                    # Точка входа приложения
│   ├── Routing.kt                 # Маршрутизация HTTP-запросов
│   ├── Articles.kt                # (заглушка)
│   ├── Http.kt                    # Конфигурация HTTP (CORS)
│   ├── Resources.kt               # Конфигурация ресурсов
│   ├── Serialization.kt           # Конфигурация сериализации
│   ├── StatusPages.kt             # Конфигурация страниц ошибок
│   ├── Websockets.kt              # Конфигурация WebSockets
│   ├── database/
│   │   └── DataBaseManager.kt     # Менеджер подключения к PostgreSQL
│   ├── quotes/
│   │   ├── Quote.kt               # Модель котировки и HTML-утилиты
│   │   └── QuoteReposetory.kt     # Репозиторий для работы с котировками из БД
│   └── users/
│       ├── Authorization.kt       # Логика авторизации пользователей
│       ├── Register.kt            # Логика регистрации пользователей
│       └── RegisterRoutes.kt      # Маршруты регистрации (HTML форма + API)
├── src/main/resources/
│   ├── application.yaml           # Конфигурация Ktor
│   └── logback.xml                # Конфигурация логирования
├── build.gradle.kts               # Gradle конфигурация
└── README.md                      # Этот файл
```

## Технологии

- Kotlin 1.9
- Ktor 2.3
- PostgreSQL драйвер
- Exposed (ORM)
- HikariCP (connection pool)
- BCrypt для хеширования паролей
- Gradle (Kotlin DSL)

## Функциональность

### Котировки
- **GET /quotes** – возвращает HTML-таблицу с котировками из базы данных
- Данные включают название, последнюю цену, минимальную/максимальную цену и процентное изменение

### Пользователи
- **GET /register** – HTML-форма для регистрации нового пользователя
- **POST /api/register** – API endpoint для обработки регистрации (принимает JSON)
- Хеширование паролей с использованием BCrypt
- Авторизация пользователей (реализована в `Authorization.kt`)

## Конфигурация

### База данных
Параметры подключения заданы в `DataBaseManager.kt`:
- URL: `jdbc:postgresql://localhost:5432/itmo_traiding_system`
- Пользователь: `postgres`
- Пароль: `Gb%v5oVA`

### Ktor
Конфигурация приложения находится в `src/main/resources/application.yaml`:
```yaml
ktor:
  deployment:
    port: 8080
  application:
    modules:
      - com.trading.HttpKt.configureHttp
      - com.trading.SerializationKt.configureSerialization
      - com.trading.WebsocketsKt.configureWebsockets
      - com.trading.StatusPagesKt.configureStatusPages
      - com.trading.ResourcesKt.configureResources
      - com.trading.RoutingKt.configureRouting
      - com.trading.RegisterRoutesKt.module
```

## Запуск

1. Убедитесь, что база данных PostgreSQL запущена и настроена (см. основной README)
2. Перейдите в директорию `gateway`:
   ```bash
   cd gateway
   ```
3. Запустите приложение с помощью Gradle:
   ```bash
   ./gradlew run
   ```
4. Приложение будет доступно по адресу `http://localhost:8080`

### Альтернативные команды
- **Разработка с автоматической перезагрузкой:** `./gradlew run --continuous`
- **Сборка JAR:** `./gradlew build`
- **Запуск тестов:** `./gradlew test`

## Зависимости

Зависимости управляются через файл `gradle/libs.versions.toml`. Основные зависимости:
- `ktor-server-core`, `ktor-server-netty`
- `ktor-server-html-builder`
- `exposed-core`, `exposed-jdbc`
- `postgresql` драйвер
- `hikari-cp`
- `bcrypt`
- `kotlinx-serialization-json`

## Разработка

### Структура кода
- **Маршруты:** Определены в `Routing.kt` и `RegisterRoutes.kt`
- **Логика работы с БД:** `DataBaseManager` предоставляет низкоуровневые операции, `QuoteReposetory` – репозиторий для котировок
- **Бизнес-логика:** `Register` и `Authorization` содержат логику регистрации и авторизации
- **Модели:** `Quote` – модель котировки

### Расширение функциональности
1. Добавление новых endpoint'ов: создайте новый файл в соответствующем пакете и зарегистрируйте маршруты в `Routing.kt`
2. Изменение логики работы с БД: модифицируйте `QuoteReposetory.kt` или создайте новый репозиторий
3. Добавление новых таблиц: создайте модели и миграции (в текущей версии миграции выполняются вручную)

## Тестирование

Тесты находятся в `src/test/kotlin/`. Для запуска:
```bash
./gradlew test
```

## Примечания

- Пароли хранятся в виде хешей BCrypt
- Для production использования рекомендуется вынести учётные данные БД в переменные окружения
- В текущей версии отсутствует механизм миграций БД – структура создаётся вручную
- WebSocket функциональность настроена, но не используется в текущей реализации

## Связь с другими компонентами

Gateway работает совместно с:
1. **Модулем ядра Linux** (`generateQuotes_kernel`) – генерирует котировки в файл `/quotes.log`
2. **Go-сервером** – читает лог и записывает котировки в БД
3. **PostgreSQL** – хранит данные котировок и пользователей

Gateway читает данные из БД, обновлённые Go-сервером, и предоставляет их через HTTP API.