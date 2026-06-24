# Модуль: Linux Kernel Module

#docs #module #kernel

## Назначение

Генерирует биржевые котировки и предоставляет их пользовательским программам через символьное устройство `/dev/itmo_quotes`. Реализован как misc-устройство (`MISC_DYNAMIC_MINOR`).

## Расположение

```
server/drivers/
├── generateQuotes_kernel.c    # Исходный код модуля
├── Makefile                   # Сборка .ko файла
└── quotes.log                 # Тестовые данные (используется без модуля)
```

## Архитектура модуля

```
Kernel Space:
  ┌─────────────────────────────────────────┐
  │  quotes[7]  ← update_random_quote()    │
  │  (mutex-protected array)                │
  │                                         │
  │  generator_thread  (kthread)            │
  │  ├── update_random_quote() каждые 3 сек│
  │  └── msleep_interruptible(3000)         │
  │                                         │
  │  File operations:                       │
  │  ├── quotes_open()  → строит snapshot  │
  │  ├── quotes_read()  → копирует в user  │
  │  └── quotes_release() → free snapshot  │
  └─────────────────────────────────────────┘
         ↓ /dev/itmo_quotes
  User Space (Go-сервер, cat, и др.)
```

## Структуры данных

```c
struct quote {
    char name[16];       // Тикер: "SBER", "YANDEX", ...
    u32  price;          // Цена: случайное число 1000-500000
    time64_t updated_at; // Unix timestamp последнего обновления
};

static struct quote quotes[7];  // массив из 7 котировок
static DEFINE_MUTEX(quotes_lock); // мьютекс для потокобезопасности
```

## Тикеры

`GAZ`, `YANDEX`, `SBER`, `LUKOIL`, `ROSNEFT`, `VTBR`, `MOEX`

Цена: `1000 + get_random_u32_below(499001)` — диапазон 1000–500000.

## Поведение при read()

1. `quotes_open()` — захватывает мьютекс, строит текстовый снимок в формате CSV:
   ```
   SBER,12345,2026-06-23 12:30:00
   YANDEX,98765,2026-06-23 12:30:00
   ...
   ```
   Сохраняет снимок как `file->private_data = snapshot`. Освобождает мьютекс.

2. `quotes_read()` — копирует данные из `private_data` в userspace буфер.

3. `quotes_release()` — освобождает память снимка.

## Важные детали

- Каждый `open()` создаёт новый снимок — **атомарный снимок всех котировок**
- `kfree(file->private_data)` вызывается в `release()` — нет утечек памяти
- kthread может быть остановлен в `module_exit` через `kthread_stop()`
- Буфер снимка ограничен `SNAPSHOT_SIZE = 1024` байт

## Как использовать

```bash
# Сборка:
cd server/drivers
make all

# Загрузка:
sudo insmod generateQuotes_kernel.ko

# Чтение:
cat /dev/itmo_quotes   # один снимок 7 котировок

# Выгрузка:
sudo rmmod generateQuotes_kernel

# Отладка:
dmesg | tail -20
```

## Зависимости

- Заголовки ядра Linux (`linux-headers-$(uname -r)`)
- `KDIR` — путь к дереву сборки ядра: `/lib/modules/$(uname -r)/build`
- Права суперпользователя для `insmod`/`rmmod`

## Edge Cases

- Если `kzalloc` в `quotes_open` не удаётся — возвращает `-ENOMEM`, устройство не открывается
- `msleep_interruptible(3000)` — kthread может быть прерван при выгрузке модуля
- При первом `open()` до первого тика `kthread` — котировки имеют нулевые цены и временные метки
- Символьное устройство может быть открыто несколькими процессами одновременно (каждый получит свой снимок)

## Примеры использования

```bash
# Чтение в Go-сервере:
QUOTES_SOURCE_PATH=/dev/itmo_quotes go run server/go/main.go

# Чтение через Python:
with open('/dev/itmo_quotes', 'r') as f:
    print(f.read())
```

## Связанные документы

- [[go-server]] — читает данные из этого модуля
- [[../flows/quote-ingestion-flow]] — полный flow данных
- [[../01-Architecture]]
