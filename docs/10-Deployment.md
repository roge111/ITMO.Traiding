# 10 — Деплой

#docs #deployment

## Docker Compose (рекомендуемый способ)

Полный стек поднимается через `docker-compose.yml` в корне проекта.

### Команды

```bash
# Собрать и запустить все сервисы
docker compose up --build

# Запустить только БД
docker compose up -d postgres clickhouse

# Перезапустить только gateway
docker compose restart gateway

# Посмотреть логи
docker compose logs -f gateway
docker compose logs -f go-server

# Остановить все (сохранить volumes)
docker compose down

# Остановить все и удалить volumes (сброс данных)
docker compose down -v
```

---

## Сервисы и их Dockerfiles

### go-server

**Dockerfile:** `server/go/Dockerfile`

```dockerfile
FROM golang:1.22-bookworm AS builder
WORKDIR /app
COPY go.mod go.sum ./
RUN go mod download
COPY *.go ./
RUN CGO_ENABLED=0 go build -o server .

FROM debian:bookworm-slim
WORKDIR /app
COPY --from=builder /app/server .
CMD ["./server"]
```

- Multi-stage: сборка в `golang:1.22`, runtime — `debian:bookworm-slim`
- `CGO_ENABLED=0` — статически слинкованный бинарь
- Зависит от `clickhouse` (healthcheck)
- Volumes: `./server/drivers/quotes.log:/app/quotes.log:ro`

---

### gateway

**Dockerfile:** `gateway/Dockerfile`

```dockerfile
FROM eclipse-temurin:20-jdk-jammy AS builder
WORKDIR /app
COPY gradlew gradlew
COPY gradle/ gradle/
RUN chmod +x gradlew && \
    GRADLE_OPTS="-Xmx512m -Dfile.encoding=UTF-8" ./gradlew --version --no-daemon
COPY settings.gradle.kts build.gradle.kts ./
RUN GRADLE_OPTS="-Xmx1g -Dfile.encoding=UTF-8" ./gradlew dependencies --no-daemon 2>/dev/null || true
COPY src/ src/
RUN GRADLE_OPTS="-Xmx1g -Dfile.encoding=UTF-8" ./gradlew buildFatJar --no-daemon

FROM eclipse-temurin:20-jre-jammy
WORKDIR /app
COPY --from=builder /app/build/libs/*-all.jar app.jar
COPY --from=builder /app/src/main/resources/db/migration/ db/migration/
COPY entrypoint.sh .
RUN chmod +x entrypoint.sh
ENTRYPOINT ["./entrypoint.sh"]
```

**Стратегия кэширования слоёв:**
1. Gradlew + wrapper (меняется редко)
2. `build.gradle.kts` + зависимости (меняются при обновлении deps)
3. Исходный код (меняется часто)

**entrypoint.sh:**
```sh
#!/bin/sh
set -e
java -cp app.jar com.trading.database.FlywayMigrateApp  # apply migrations
exec java -jar app.jar                                    # start server
```

---

## Health Checks в Docker Compose

```yaml
postgres:
  healthcheck:
    test: ["CMD", "pg_isready", "-U", "postgres"]
    interval: 5s
    timeout: 5s
    retries: 10

clickhouse:
  healthcheck:
    test: ["CMD", "wget", "--no-verbose", "--tries=1", "--spider", "http://localhost:8123/ping"]
    interval: 5s
    timeout: 5s
    retries: 10
```

`go-server` и `gateway` зависят от `clickhouse: condition: service_healthy` и `postgres: condition: service_healthy`.

---

## Переменные для Production

> [!WARNING]
> Значения паролей в `docker-compose.yml` (`postgres`, `itmo`) предназначены только для разработки. Для production используйте Docker Secrets или внешний vault.

### Минимальный набор для production

```bash
POSTGRES_PASSWORD=<secure_password>
POSTGRES_JDBC_URL=jdbc:postgresql://<host>:5432/itmo_traiding_system
CLICKHOUSE_PASSWORD=<secure_password>
CLICKHOUSE_JDBC_URL=jdbc:clickhouse://<host>:8123/default
CLICKHOUSE_ADDR=<host>:9000
```

---

## Сборка образов вручную

```bash
# Go-сервер
cd server/go
docker build -t itmo-trading-go .

# Gateway
cd gateway
docker build -t itmo-trading-gateway .
```

---

## Возможные стратегии деплоя

| Стратегия | Статус | Описание |
|---|---|---|
| Docker Compose (single host) | ✅ Реализовано | Текущий подход |
| Kubernetes | ❌ Не реализовано | Требует написания Helm charts / manifests |
| CI/CD (GitHub Actions) | ❌ Не реализовано | Нет `.github/workflows/` |
| Cloud (AWS/GCP/Yandex Cloud) | ❌ Не реализовано | Нет terraform/cloudformation |

---

## Потенциальные улучшения

1. Добавить `.env` файл для локальной разработки вместо `export` команд
2. Добавить GitHub Actions workflow для сборки и тестирования
3. Настроить healthcheck для gateway (сейчас его нет)
4. Добавить `restart: unless-stopped` для go-server (уже есть) и gateway (уже есть)

---

## Связанные документы

- [[03-Setup-and-Installation]]
- [[04-Configuration]]
- [[11-Troubleshooting]]
