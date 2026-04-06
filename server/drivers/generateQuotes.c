/*
Драйвер для генерации котировок в файл dataQuotes.fifo с очередью фифо

*/


#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <fcntl.h>
#include <signal.h>
#include <time.h>
#include <math.h>
#include <postgresql/libpq-fe.h>


volatile sig_atomic_t  running = 1; //Флан для работы генерации котировок

void  handle_signal(int sig)
{
    if (sig == SIGINT || sig == SIGTERM) running = 0;
}

// Вектор имен бумаг
static const char *stock_names[] = {
    "GAZ",
    "YANDEX",
    "SBER",
    "LUKOIL",
    "ROSNEFT",
    "VTBR",
    "MOEX"
};
#define NUM_STOCKS (sizeof(stock_names) / sizeof(stock_names[0]))

// Генерация случайного целого числа в диапазоне [min, max]
long random_int(long min, long max) {
    return min + rand() % (max - min + 1);
}

// Функция генерации котировки
char generate_quote(PGconn *conn, FILE *fifo, FILE *logfile) {
    // Выбираем случайную бумагу
    int stock_index = rand() % NUM_STOCKS;
    const char *quote_name = stock_names[stock_index];
    
    // Генерируем новую стоимость в диапазоне 1000 - 500000 (целые числа, BIGINT)
    long new_cost = random_int(1000, 500000);
    
    // Объявляем переменные для предыдущих значений
    long last_cost = 0, min_cost = 0, max_cost = 0;
    
    // Получаем предыдущие данные из БД
    char query[512];
    snprintf(query, sizeof(query),
             "SELECT last_cost, min_cost, max_cost FROM c_quotes WHERE quote_name = '%s'",
             quote_name);
    
    PGresult *res = PQexec(conn, query);
    if (PQresultStatus(res) != PGRES_TUPLES_OK) {
        fprintf(stderr, "SELECT failed: %s\n", PQerrorMessage(conn));
        PQclear(res);
        return 0; // Таблица должна существовать, иначе ошибка конфигурации
    }
    
    int rows = PQntuples(res);
    if (rows > 0) {
        last_cost = atol(PQgetvalue(res, 0, 0));
        min_cost = atol(PQgetvalue(res, 0, 1));
        max_cost = atol(PQgetvalue(res, 0, 2));
    } else {
        // Если записи нет, вставляем новую запись (предполагаем, что quote_id автоинкремент)
        PQclear(res);
        char insert_query[512];
        snprintf(insert_query, sizeof(insert_query),
                 "INSERT INTO c_quotes (quote_name, last_cost, min_cost, max_cost, percentage_difference) "
                 "VALUES ('%s', %ld, %ld, %ld, 0.0)",
                 quote_name, new_cost, new_cost, new_cost);
        res = PQexec(conn, insert_query);
        if (PQresultStatus(res) != PGRES_COMMAND_OK) {
            fprintf(stderr, "INSERT failed: %s\n", PQerrorMessage(conn));
            PQclear(res);
            return 0;
        }
        PQclear(res);
        // Устанавливаем значения для последующего обновления (хотя UPDATE не нужен)
        last_cost = new_cost;
        min_cost = new_cost;
        max_cost = new_cost;
        
        // После INSERT можно выйти, т.к. значения уже установлены
        // Записываем в FIFO и лог-файл
        if (fifo) {
            time_t now = time(NULL);
            struct tm *tm_info = localtime(&now);
            char time_buf[20];
            strftime(time_buf, sizeof(time_buf), "%Y-%m-%d %H:%M:%S", tm_info);
            fprintf(fifo, "%s,%ld,%s\n", quote_name, new_cost, time_buf);
            fflush(fifo);
        }
        if (logfile) {
            time_t now = time(NULL);
            struct tm *tm_info = localtime(&now);
            char time_buf[20];
            strftime(time_buf, sizeof(time_buf), "%Y-%m-%d %H:%M:%S", tm_info);
            fprintf(logfile, "%s,%ld,%s\n", quote_name, new_cost, time_buf);
            fflush(logfile);
        }
        return 1;
    }
    PQclear(res);
    
    // Обновляем минимум и максимум
    long updated_min = (new_cost < min_cost) ? new_cost : min_cost;
    long updated_max = (new_cost > max_cost) ? new_cost : max_cost;
    
    // Вычисляем процентную разницу
    double percentage_difference = 0.0;
    if (last_cost != 0) {
        percentage_difference = ((double)(new_cost - last_cost) / last_cost) * 100.0;
    }
    
    // Обновляем запись в БД
    char update_query[512];
    snprintf(update_query, sizeof(update_query),
             "UPDATE c_quotes SET "
             "last_cost = %ld, "
             "min_cost = %ld, "
             "max_cost = %ld, "
             "percentage_difference = %.2f "
             "WHERE quote_name = '%s'",
             new_cost, updated_min, updated_max, percentage_difference, quote_name);
    
    res = PQexec(conn, update_query);
    if (PQresultStatus(res) != PGRES_COMMAND_OK) {
        fprintf(stderr, "UPDATE failed: %s\n", PQerrorMessage(conn));
        PQclear(res);
        return 0;
    }
    PQclear(res);
    
    // Записываем в FIFO и лог-файл (формат: имя, цена, время)
    if (fifo) {
        time_t now = time(NULL);
        struct tm *tm_info = localtime(&now);
        char time_buf[20];
        strftime(time_buf, sizeof(time_buf), "%Y-%m-%d %H:%M:%S", tm_info);
        fprintf(fifo, "%s,%ld,%s\n", quote_name, new_cost, time_buf);
        fflush(fifo);
    }
    if (logfile) {
        time_t now = time(NULL);
        struct tm *tm_info = localtime(&now);
        char time_buf[20];
        strftime(time_buf, sizeof(time_buf), "%Y-%m-%d %H:%M:%S", tm_info);
        fprintf(logfile, "%s,%ld,%s\n", quote_name, new_cost, time_buf);
        fflush(logfile);
    }
    
    return 1;
}

int main()
{
    signal(SIGINT, handle_signal);
    signal(SIGTERM, handle_signal);

    // Инициализация генератора случайных чисел
    srand(time(NULL));

    PGconn *conn;

    char *conn_string = "dbname=itmo_traiding_system user=postgres password=Gb%v5oVA host=localhost port=5432";
    FILE *fifo = fopen("/tmp/quotes.fifo", "w");
    if (!fifo) {
        fprintf(stderr, "Failed to open FIFO /tmp/quotes.fifo\n");
        exit(1);
    }
    
    // Открываем лог-файл для записи котировок (добавление в конец)
    FILE *logfile = fopen("quotes.log", "a");
    if (!logfile) {
        fprintf(stderr, "Failed to open log file quotes.log\n");
        fclose(fifo);
        exit(1);
    }
    
    /* Проверка соединения */
    conn = PQconnectdb(conn_string);
    if (PQstatus(conn) != CONNECTION_OK)
    {
        fprintf(stderr, "Connection to database failed: %s", PQerrorMessage(conn));
        PQfinish(conn);
        fclose(fifo);
        fclose(logfile);
        exit(1);
    }

    printf("Генератор котировок запущен. Нажмите Ctrl+C для остановки.\n");

    /* Основной цикл генерации котировок*/
    while (running)
    {
        if (!generate_quote(conn, fifo, logfile)) {
            fprintf(stderr, "Ошибка генерации котировки\n");
        }
        // Задержка 1 секунда между генерациями
        sleep(1);
    }

    // Очистка ресурсов
    printf("\nЗавершение работы...\n");
    PQfinish(conn);
    fclose(fifo);
    fclose(logfile);
    return 0;
}

