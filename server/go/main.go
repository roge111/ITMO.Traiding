package main

import (
	"bufio"
	"database/sql"
	"fmt"
	"log"
	"net/url"
	"os"
	"strconv"
	"strings"
	"time"

	_ "github.com/ClickHouse/clickhouse-go/v2"
)

func getenv(key, defaultVal string) string {
	v, ok := os.LookupEnv(key)
	if !ok || v == "" {
		return defaultVal
	}
	return v
}

func getenvPassword() string {
	return os.Getenv("CLICKHOUSE_PASSWORD")
}

func getenvBool(key string) bool {
	v := strings.TrimSpace(strings.ToLower(os.Getenv(key)))
	return v == "1" || v == "true" || v == "yes"
}

func quotesLogPath() string {
	return getenv("QUOTES_LOG_PATH", "/quotes.log")
}

func ensureSchema(db *sql.DB, database string) error {
	if database != "" && database != "default" {
		if _, err := db.Exec("CREATE DATABASE IF NOT EXISTS " + quoteIdent(database)); err != nil {
			return fmt.Errorf("create database: %w", err)
		}
	}
	fqtn := quoteIdent(database) + "." + quoteIdent("quotes")
	ddl := fmt.Sprintf(`
CREATE TABLE IF NOT EXISTS %s (
    quote_name String,
    last_cost Int32,
    min_cost Int32,
    max_cost Int32,
    percentage_change Float64,
    created_at DateTime DEFAULT now(),
    updated_at DateTime DEFAULT now(),
    version UInt64
) ENGINE = ReplacingMergeTree(version)
ORDER BY quote_name
`, fqtn)
	if _, err := db.Exec(ddl); err != nil {
		return fmt.Errorf("create table: %w", err)
	}
	return nil
}

func quoteIdent(name string) string {
	return "`" + strings.ReplaceAll(name, "`", "``") + "`"
}

func openClickHouse() (*sql.DB, string, error) {
	addr := getenv("CLICKHOUSE_ADDR", "localhost:9000")
	database := getenv("CLICKHOUSE_DATABASE", "default")
	user := getenv("CLICKHOUSE_USER", "default")
	password := getenvPassword()

	u := &url.URL{
		Scheme: "clickhouse",
		Host:   addr,
		Path:   "/" + database,
	}
	if password != "" {
		u.User = url.UserPassword(user, password)
	} else {
		u.User = url.User(user)
	}
	dsn := u.String()

	db, err := sql.Open("clickhouse", dsn)
	if err != nil {
		return nil, "", err
	}
	db.SetMaxOpenConns(4)

	if err := db.Ping(); err != nil {
		_ = db.Close()
		return nil, "", err
	}
	if err := ensureSchema(db, database); err != nil {
		_ = db.Close()
		return nil, "", err
	}
	return db, database, nil
}

// processQuoteLine applies one log line to ClickHouse (insert new version).
func processQuoteLine(db *sql.DB, tableRef, rowData string) {
	if rowData == "" {
		return
	}
	dataQuote := strings.Split(rowData, ",")
	if len(dataQuote) < 3 {
		log.Printf("Некорректная строка: %s", rowData)
		return
	}
	nameQuote := dataQuote[0]
	price, err := strconv.Atoi(dataQuote[1])
	if err != nil {
		log.Printf("Ошибка конвертации цены: %v", err)
		return
	}
	if _, err := time.Parse("2006-01-02 15:04:05", dataQuote[2]); err != nil {
		log.Printf("Некорректное время в строке: %v", err)
		return
	}

	now := time.Now().UTC()

	q := `SELECT last_cost, min_cost, max_cost, created_at, version
		FROM ` + tableRef + ` FINAL WHERE quote_name = ?`
	rows, qerr := db.Query(q, nameQuote)
	if qerr != nil {
		log.Printf("Ошибка запроса ClickHouse: %v", qerr)
		return
	}
	func() {
		defer rows.Close()
		if !rows.Next() {
			_, ierr := db.Exec(
				`INSERT INTO `+tableRef+` (quote_name, last_cost, min_cost, max_cost, percentage_change, created_at, updated_at, version) VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
				nameQuote, int32(price), int32(price), int32(price), 0.0, now, now, uint64(1),
			)
			if ierr != nil {
				log.Printf("Ошибка вставки: %v", ierr)
			} else {
				fmt.Printf("✅ [%s] Бумага добавлена в ClickHouse: цена=%d\n", time.Now().Format("15:04:05"), price)
			}
			return
		}
		var (
			lastC   int32
			minC    int32
			maxC    int32
			created time.Time
			ver     uint64
		)
		if scanErr := rows.Scan(&lastC, &minC, &maxC, &created, &ver); scanErr != nil {
			log.Printf("Ошибка чтения строки: %v", scanErr)
			return
		}

		newLast := int32(price)
		newMin := minC
		if int32(price) < newMin {
			newMin = int32(price)
		}
		newMax := maxC
		if int32(price) > newMax {
			newMax = int32(price)
		}
		var newPct float64
		if lastC != 0 {
			newPct = (float64(price) - float64(lastC)) / float64(lastC) * 100
		}

		_, ierr := db.Exec(
			`INSERT INTO `+tableRef+` (quote_name, last_cost, min_cost, max_cost, percentage_change, created_at, updated_at, version) VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
			nameQuote, newLast, newMin, newMax, newPct, created, now, ver+1,
		)
		if ierr != nil {
			log.Printf("Ошибка вставки версии: %v", ierr)
		} else {
			fmt.Printf("📈 [%s] %s обновлена: %d (%.2f%%)\n", time.Now().Format("15:04:05"), nameQuote, price, newPct)
		}
	}()
}

// processLogFromOffset reads from path starting at offset; returns new file offset (byte position).
func processLogFromOffset(db *sql.DB, tableRef, path string, fromOffset int64) (int64, error) {
	fi, statErr := os.Stat(path)
	if statErr != nil {
		return fromOffset, statErr
	}
	if fi.Size() < fromOffset {
		fromOffset = 0
	}
	file, err := os.Open(path)
	if err != nil {
		return fromOffset, err
	}
	defer file.Close()

	if _, err := file.Seek(fromOffset, 0); err != nil {
		return fromOffset, err
	}

	scanner := bufio.NewScanner(file)
	for scanner.Scan() {
		processQuoteLine(db, tableRef, scanner.Text())
	}
	if err := scanner.Err(); err != nil {
		return fromOffset, err
	}
	pos, err := file.Seek(0, 1)
	if err != nil {
		return fromOffset, err
	}
	return pos, nil
}

func main() {
	db, database, err := openClickHouse()
	if err != nil {
		log.Fatal("❌ Ошибка подключения к ClickHouse:", err)
	}
	defer db.Close()

	fmt.Println("✅ Успешное подключение к ClickHouse!")

	tableRef := quoteIdent(database) + "." + quoteIdent("quotes")
	logPath := quotesLogPath()

	if getenvBool("PROCESS_EXISTING_AND_EXIT") {
		_, err := processLogFromOffset(db, tableRef, logPath, 0)
		if err != nil {
			log.Fatalf("ошибка обработки лога %s: %v", logPath, err)
		}
		os.Exit(0)
	}

	var logOffset int64
	for {
		newOff, err := processLogFromOffset(db, tableRef, logPath, logOffset)
		if err != nil {
			if os.IsNotExist(err) {
				log.Printf("Файл %s не найден, повтор через 5 с: %v", logPath, err)
			} else {
				log.Printf("Ошибка обработки %s: %v, повтор через 5 с", logPath, err)
			}
			time.Sleep(5 * time.Second)
			continue
		}
		logOffset = newOff
		time.Sleep(1 * time.Second)
	}
}
