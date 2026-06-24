# Диаграммы

#docs #diagrams

Индекс диаграмм проекта ITMO.Trading. Все диаграммы встроены в Markdown-документы в формате Mermaid.

## Архитектурные диаграммы

| Диаграмма | Тип | Где найти |
|---|---|---|
| Компонентная схема системы | C4 Context / flowchart | [[../01-Architecture#Компонентная-схема]] |
| Data flow: kernel → ClickHouse → Android | Sequence | [[../flows/quote-ingestion-flow]] |
| Регистрация пользователя | Sequence | [[../flows/user-registration-flow#Регистрация]] |
| Авторизация (вход) | Sequence | [[../flows/user-registration-flow#Вход-(авторизация)]] |
| Покупка акций (BUY) | Sequence | [[../flows/trading-flow#Покупка-(BUY)]] |
| Продажа акций (SELL) | Sequence | [[../flows/trading-flow#Продажа-(SELL)]] |
| Просмотр портфеля | Sequence | [[../flows/trading-flow#Просмотр-портфеля]] |

## Схемы данных

| Диаграмма | Тип | Где найти |
|---|---|---|
| ER-диаграмма PostgreSQL | ER | [[../07-Database#ER-диаграмма]] |
| Схема таблиц ClickHouse | Описание | [[../07-Database#ClickHouse]] |

## Как рендерить диаграммы

### Obsidian
Диаграммы Mermaid рендерятся автоматически при открытии файла в Obsidian (включён плагин Mermaid по умолчанию).

### GitHub
GitHub рендерит Mermaid-блоки в README.md и других Markdown-файлах напрямую.

### VS Code
Установи расширение **Markdown Preview Mermaid Support** для предпросмотра.

### CLI (mermaid-js)
```bash
npx @mermaid-js/mermaid-cli -i docs/diagrams/input.md -o output.svg
```

## Добавление новых диаграмм

Вставляй диаграмму непосредственно в документ, где она описывает соответствующий контекст. Не создавай отдельных `.mmd` файлов — держи диаграммы рядом с описанием.

```markdown
```mermaid
sequenceDiagram
    ...
```
```

После добавления — обнови этот индекс.

## Связанные документы

- [[../01-Architecture]] — основные архитектурные диаграммы
- [[../flows/README]] — все flow-диаграммы
- [[../07-Database]] — ER-диаграммы
