/*
 * Драйвер ядра для генерации котировок в файл dataQuotes.fifo с очередью фифо
 * и лог-файл quotes.log
 *
 * Преобразован из пользовательской программы generateQuotes.c
 * Работает как модуль ядра Linux (.ko)
 * Вместо БД использует файл для хранения состояния (последняя цена, минимум, максимум)
 */

#include <linux/module.h>
#include <linux/kernel.h>
#include <linux/fs.h>
#include <linux/file.h>
#include <linux/sched.h>
#include <linux/kthread.h>
#include <linux/delay.h>
#include <linux/random.h>
#include <linux/string.h>
#include <linux/time.h>
#include <linux/version.h>
#include <linux/uaccess.h>
#include <linux/slab.h>

MODULE_LICENSE("GPL");
MODULE_AUTHOR("ITMO Trading");
MODULE_DESCRIPTION("Kernel driver for generating stock quotes to FIFO and log file");

// Флаг для работы генерации котировок
static volatile int running = 1;

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

// Структура для хранения состояния одной акции
struct stock_state {
    char name[16];
    long last_cost;
    long min_cost;
    long max_cost;
    long percentage_diff; // процентная разница * 100 (целое число)
};

// Глобальный массив состояний
static struct stock_state states[NUM_STOCKS];

// Файл состояния
#define STATE_FILE "/var/lib/quote_state.dat"

// Структура для хранения состояния потока
static struct task_struct *quote_thread = NULL;

// Генерация случайного целого числа в диапазоне [min, max]
static long random_int(long min, long max) {
    unsigned int rand_val;
    get_random_bytes(&rand_val, sizeof(rand_val));
    return min + (rand_val % (max - min + 1));
}

// Функция записи строки в файл из пространства ядра
static int write_to_file(const char *filename, const char *data, size_t len) {
    struct file *filp;
    loff_t pos = 0;
    int ret;

    // Открываем файл для записи (добавление в конец)
    filp = filp_open(filename, O_WRONLY | O_CREAT | O_APPEND, 0644);
    if (IS_ERR(filp)) {
        printk(KERN_ERR "generateQuotes: failed to open file %s, error %ld\n",
               filename, PTR_ERR(filp));
        return PTR_ERR(filp);
    }

    // Пишем данные
    ret = kernel_write(filp, data, len, &pos);

    // Закрываем файл
    filp_close(filp, NULL);

    if (ret < 0) {
        printk(KERN_ERR "generateQuotes: write to %s failed, error %d\n",
               filename, ret);
        return ret;
    }
    return 0;
}

// Функция для ограничения файла лога до 5 строк (FIFO очередь)
static void limit_log_lines(const char *filename) {
    struct file *filp;
    loff_t pos = 0;
    char *buffer = NULL;
    ssize_t ret;
    int line_count = 0;
    char *lines[6] = {0}; // храним указатели на последние 5 строк + 1
    char *line_start;
    int i;

    // Открываем файл для чтения
    filp = filp_open(filename, O_RDONLY, 0);
    if (IS_ERR(filp)) {
        // Файл может не существовать - это нормально
        return;
    }

    // Получаем размер файла
    loff_t file_size = vfs_llseek(filp, 0, SEEK_END);
    vfs_llseek(filp, 0, SEEK_SET);

    if (file_size <= 0) {
        filp_close(filp, NULL);
        return;
    }

    // Выделяем буфер для чтения всего файла
    buffer = kmalloc(file_size + 1, GFP_KERNEL);
    if (!buffer) {
        filp_close(filp, NULL);
        return;
    }

    // Читаем весь файл
    ret = kernel_read(filp, buffer, file_size, &pos);
    filp_close(filp, NULL);

    if (ret != file_size) {
        kfree(buffer);
        return;
    }

    buffer[file_size] = '\0';

    // Подсчитываем строки и запоминаем начало последних 5 строк
    line_start = buffer;
    for (i = 0; i < file_size; i++) {
        if (buffer[i] == '\n') {
            buffer[i] = '\0'; // временно заменяем символ новой строки на нуль-терминатор
            // Сдвигаем массив строк, сохраняя только последние 5
            if (line_count >= 5) {
                for (int j = 0; j < 4; j++) {
                    lines[j] = lines[j + 1];
                }
                lines[4] = line_start;
            } else {
                lines[line_count] = line_start;
            }
            line_count++;
            line_start = &buffer[i + 1];
        }
    }

    // Если строк больше 5, перезаписываем файл только последними 5 строками
    if (line_count > 5) {
        struct file *write_filp;
        loff_t write_pos = 0;
        
        write_filp = filp_open(filename, O_WRONLY | O_CREAT | O_TRUNC, 0644);
        if (IS_ERR(write_filp)) {
            kfree(buffer);
            return;
        }

        // Записываем последние 5 строк
        for (i = line_count - 5; i < line_count; i++) {
            char *line = lines[i - (line_count - 5)];
            size_t len = strlen(line);
            kernel_write(write_filp, line, len, &write_pos);
            kernel_write(write_filp, "\n", 1, &write_pos);
        }

        filp_close(write_filp, NULL);
        printk(KERN_DEBUG "generateQuotes: limited log file to 5 lines\n");
    }

    kfree(buffer);
}

// Чтение состояния из файла
static void read_state_file(void) {
    struct file *filp;
    loff_t pos = 0;
    int ret;
    size_t expected_size = sizeof(states);

    filp = filp_open(STATE_FILE, O_RDONLY, 0);
    if (IS_ERR(filp)) {
        printk(KERN_INFO "generateQuotes: state file not found, using defaults\n");
        // Инициализируем состояния значениями по умолчанию
        for (int i = 0; i < NUM_STOCKS; i++) {
            strncpy(states[i].name, stock_names[i], sizeof(states[i].name) - 1);
            states[i].name[sizeof(states[i].name) - 1] = '\0';
            states[i].last_cost = 0;
            states[i].min_cost = 0;
            states[i].max_cost = 0;
            states[i].percentage_diff = 0;
        }
        return;
    }

    ret = kernel_read(filp, (void *)states, expected_size, &pos);

    filp_close(filp, NULL);

    if (ret != expected_size) {
        printk(KERN_WARNING "generateQuotes: state file corrupted or incomplete, using defaults\n");
        // Инициализируем заново
        for (int i = 0; i < NUM_STOCKS; i++) {
            strncpy(states[i].name, stock_names[i], sizeof(states[i].name) - 1);
            states[i].name[sizeof(states[i].name) - 1] = '\0';
            states[i].last_cost = 0;
            states[i].min_cost = 0;
            states[i].max_cost = 0;
            states[i].percentage_diff = 0;
        }
    } else {
        printk(KERN_INFO "generateQuotes: state loaded from %s\n", STATE_FILE);
    }
}

// Сохранение состояния в файл
static void write_state_file(void) {
    struct file *filp;
    loff_t pos = 0;
    int ret;

    filp = filp_open(STATE_FILE, O_WRONLY | O_CREAT | O_TRUNC, 0644);
    if (IS_ERR(filp)) {
        printk(KERN_ERR "generateQuotes: failed to open state file for writing, error %ld\n",
               PTR_ERR(filp));
        return;
    }

    ret = kernel_write(filp, (void *)states, sizeof(states), &pos);

    filp_close(filp, NULL);

    if (ret != sizeof(states)) {
        printk(KERN_ERR "generateQuotes: failed to write state file, error %d\n", ret);
    } else {
        printk(KERN_DEBUG "generateQuotes: state saved to %s\n", STATE_FILE);
    }
}

// Функция генерации одной котировки и записи в файлы
static void generate_quote(void) {
    // Выбираем случайную бумагу
    int stock_index = get_random_u32() % NUM_STOCKS;
    struct stock_state *state = &states[stock_index];
    const char *quote_name = stock_names[stock_index];

    // Генерируем новую стоимость в диапазоне 1000 - 500000 (целые числа)
    long new_cost = random_int(1000, 500000);

    // Обновляем минимум и максимум
    long updated_min = (state->min_cost == 0 || new_cost < state->min_cost) ? new_cost : state->min_cost;
    long updated_max = (state->max_cost == 0 || new_cost > state->max_cost) ? new_cost : state->max_cost;

    // Вычисляем процентную разницу (целое число процентов)
    long percentage_diff = 0;
    if (state->last_cost != 0) {
        // (new - old) * 100 / old
        percentage_diff = ((new_cost - state->last_cost) * 100) / state->last_cost;
    }

    // Обновляем состояние в памяти
    state->last_cost = new_cost;
    state->min_cost = updated_min;
    state->max_cost = updated_max;
    state->percentage_diff = percentage_diff;

    // Получаем текущее время
    struct timespec64 ts;
    ktime_get_real_ts64(&ts);
    struct tm tm;
    time64_to_tm(ts.tv_sec, 0, &tm);

    // Формируем строку для записи (формат: имя, цена, время)
    char line[128];
    snprintf(line, sizeof(line), "%s,%ld,%04ld-%02d-%02d %02d:%02d:%02d\n",
             quote_name,
             new_cost,
             tm.tm_year + 1900,
             tm.tm_mon + 1,
             tm.tm_mday,
             tm.tm_hour,
             tm.tm_min,
             tm.tm_sec);

    // Записываем в FIFO (если файл существует)
    write_to_file("/tmp/quotes.fifo", line, strlen(line));

    // Записываем в лог-файл (абсолютный путь в корне устройства)
    write_to_file("/quotes.log", line, strlen(line));
    
    // Ограничиваем файл лога до 5 строк (FIFO очередь)
    limit_log_lines("/quotes.log");

    // Периодически сохраняем состояние (например, каждые 10 котировок)
    static int save_counter = 0;
    if (++save_counter >= 10) {
        write_state_file();
        save_counter = 0;
    }

    printk(KERN_INFO "generateQuotes: generated %s %ld (diff %ld%%)\n",
           quote_name, new_cost, percentage_diff);
}

// Функция потока генерации котировок
static int quote_thread_func(void *data) {
    while (!kthread_should_stop() && running) {
        generate_quote();
        // Задержка 3 секунды после добавления новой котировки
        msleep(3000);
    }
    return 0;
}

// Инициализация модуля
static int __init generate_quotes_init(void) {
    printk(KERN_INFO "generateQuotes: kernel module loading\n");

    // Загружаем состояние из файла
    read_state_file();

    // Создаем поток генерации котировок
    quote_thread = kthread_run(quote_thread_func, NULL, "generate_quotes");
    if (IS_ERR(quote_thread)) {
        printk(KERN_ERR "generateQuotes: failed to create kernel thread\n");
        return PTR_ERR(quote_thread);
    }

    printk(KERN_INFO "generateQuotes: kernel thread started\n");
    return 0;
}

// Выход из модуля
static void __exit generate_quotes_exit(void) {
    printk(KERN_INFO "generateQuotes: stopping module\n");
    running = 0;
    if (quote_thread) {
        kthread_stop(quote_thread);
        quote_thread = NULL;
    }
    // Сохраняем состояние перед выгрузкой
    write_state_file();
    printk(KERN_INFO "generateQuotes: module unloaded\n");
}

module_init(generate_quotes_init);
module_exit(generate_quotes_exit);