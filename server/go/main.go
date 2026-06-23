package main

import (
	"bufio"
	"database/sql"
	"fmt"
	"io"
	"log"
	"net/url"
	"os"
	"strconv"
	"strings"
	"time"

	_ "github.com/ClickHouse/clickhouse-go/v2"
)

const quoteTimeLayout = "2006-01-02 15:04:05"

type inputQuote struct {
	name      string
	price     int32
	timestamp time.Time
}

func getenv(key, defaultValue string) string {
	if value := strings.TrimSpace(os.Getenv(key)); value != "" {
		return value
	}
	return defaultValue
}

func getenvBool(key string) bool {
	switch strings.ToLower(strings.TrimSpace(os.Getenv(key))) {
	case "1", "true", "yes":
		return true
	default:
		return false
	}
}

func quoteSourcePath() string {
	// QUOTES_LOG_PATH оставлен для совместимости с интеграционным тестом.
	if legacyPath := strings.TrimSpace(os.Getenv("QUOTES_LOG_PATH")); legacyPath != "" {
		return legacyPath
	}
	return getenv("QUOTES_SOURCE_PATH", "/dev/itmo_quotes")
}

func quoteIdentifier(name string) string {
	return "`" + strings.ReplaceAll(name, "`", "``") + "`"
}

func openClickHouse() (*sql.DB, string, error) {
	address := getenv("CLICKHOUSE_ADDR", "localhost:9000")
	database := getenv("CLICKHOUSE_DATABASE", "default")
	user := getenv("CLICKHOUSE_USER", "default")
	password := os.Getenv("CLICKHOUSE_PASSWORD")

	connectionURL := &url.URL{
		Scheme: "clickhouse",
		Host:   address,
		Path:   "/" + database,
	}
	if password == "" {
		connectionURL.User = url.User(user)
	} else {
		connectionURL.User = url.UserPassword(user, password)
	}

	db, err := sql.Open("clickhouse", connectionURL.String())
	if err != nil {
		return nil, "", err
	}
	db.SetMaxOpenConns(4)

	if err := db.Ping(); err != nil {
		db.Close()
		return nil, "", fmt.Errorf("ping ClickHouse: %w", err)
	}
	if err := ensureSchema(db, database); err != nil {
		db.Close()
		return nil, "", err
	}
	return db, database, nil
}

func ensureSchema(db *sql.DB, database string) error {
	if database != "default" {
		if _, err := db.Exec("CREATE DATABASE IF NOT EXISTS " + quoteIdentifier(database)); err != nil {
			return fmt.Errorf("create database: %w", err)
		}
	}

	table := quoteIdentifier(database) + "." + quoteIdentifier("quotes")
	_, err := db.Exec(fmt.Sprintf(`
		CREATE TABLE IF NOT EXISTS %s (
			quote_name String,
			last_cost Int32,
			min_cost Int32,
			max_cost Int32,
			percentage_change Float64,
			created_at DateTime DEFAULT now(),
			updated_at DateTime DEFAULT now(),
			version UInt64
		)
		ENGINE = ReplacingMergeTree(version)
		ORDER BY quote_name
	`, table))
	if err != nil {
		return fmt.Errorf("create quotes table: %w", err)
	}

	historyTable := quoteIdentifier(database) + "." + quoteIdentifier("quotes_history")
	_, err = db.Exec(fmt.Sprintf(`
		CREATE TABLE IF NOT EXISTS %s (
			quote_name String,
			price Int32,
			happened_at DateTime,
			version UInt64
		)
		ENGINE = MergeTree()
		ORDER BY (quote_name, happened_at, version)
	`, historyTable))
	if err != nil {
		return fmt.Errorf("create quotes_history table: %w", err)
	}
	return nil
}

func parseQuoteLine(line string) (inputQuote, error) {
	parts := strings.Split(strings.TrimSpace(line), ",")
	if len(parts) != 3 {
		return inputQuote{}, fmt.Errorf("expected 3 fields")
	}

	name := strings.TrimSpace(parts[0])
	if name == "" {
		return inputQuote{}, fmt.Errorf("empty quote name")
	}

	price, err := strconv.ParseInt(strings.TrimSpace(parts[1]), 10, 32)
	if err != nil || price <= 0 {
		return inputQuote{}, fmt.Errorf("invalid price %q", parts[1])
	}

	timestamp, err := time.ParseInLocation(
		quoteTimeLayout,
		strings.TrimSpace(parts[2]),
		time.Local,
	)
	if err != nil {
		return inputQuote{}, fmt.Errorf("invalid timestamp: %w", err)
	}

	return inputQuote{name: name, price: int32(price), timestamp: timestamp}, nil
}

// readSnapshot выполняет требуемую цепочку open -> read -> close.
// Для символьного устройства один вызов возвращает текущий массив котировок.
func readSnapshot(path string) ([]inputQuote, error) {
	source, err := os.Open(path)
	if err != nil {
		return nil, err
	}
	defer source.Close()

	data, err := io.ReadAll(source)
	if err != nil {
		return nil, err
	}

	var quotes []inputQuote
	scanner := bufio.NewScanner(strings.NewReader(string(data)))
	for scanner.Scan() {
		quote, parseErr := parseQuoteLine(scanner.Text())
		if parseErr != nil {
			log.Printf("пропущена некорректная строка %q: %v", scanner.Text(), parseErr)
			continue
		}
		quotes = append(quotes, quote)
	}
	return quotes, scanner.Err()
}

func saveQuote(db *sql.DB, table string, quote inputQuote) error {
	const selectColumns = "last_cost, min_cost, max_cost, created_at, version"
	row := db.QueryRow(
		"SELECT "+selectColumns+" FROM "+table+" FINAL WHERE quote_name = ?",
		quote.name,
	)

	var (
		lastCost int32
		minCost  int32
		maxCost  int32
		created  time.Time
		version  uint64
	)
	err := row.Scan(&lastCost, &minCost, &maxCost, &created, &version)
	if err == sql.ErrNoRows {
		_, insertErr := db.Exec(
			"INSERT INTO "+table+" (quote_name, last_cost, min_cost, max_cost, percentage_change, created_at, updated_at, version) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
			quote.name, quote.price, quote.price, quote.price, 0.0,
			quote.timestamp, quote.timestamp, uint64(1),
		)
		if insertErr != nil {
			return insertErr
		}
		return saveQuoteHistory(db, quote, 1)
	}
	if err != nil {
		return err
	}

	newMin := minCost
	if quote.price < newMin {
		newMin = quote.price
	}
	newMax := maxCost
	if quote.price > newMax {
		newMax = quote.price
	}
	percentageChange := 0.0
	if lastCost != 0 {
		percentageChange = (float64(quote.price) - float64(lastCost)) / float64(lastCost) * 100
	}

	_, err = db.Exec(
		"INSERT INTO "+table+" (quote_name, last_cost, min_cost, max_cost, percentage_change, created_at, updated_at, version) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
		quote.name, quote.price, newMin, newMax, percentageChange,
		created, quote.timestamp, version+1,
	)
	if err != nil {
		return err
	}
	return saveQuoteHistory(db, quote, version+1)
}

func saveQuoteHistory(db *sql.DB, quote inputQuote, version uint64) error {
	_, err := db.Exec(
		"INSERT INTO quotes_history (quote_name, price, happened_at, version) VALUES (?, ?, ?, ?)",
		quote.name, quote.price, quote.timestamp, version,
	)
	return err
}

func processSnapshot(
	db *sql.DB,
	table string,
	quotes []inputQuote,
	seen map[string]string,
) {
	for _, quote := range quotes {
		signature := fmt.Sprintf("%d@%d", quote.price, quote.timestamp.Unix())
		if seen[quote.name] == signature {
			continue
		}
		if err := saveQuote(db, table, quote); err != nil {
			log.Printf("не удалось сохранить %s: %v", quote.name, err)
			continue
		}
		seen[quote.name] = signature
		log.Printf("%s: price=%d", quote.name, quote.price)
	}
}

func main() {
	db, database, err := openClickHouse()
	if err != nil {
		log.Fatal("ошибка подключения к ClickHouse: ", err)
	}
	defer db.Close()

	sourcePath := quoteSourcePath()
	table := quoteIdentifier(database) + "." + quoteIdentifier("quotes")
	seen := make(map[string]string)

	for {
		quotes, readErr := readSnapshot(sourcePath)
		if readErr != nil {
			log.Printf("не удалось прочитать %s: %v", sourcePath, readErr)
		} else {
			processSnapshot(db, table, quotes, seen)
		}

		if getenvBool("PROCESS_EXISTING_AND_EXIT") {
			if readErr != nil {
				os.Exit(1)
			}
			return
		}
		time.Sleep(time.Second)
	}
}
