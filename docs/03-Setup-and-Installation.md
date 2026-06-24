# 03 — Установка и запуск

#docs #setup

## Требования к окружению

| Компонент | Версия | Где нужен |
|---|---|---|
| Docker + Docker Compose | Desktop / CLI v2+ | Локальные БД + production |
| JDK | 20 | Gateway разработка |
| Go | 1.22+ | Go-сервер разработка |
| Android Studio | Latest stable | Android-разработка |
| Android SDK Platform | 34 | Android-разработка |
| Linux + kernel headers | Любой | Только kernel module |

> [!NOTE]
> Для Windows-разработки используйте WSL2 только для сборки kernel module. Остальные компоненты работают нативно в PowerShell.

---

## Шаг 1 — Запуск баз данных

```bash
# Из корня проекта
docker compose up -d postgres clickhouse

# Проверить что контейнеры запущены
docker compose ps
```

Ожидаемый результат: `itmo-trading-postgres` и `itmo-trading-clickhouse` в статусе `healthy`.

> [!WARNING]
> Если ClickHouse ранее запускался без пароля, старый volume содержит конфиг без аутентификации. Выполните сброс:
> ```bash
> docker compose down -v
> docker compose up -d
> ```

---

## Шаг 2 — Применить миграции PostgreSQL

```bash
cd gateway
export POSTGRES_PASSWORD=postgres        # или POSTGRES_PASSWORD=ваш_пароль
./gradlew flywayMigrate --no-daemon
```

Создаются таблицы: `users`, `portfolio`, `trades`.

---

## Шаг 3 — Запустить Gateway

```bash
# В папке gateway/
export POSTGRES_PASSWORD=postgres
export CLICKHOUSE_PASSWORD=itmo
./gradlew run
```

Сервер стартует на `http://localhost:8080`. Проверьте:
- `http://localhost:8080/register` — страница регистрации
- `http://localhost:8080/quotes` — HTML-таблица котировок (пустая, пока не загружены данные)

---

## Шаг 4 — Загрузить котировки

### Вариант A: Тестовый файл (без kernel module, любая ОС)

```bash
cd server/go
export CLICKHOUSE_PASSWORD=itmo
export QUOTES_SOURCE_PATH=../drivers/quotes.log
export PROCESS_EXISTING_AND_EXIT=true
go run .
```

### Вариант B: Непрерывный режим (без kernel module)

```bash
cd server/go
export CLICKHOUSE_PASSWORD=itmo
export QUOTES_SOURCE_PATH=../drivers/quotes.log
go run .   # Ctrl+C для остановки
```

### Вариант C: Реальный Linux kernel module

```bash
cd server/drivers
make all
sudo insmod generateQuotes_kernel.ko
dmesg | tail   # проверить загрузку

# В другом терминале:
cd server/go
export CLICKHOUSE_PASSWORD=itmo
go run .   # читает /dev/itmo_quotes по умолчанию
```

---

## Шаг 5 — Android-приложение (опционально)

1. Откройте **только папку `android/`** в Android Studio (не весь репозиторий).
2. Если `android/local.properties` содержит чужой путь — удалите его, Android Studio создаст новый.
3. В SDK Manager установите: Android SDK Platform 34, Build-Tools, Platform-Tools, Emulator.
4. Создайте виртуальное устройство: Pixel 7, API 34, x86_64, Google APIs.
5. Gateway должен быть запущен (`./gradlew run`).
6. Нажмите Run (`▶`).

> [!TIP]
> Адрес `http://10.0.2.2:8080` в `BuildConfig.BASE_URL` — это специальный alias эмулятора для хост-машины. На реальном устройстве замените на IP компьютера в локальной сети.

---

## Запуск через Docker Compose (полный стек)

```bash
# Поднять всё (БД + Go-сервер + gateway)
docker compose up --build

# Или только сервисы приложения (без сборки)
docker compose up -d
```

> [!NOTE]
> При первом запуске gateway через Docker Flyway-миграции применяются автоматически через `entrypoint.sh`.

---

## Частые проблемы при запуске

| Проблема | Причина | Решение |
|---|---|---|
| `Connection refused :9000` | ClickHouse не запущен | `docker compose up -d clickhouse` |
| `Flyway: No migrations found` | Неверный classpath в fat JAR | Используй `filesystem:db/migration` или `docker compose up gateway` |
| `authentication failed: itmo` | ClickHouse volume с пустым паролем | `docker compose down -v && docker compose up -d` |
| `POSTGRES_PASSWORD not set` | Переменная не экспортирована | `export POSTGRES_PASSWORD=postgres` |
| Gateway: `tables not found` | Flyway не применился | `./gradlew flywayMigrate --no-daemon` |
| Android: `Connection refused` | Gateway не запущен или BASE_URL неверен | Запустить gateway, проверить `10.0.2.2:8080` |
| Kernel module: `insmod: ERROR` | Нет заголовков ядра | `sudo apt install linux-headers-$(uname -r)` |

---

## Связанные документы

- [[04-Configuration]]
- [[10-Deployment]]
- [[11-Troubleshooting]]
