# 05 — Руководство разработчика

#docs #development

## Команды разработки

### Gateway (Kotlin/Ktor)

```bash
cd gateway
export POSTGRES_PASSWORD=postgres
export CLICKHOUSE_PASSWORD=itmo

./gradlew run                     # Запуск сервера (порт 8080)
./gradlew run --continuous        # Auto-reload при изменении кода
./gradlew test                    # Запустить тесты
./gradlew flywayMigrate --no-daemon  # Применить миграции PostgreSQL
./gradlew buildFatJar --no-daemon    # Собрать fat JAR
./gradlew dependencies            # Показать дерево зависимостей
```

### Go Server

```bash
cd server/go
export CLICKHOUSE_PASSWORD=itmo

go run .                          # Запустить (читает /dev/itmo_quotes)
go test ./...                     # Запустить тесты
go build -o server .              # Собрать бинарь
go vet ./...                      # Статический анализ

# Режим одного прогона из файла:
QUOTES_SOURCE_PATH=../drivers/quotes.log PROCESS_EXISTING_AND_EXIT=true go run .
```

### Kernel Module

```bash
cd server/drivers
make all           # Собрать .ko
sudo insmod generateQuotes_kernel.ko   # Загрузить
dmesg | tail       # Проверить логи ядра
cat /dev/itmo_quotes                   # Прочитать снимок котировок
sudo rmmod generateQuotes_kernel       # Выгрузить
make clean         # Очистить артефакты
```

### Android

```bash
cd android
./gradlew assembleDebug    # Собрать APK
./gradlew installDebug     # Установить на подключённое устройство
./gradlew test             # Unit-тесты
```

### Интеграционные тесты

```bash
export POSTGRES_PASSWORD=postgres
chmod +x scripts/integration-test.sh
./scripts/integration-test.sh
```

---

## Паттерны кода

### Добавление нового API endpoint

1. **Создать route** в `TradingRoutes.kt` (или `RegisterRoutes.kt`):

```kotlin
get("/api/myEndpoint") {
    val param = call.request.queryParameters["param"]
    if (param.isNullOrBlank()) {
        call.respond(HttpStatusCode.BadRequest, "Missing param")
        return@get
    }
    call.respond(runCatching { TradingRepository.myMethod(param) }.getOrElse {
        call.respond(HttpStatusCode.BadRequest, it.message ?: "Error")
        return@get
    })
}
```

2. **Добавить бизнес-логику** в `TradingRepository.kt`:

```kotlin
fun myMethod(param: String): MyResponse {
    // PostgreSQL:
    postgresConnection().use { conn ->
        conn.usePrepared("SELECT ... FROM table WHERE field = ?", param) { rs ->
            // обработать ResultSet
        }
    }
    // ClickHouse:
    ClickHouseManager.getConnection().use { conn ->
        conn.prepareStatement("SELECT ... FROM quotes FINAL").use { ps ->
            ps.executeQuery().use { rs -> /* ... */ }
        }
    }
}
```

3. **Создать Serializable data class** в `TradingModels.kt`:

```kotlin
@Serializable
data class MyResponse(
    val field1: String,
    val field2: Double
)
```

### Добавление новой Flyway-миграции

1. Создать файл `gateway/src/main/resources/db/migration/V6__описание.sql`.
2. Версия должна быть больше последней (`V5`).
3. SQL-изменение должно быть **идемпотентным** (`IF NOT EXISTS`, `IF EXISTS`).
4. Применить: `./gradlew flywayMigrate`.

> [!WARNING]
> Никогда не изменяй уже примененные миграционные файлы (V1, V3, V4, V5). Flyway хранит их checksum и упадёт с `FlywayValidateException`.

### Добавление нового экрана в Android

1. В `MainActivity.kt` добавить новый `@Composable` экран-функцию.
2. Добавить навигацию через переменную состояния (паттерн: `var screen by remember { mutableStateOf("quotes") }`).
3. Если нужен новый API-вызов — добавить `suspend fun` в `ApiClient.kt`.
4. Данные моделей — добавить data class в `Quote.kt`.

---

## Структура Gradle

Gateway использует два каталога версий:
- `gradle/libs.versions.toml` — Kotlin, Exposed, Logback
- `gateway/build.gradle.kts` — Ktor-зависимости через `ktorLibs.*`

Ktor-зависимости объявлены через отдельный catalog `ktorLibs` (см. `settings.gradle.kts`).

---

## Как устроен ClickHouseManager

`ClickHouseManager` — singleton (`object`), инициализируется при первом обращении:

```kotlin
object ClickHouseManager {
    private val dataSource: HikariDataSource  // пул 4 соединения
    init {
        // читает CLICKHOUSE_JDBC_URL, CLICKHOUSE_USER, CLICKHOUSE_PASSWORD
        // создаёт HikariDataSource
        ensureQuotesTable()       // CREATE TABLE IF NOT EXISTS quotes
        ensureQuotesHistoryTable() // CREATE TABLE IF NOT EXISTS quotes_history
    }
    fun getConnection(): Connection = dataSource.connection
}
```

> [!TIP]
> ClickHouseManager создаёт таблицы при инициализации — дополнительных миграций для ClickHouse не требуется.

---

## Как устроена транзакция покупки/продажи

`TradingRepository.trade()` выполняет транзакцию PostgreSQL вручную (`conn.autoCommit = false`):

1. `SELECT ... FOR UPDATE` на `users` — блокировка строки пользователя
2. `SELECT ... FOR UPDATE` на `portfolio` — блокировка позиции
3. Проверка баланса / количества акций
4. `UPDATE users SET balance = ?`
5. `INSERT INTO portfolio ON CONFLICT DO UPDATE`
6. `INSERT INTO trades`
7. `conn.commit()`
8. После коммита — `applyTradeImpact()` в ClickHouse (вне транзакции PG)

> [!WARNING]
> Обновление ClickHouse после коммита PostgreSQL не является атомарным. При сбое между шагами 7 и 8 цена в ClickHouse не обновится, но сделка в PostgreSQL будет записана.

---

## Связанные документы

- [[06-API]]
- [[07-Database]]
- [[09-Testing]]
- [[02-Project-Structure]]
