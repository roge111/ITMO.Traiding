# ITMO.Trading System

Система для сбора, обработки и отображения биржевых котировок в реальном времени. Проект состоит из трёх основных компонентов: модуля ядра Linux, Go-сервера для записи в БД и Kotlin/Ktor веб-шлюза для предоставления API.

## Структура проекта

```
ITMO.Traiding/
├── gateway/                    # Kotlin/Ktor веб-шлюз
│   ├── src/main/kotlin/
│   │   ├── main.kt            # Точка входа приложения
│   │   ├── Routing.kt         # Маршрутизация HTTP-запросов
│   │   ├── Articles.kt        # (заглушка)
│   │   ├── Http.kt            # (заглушка)
│   │   ├── Resources.kt       # (заглушка)
│   │   ├── Serialization.kt   # (заглушка)
│   │   ├── StatusPages.kt     # (заглушка)
│   │   ├── Websockets.kt      # (заглушка)
│   │   ├── database/
│   │   │   └── DataBaseManager.kt  # Менеджер подключения к PostgreSQL
│   │   └── quotes/
│   │       ├── Quote.kt            # Модель котировки и HTML-утилиты
│   │       └── QuoteRepository.kt  # Репозиторий для работы с котировками из БД
│   ├── src/main/resources/
│   │   ├── application.yaml   # Конфигурация Ktor
│   │   └── logback.xml        # Конфигурация логирования
│   ├── build.gradle.kts       # Gradle конфигурация
│   └── README.md              # (пустой)
├── server/                    # Go-сервер для обработки логов
│   ├── go/
│   │   ├── main.go            # Основная логика чтения лога и записи в БД
│   │   ├── go.mod             # Go модули
│   │   └── go.sum             # Зависимости
│   └── drivers/               # Модуль ядра Linux
│       ├── generateQuotes_kernel.c  # Исходный код модуля ядра
│       ├── Makefile           # Сборка модуля
│       ├── generateQuotes_kernel.ko # Скомпилированный модуль
│       └── quotes.log         # Пример лог-файла (генерируется модулем)
└── README.md                  # Этот файл
```

## Назначение компонентов

### 1. Модуль ядра Linux (`generateQuotes_kernel`)
- **Расположение:** `server/drivers/`
- **Назначение:** Генерирует случайные котировки и записывает их в файл `/quotes.log` в формате `название,цена,время`.
- **Сборка:** `make all` (требуются заголовки ядра)
- **Установка:** `sudo insmod generateQuotes_kernel.ko`
- **Удаление:** `sudo rmmod generateQuotes_kernel`

### 2. Go-сервер (`server/go/main.go`)
- **Расположение:** `server/go/`
- **Назначение:** Бесконечно читает файл `/quotes.log`, парсит записи и сохраняет/обновляет котировки в базе данных PostgreSQL.
- **Функциональность:**
  - Подключение к БД PostgreSQL (`itmo_traiding_system`)
  - Чтение лога построчно
  - Парсинг названия, цены и времени
  - Расчет минимальной/максимальной цены и процентного изменения
  - Вставка новых котировок или обновление существующих
- **Запуск:** `go run main.go` (предварительно должен быть запущен модуль ядра и создан файл лога)

### 3. Kotlin/Ktor веб-шлюз (`gateway`)
- **Расположение:** `gateway/`
- **Назначение:** Предоставляет HTTP API для доступа к котировкам из базы данных.
- **Технологии:** Kotlin, Ktor, Exposed, PostgreSQL, HikariCP
- **Функциональность:**
  - REST endpoint `/quotes` возвращает HTML-таблицу с котировками
  - Подключение к той же БД PostgreSQL
  - Использование connection pool (HikariCP)
- **Запуск:** `./gradlew run` (порт 8080 по умолчанию)

## База данных

Используется PostgreSQL с базой `itmo_traiding_system`. Таблица `quotes` имеет следующую структуру:

```sql
CREATE TABLE quotes (
    quote_id SERIAL PRIMARY KEY,
    quote_name VARCHAR(50) NOT NULL,
    last_cost INTEGER NOT NULL,
    min_cost INTEGER NOT NULL,
    max_cost INTEGER NOT NULL,
    percentage_change DOUBLE PRECISION NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

## Требования

- Linux (для модуля ядра)
- PostgreSQL 14+
- Go 1.21+
- Java 21+ (для Kotlin/Ktor)
- Заголовки ядра Linux (для сборки модуля)

## Настройка и запуск

### 1. Настройка базы данных
```bash
sudo -u postgres psql
CREATE DATABASE itmo_traiding_system;
\c itmo_traiding_system
CREATE TABLE quotes (...); # как выше
```

### 2. Сборка и запуск модуля ядра
```bash
cd server/drivers
make all
sudo insmod generateQuotes_kernel.ko
# Проверьте, что файл /quotes.log создается
tail -f /quotes.log
```

### 3. Запуск Go-сервера
```bash
cd server/go
go run main.go
```

### 4. Запуск веб-шлюза
```bash
cd gateway
./gradlew run
```

После запуска всех компонентов откройте в браузере `http://localhost:8080/quotes` для просмотра котировок.

## Конфигурация

### Gateway
Файл `gateway/src/main/resources/application.yaml`:
```yaml
ktor:
  deployment:
    port: 8080
    host: 0.0.0.0
```

Параметры подключения к БД заданы в коде (`DataBaseManager.kt`):
- URL: `jdbc:postgresql://localhost:5432/itmo_traiding_system`
- Пользователь: `postgres`
- Пароль: `Gb%v5oVA`

### Go-сервер
Параметры подключения к БД заданы в `main.go` (аналогичные).

## Разработка

### Gateway
- Используется Gradle с Kotlin DSL
- Зависимости управляются через `gradle/libs.versions.toml`
- Для разработки: `./gradlew run --continuous`
- Тесты: `./gradlew test`

### Go-сервер
- Стандартный Go modules
- Зависимость: `github.com/lib/pq`

### Модуль ядра
- Требуются права суперпользователя для установки
- Для отладки используйте `dmesg | tail`

## Возможные улучшения

1. **Безопасность:** Вынести учётные данные БД в переменные окружения
2. **Масштабируемость:** Добавить миграции базы данных (Flyway/Liquibase)
3. **API:** Расширить REST API (JSON ответы, фильтрация, пагинация)
4. **Веб-интерфейс:** Добавить SPA фронтенд для визуализации котировок
5. **Мониторинг:** Интеграция с Prometheus/Grafana

## Авторы

Проект разработан в рамках учебного курса ИТМО.

## Лицензия

MIT