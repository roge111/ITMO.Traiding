# 11 — Устранение неполадок

#docs #troubleshooting

## Проблемы запуска

### Gateway не стартует: `Connection refused clickhouse:8123`

**Симптом:** gateway падает при инициализации `ClickHouseManager`.

**Причина:** ClickHouse не запущен или ещё не прошёл health check.

**Решение:**
```bash
docker compose up -d clickhouse
docker compose ps   # ждать статуса healthy
docker compose restart gateway
```

---

### Gateway не стартует: `POSTGRES auth failed`

**Симптом:** `PSQLException: FATAL: password authentication failed for user "postgres"`.

**Причина:** `POSTGRES_PASSWORD` не задана или задана неверно.

**Решение:**
```bash
export POSTGRES_PASSWORD=postgres
./gradlew run
```

В Docker: проверить `docker-compose.yml`, секция `gateway.environment`.

---

### Flyway: `Detected applied migration not resolved: 1`

**Симптом:** `FlywayValidateException: Detected applied migration not resolved locally: 1`.

**Причина:** Flyway создал baseline с версией 1, которая конфликтует с V1__create_users.sql.

**Решение:**
```bash
# В PostgreSQL удалить flyway_schema_history:
psql -U postgres -d itmo_traiding_system -c "DROP TABLE IF EXISTS flyway_schema_history;"
# Затем:
./gradlew flywayMigrate --no-daemon
```

Или, если можно сбросить данные:
```bash
docker compose down -v
docker compose up -d
./gradlew flywayMigrate --no-daemon
```

---

### Flyway: `No migrations found in classpath:db/migration`

**Симптом:** Миграции не применяются (логи говорят "no migrations found").

**Причина:** В fat JAR classpath scanner не находит SQL-файлы по `classpath:`.

**Решение (Docker):** `entrypoint.sh` использует `FlywayMigrateApp`, который копирует файлы из JAR и применяет их через `filesystem:`. Убедитесь что в `Dockerfile` есть строка:
```dockerfile
COPY --from=builder /app/src/main/resources/db/migration/ db/migration/
```

**Решение (локально):** Используйте `./gradlew flywayMigrate` — Gradle task имеет доступ к classpath напрямую.

---

### ClickHouse: `authentication failed` при старте с password

**Симптом:** Go-сервер или gateway не могут подключиться к ClickHouse с паролем.

**Причина:** Старый Docker volume был создан без аутентификации. ClickHouse кэширует конфиг в volume.

**Решение:**
```bash
docker compose down -v   # ВНИМАНИЕ: удалит все данные
docker compose up -d
```

---

### Go-сервер: `open /dev/itmo_quotes: no such file`

**Симптом:** Go-сервер завершается с ошибкой чтения устройства.

**Причина:** Kernel module не загружен или система не Linux.

**Решение (использовать тестовый файл):**
```bash
export QUOTES_SOURCE_PATH=../drivers/quotes.log
export PROCESS_EXISTING_AND_EXIT=true
go run .
```

---

## Проблемы конфигурации

### Android: `Unable to connect to http://10.0.2.2:8080`

**Причина 1:** Gateway не запущен.
**Решение:** Запустить `./gradlew run` в папке `gateway/`.

**Причина 2:** Реальное устройство вместо эмулятора. `10.0.2.2` работает только в Android Emulator.
**Решение:** В `android/app/build.gradle.kts` заменить `BASE_URL` на IP компьютера:
```kotlin
buildConfigField("String", "BASE_URL", "\"http://192.168.1.100:8080\"")
```

**Причина 3:** Android Emulator не запущен.
**Решение:** Запустить Device Manager → Play.

---

### Gateway: порт 8080 занят

**Симптом:** `Address already in use: 8080`.

**Решение:**
```bash
# Найти процесс на порту:
lsof -i :8080
# Или через fuser:
fuser -k 8080/tcp
```

---

## Проблемы сборки

### Gradle: `OutOfMemoryError` при сборке в Docker

**Симптом:** Gradle процесс падает с OOM или Docker build зависает.

**Причина:** Gradle по умолчанию запускает daemon с `-Xmx12g`.

**Решение:** В Dockerfile используется `GRADLE_OPTS="-Xmx1g -Dfile.encoding=UTF-8"`. Убедитесь что строка присутствует в `RUN` командах Dockerfile.

---

### Gradle: timeout при скачивании дистрибутива

**Симптом:** `Could not GET 'https://services.gradle.org/distributions/...'`. Таймаут через 10 сек.

**Причина:** Дефолтный `networkTimeout=10000` (10 секунд) слишком мал для Gradle ~150MB.

**Решение:** В `gateway/gradle/wrapper/gradle-wrapper.properties`:
```properties
networkTimeout=120000
```

---

### Kernel module: `make: *** [Makefile:7: all] Error 2`

**Симптом:** Ошибка при `make all` в `server/drivers/`.

**Причина:** Нет заголовков ядра Linux.

**Решение:**
```bash
sudo apt update
sudo apt install linux-headers-$(uname -r)
make all
```

---

## Диагностика

### Проверить что ClickHouse работает

```bash
# Через HTTP API:
curl http://localhost:8123/ping

# Посмотреть котировки:
curl 'http://localhost:8123/?query=SELECT+*+FROM+quotes+FINAL&user=default&password=itmo'
```

### Проверить что PostgreSQL работает

```bash
docker exec -it itmo-trading-postgres psql -U postgres -d itmo_traiding_system -c '\dt'
```

### Проверить логи контейнеров

```bash
docker compose logs postgres
docker compose logs clickhouse
docker compose logs gateway
docker compose logs go-server
```

### Проверить kernel module

```bash
lsmod | grep generateQuotes    # модуль загружен?
dmesg | tail -20               # логи ядра
cat /dev/itmo_quotes           # снимок котировок
```

---

## Связанные документы

- [[03-Setup-and-Installation]]
- [[04-Configuration]]
- [[10-Deployment]]
