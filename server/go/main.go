package main

import (
	"bufio"
	"database/sql"
	"fmt"
	"log"
	"os"
	"strconv"
	"strings"
	"time"

	"github.com/lib/pq"
)

func main() {
	cfg := pq.Config{
		Host:     "localhost",
		Port:     5432,
		User:     "postgres",
		Password: "Gb%v5oVA",
		Database: "itmo_traiding_system",
	}

	c, err := pq.NewConnectorConfig(cfg)
	if err != nil {
		log.Fatal(err)
	}

	// Открываем соединение
	db := sql.OpenDB(c)
	defer db.Close()

	// Проверяем соединение
	err = db.Ping()
	if err != nil {
		log.Fatal("❌ Ошибка подключения к БД:", err)
	}

	fmt.Println("✅ Успешное подключение к PostgreSQL!")

	// Бесконечный цикл чтения файла
	for {
		func() {
			file, err := os.Open("/quotes.log")
			if err != nil {
				log.Printf("Ошибка открытия файла: %v. Повтор через 5 секунд.", err)
				time.Sleep(5 * time.Second)
				return
			}
			defer file.Close()

			scanner := bufio.NewScanner(file)
			for scanner.Scan() {
				row_data := scanner.Text()
				if row_data == "" {
					continue
				}

				data_quote := strings.Split(row_data, ",")
				if len(data_quote) < 3 {
					log.Printf("Некорректная строка: %s", row_data)
					continue
				}

				name_quote := data_quote[0]
				price, err := strconv.Atoi(data_quote[1])
				if err != nil {
					log.Printf("Ошибка конвертации цены: %v", err)
					continue
				}

				timestamp, err := time.Parse("2006-01-02 15:04:05", data_quote[2])
				if err != nil {
					log.Printf("Ошибка парсинга времени: %v", err)
					continue
				}

				var lastID int
				_ = db.QueryRow("SELECT COALESCE(MAX(quote_id), 0) FROM quotes").Scan(&lastID)
				newID := lastID + 1

				var flag int
				err = db.QueryRow("SELECT 1 FROM quotes WHERE quote_name=$1", name_quote).Scan(&flag)
				if err == sql.ErrNoRows {
					flag = 0 // записи нет
				} else if err != nil {
					log.Printf("Ошибка проверки существования: %v", err)
					continue
				}

				if flag == 0 {
					// Повторная проверка (на случай race condition)
					var flag2 int
					err := db.QueryRow("SELECT 1 FROM quotes WHERE quote_name=$1", name_quote).Scan(&flag2)
					if err == sql.ErrNoRows {
						flag2 = 0
					} else if err != nil {
						log.Printf("Ошибка повторной проверки: %v", err)
						continue
					}

					if flag2 == 0 {
						_, err := db.Exec("INSERT INTO quotes(quote_id, quote_name, last_cost, min_cost, max_cost, updated_at) VALUES ($1, $2, $3, $4, $5, $6)", newID, name_quote, price, price, price, timestamp)
						if err != nil {
							log.Printf("Ошибка вставки: %v", err)
						} else {
							fmt.Printf("✅ [%s] Бумага добавлена в базу данных: цена=%d\n", time.Now().Format("15:04:05"), price)
						}
					}
				} else {
					var dbTime time.Time
					var quote_name_db string
					var last_cost_db int
					var min_cost_db int
					var max_cost_db int

					err = db.QueryRow("SELECT quote_name, last_cost, min_cost, max_cost, updated_at FROM quotes WHERE quote_name=$1", name_quote).Scan(&quote_name_db, &last_cost_db, &min_cost_db, &max_cost_db, &dbTime)
					if err != nil {
						log.Printf("Ошибка получения данных: %v", err)
						continue
					}

					if timestamp.After(dbTime) {
						if price < min_cost_db {
							min_cost_db = price
						}
						if price > max_cost_db {
							max_cost_db = price
						}

						var del_price_procent float64
						if last_cost_db != 0 {
							del_price_procent = (float64(price-last_cost_db) / float64(last_cost_db)) * 100
						} else {
							del_price_procent = 0
						}

						_, err := db.Exec("UPDATE quotes SET last_cost=$1, min_cost=$2, max_cost=$3, updated_at=$4, percentage_change=$5 WHERE quote_name=$6", price, min_cost_db, max_cost_db, timestamp, del_price_procent, name_quote)
						if err != nil {
							log.Printf("Ошибка обновления: %v", err)
						} else {
							fmt.Printf("📈 [%s] %s обновлена: %d (%.2f%%)\n", time.Now().Format("15:04:05"), name_quote, price, del_price_procent)
						}
					} else {
						fmt.Printf("⏸ [%s] %s пропущена (старые данные)\n", time.Now().Format("15:04:05"), name_quote)
					}
				}
			}

			if err := scanner.Err(); err != nil {
				log.Printf("Ошибка чтения файла: %v", err)
			}
		}()

		// Пауза перед повторным чтением файла (1 секунда)
		time.Sleep(1 * time.Second)
	}
}