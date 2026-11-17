# Слепая перепроверка: сравнение с итоговыми вердиктами

Вердикт слепой перепроверки (blind) против итогового вердикта (final) по каждой паре. Подробные таблицы ниже сформированы `reference/compare.py`, колонка "Оценка" в них - шкала 1-10 с потолком, поэтому баллы сведены отдельно.

| Пара | Модель | Баллы blind | Баллы final | Разница |
|---|---|---|---|---|
| 01-C | Claude Opus 5.5 Max | 13 из 13 | 13 из 13 | 0 |
| 01-J | Claude Fable 5.2 Max | 11 из 13 | 12 из 13 | −1 |
| 02-B | DeepSeek V4.1-Flash | 9 из 16 | 7 из 16 | +2 |
| 02-I | Gemini 4 Carbon | 13,5 из 16 | 13 из 16 | +0,5 |
| 02-K | Алиса AI | 14 из 16 | 12 из 16 | +2 |
| 03-C | Gemini 3.1 Pro | 4 из 16 | 4 из 16 | 0 |
| 04-D | ChatGPT GPT-5.6 Sol | 10 из 14 | 10 из 14 | 0 |
| 04-G | Kimi K3.1 Pro | 3 из 14 | 2 из 14 | +1 |
| 04-M | Qwen 4 Max | 7,5 из 14 | 7,5 из 14 | 0 |
| 05-B | Claude Opus 5.5 Max | 16 из 17 | 16 из 17 | 0 |
| 05-I | Gemini 4 Carbon | 13,5 из 17 | 13,5 из 17 | 0 |
| 05-L | GigaChat 3.5 Ultra | −7 из 17 | −8,5 из 17 | +1,5 |

Пункты чек-листа совпали в 357 из 361 случая (99%). Там, где разница больше балла (02-B, 02-K), слепая проверка не записала по одной-две выдуманные проблемы, которые есть в итоговом вердикте. Они проверены по коду и подтверждаются.

## 01-recon

| Ответ | Оценка blind | Оценка final | Разница | Совпадение пунктов | Ошибки blind | Ошибки final |
|---|---|---|---|---|---|---|
| C | 10.0 | 10.0 | 0.0 | 24/24 (100%) | 0 | 0 |
| J | 10.0 | 10.0 | 0.0 | 23/24 (95%) | 0 | 0 |

**Общее совпадение по пунктам:** 47/48 (97%)

**Порядок blind:** C > J
**Порядок final:** C > J

### Расхождения по пунктам
- J · K1-S5: blind = нет, final = есть

### Ошибки по категориям

**C**

**J**

## 02-business-logic

| Ответ | Оценка blind | Оценка final | Разница | Совпадение пунктов | Ошибки blind | Ошибки final |
|---|---|---|---|---|---|---|
| B | 9.0 | 7.0 | 2.0 | 35/35 (100%) | 2 | 4 |
| I | 10.0 | 10.0 | 0.0 | 34/35 (97%) | 1 | 1 |
| K | 10.0 | 10.0 | 0.0 | 34/35 (97%) | 2 | 3 |

**Общее совпадение по пунктам:** 103/105 (98%)

**Порядок blind:** K > I > B
**Порядок final:** I > K > B

### Расхождения по пунктам
- I · K2-M3c: blind = есть, final = нет
- K · K2-S1: blind = есть, final = нет

### Ошибки по категориям

**B**
- [blind] ложная_аномалия: «`ApiExceptionHandler` перехватывает `ResponseStatusException` и возвращает `500` вместо `503`»
- [blind] нарушение_формата: «| да | RESERVED | COMPLETED | markCommitted | … Перед этим выполняется `lazyReset(today)`. … | А-9 | `setFailIfSendResul»
- [final] ложная_аномалия: «`ApiExceptionHandler` перехватывает `ResponseStatusException` и возвращает `500` вместо `503`»
- [final] нарушение_формата: «`setFailIfSendResultIsError(false)` … | да | RESERVED | CANCELLED | release usage, markReleased | … получить историю пер»
- [final] ложная_аномалия: «Склейка messageInfo при обоих DECLINED может дать null ... В истории и API появляется null; ... или null; null»
- [final] ложная_аномалия: «settle при CANCELLED/DENIED требует наличия SenderUsage ... Запись usage отсутствует | NoSuchElementException»

**I**
- [blind] неточность: «Сервис `limit-service` сразу резервирует средства (`RESERVED`) и публикует `APPROVED`»
- [final] неточность: «Сервис `limit-service` сразу резервирует средства (`RESERVED`) и публикует `APPROVED`»

**K**
- [blind] неточность: «оба проходят проверку, одно обновление падает по оптимистичной блокировке, после 3 повторов событие уходит в DLT»
- [blind] неточность: «после сохранения решение может не опубликоваться (сбой брокера, падение сервиса). Восстановление возможно только если кл»
- [final] неточность: «после сохранения решение может не опубликоваться (сбой брокера, падение сервиса). Восстановление возможно только если кл»
- [final] неточность: «два одновременных перевода одного отправителя оба проходят проверку, одно обновление падает по оптимистичной блокировке,»
- [final] ложная_аномалия: «В резерве approve()/decline() не обновляют время изменения (обновляется только при закрытии резерва). … Искажение времен»

## 03-specs

| Ответ | Оценка blind | Оценка final | Разница | Совпадение пунктов | Ошибки blind | Ошибки final |
|---|---|---|---|---|---|---|
| C | 4.0 | 4.0 | 0.0 | 25/25 (100%) | 5 | 5 |

**Общее совпадение по пунктам:** 25/25 (100%)

**Порядок blind:** C
**Порядок final:** C

### Расхождения по пунктам

### Ошибки по категориям

**C**
- [blind] нарушение_формата: «| id | body | Long | Да | ID перевода | … | amount | body | BigDecimal | Да | Сумма (больше нуля, макс. 13 цифр, 2 знака»
- [blind] неточность: «| createdAt | String(ISO) / LocalDateTime | Нет | Дата-время создания | "2024-01-01T12:00:00" |»
- [blind] мелкая_ошибка: «Канал: transfers.DLT, limit-results.DLT, screening-results.DLT … | Отправители | Все сервисы (через DeadLetterPublishing»
- [blind] ложная_аномалия: «Для поля `amount` не указан параметр формата `minimum: 0`, ограничивающий точное поведение, используется `exclusiveMinim»
- [blind] ложная_аномалия: «Тип `string` со строгим `enum` (NEW, COMPLETED, CANCELLED, DENIED) | Поле `TransferEntity.status` имеет тип `String` и н»
- [final] нарушение_формата: «| id | body | Long | Да | ID перевода | … | amount | body | BigDecimal | Да | … | createdAt | String (DateTime) | Нет |»
- [final] неточность: «| createdAt | String(ISO) / LocalDateTime | Нет | Дата-время создания | "2024-01-01T12:00:00" |»
- [final] мелкая_ошибка: «Канал: transfers.DLT, limit-results.DLT, screening-results.DLT … | Отправители | Все сервисы (через DeadLetterPublishing»
- [final] ложная_аномалия: «Для поля `amount` не указан параметр формата `minimum: 0` … что в OpenAPI не отражено в `multipleOf` и `maximum`»
- [final] ложная_аномалия: «Тип `string` со строгим `enum` (NEW, COMPLETED, CANCELLED, DENIED) | Поле `TransferEntity.status` имеет тип `String` … П»

## 04-diagrams

| Ответ | Оценка blind | Оценка final | Разница | Совпадение пунктов | Ошибки blind | Ошибки final |
|---|---|---|---|---|---|---|
| D | 10.0 | 10.0 | 0.0 | 26/26 (100%) | 0 | 0 |
| G | 3.0 | 2.0 | 1.0 | 26/26 (100%) | 5 | 6 |
| M | 7.5 | 7.5 | 0.0 | 26/26 (100%) | 4 | 4 |

**Общее совпадение по пунктам:** 78/78 (100%)

**Порядок blind:** D > M > G
**Порядок final:** D > M > G

### Расхождения по пунктам

### Ошибки по категориям

**D**

**G**
- [blind] искажение: «L->>LDB: INSERT reservations (DECLINED/NONE) … usage.release(amount, ...)<br/>reservation.markReleased()<br/>(компенсаци»
- [blind] мелкая_ошибка: «T -->|"Kafka Streams source"| TDL»
- [blind] мелкая_ошибка: «TRANSFERS ||--|| TRANSFER_HISTORY : "fk: transfer_history.transfer_id → transfers.id"»
- [blind] неточность: «SENDER_USAGE ||--o{ RESERVATIONS … BLOCKED_SENDERS ||--o{ SCREENING_CHECKS»
- [blind] нарушение_формата: «SENDER_USAGE ||--o{ RESERVATIONS : "логически: reservations.sender_id (без FK)"»
- [final] мелкая_ошибка: «T -->|"Kafka Streams source"| TDL»
- [final] искажение: «и уже выполненная лимитная резервация компенсируется (освобождение `SenderUsage`, `RESERVED → RELEASED`). Показан полный»
- [final] мелкая_ошибка: «TRANSFERS ||--|| TRANSFER_HISTORY : "fk: transfer_history.transfer_id → transfers.id"»
- [final] неточность: «SENDER_USAGE ||--o{ RESERVATIONS»
- [final] мелкая_ошибка: «BLOCKED_SENDERS ||--o{ SCREENING_CHECKS»
- [final] нарушение_формата: «SENDER_USAGE ||--o{ RESERVATIONS : "логически: reservations.sender_id (без FK)"»

**M**
- [blind] мелкая_ошибка: «`state`: NONE → RESERVED (approve) → COMMITTED (markCommitted при COMPLETED) или RELEASED (markReleased при CANCELLED/DE»
- [blind] неточность: «sender_usage ||..o{ reservations : "логически по sender_id, FK нет"»
- [blind] мелкая_ошибка: «TransferRequest ||..|| Transfer : "Transfer.newTransfer в TransferController.publishNew"»
- [blind] ложная_аномалия: «String detail "русские тексты в файле в повреждённой кодировке"»
- [final] мелкая_ошибка: «`state`: NONE → RESERVED (approve) → COMMITTED (markCommitted при COMPLETED) или RELEASED (markReleased при CANCELLED/DE»
- [final] неточность: «sender_usage ||..o{ reservations : "логически по sender_id, FK нет"»
- [final] мелкая_ошибка: «TransferRequest ||..|| Transfer : "Transfer.newTransfer в TransferController.publishNew"»
- [final] ложная_аномалия: «String detail "русские тексты в файле в повреждённой кодировке"»

## 05-change-request

| Ответ | Оценка blind | Оценка final | Разница | Совпадение пунктов | Ошибки blind | Ошибки final |
|---|---|---|---|---|---|---|
| B | 10.0 | 10.0 | 0.0 | 35/35 (100%) | 0 | 0 |
| I | 10.0 | 10.0 | 0.0 | 35/35 (100%) | 1 | 1 |
| L | 1.0 | 1.0 | 0.0 | 34/35 (97%) | 6 | 6 |

**Общее совпадение по пунктам:** 104/105 (99%)

**Порядок blind:** B > I > L
**Порядок final:** B > I > L

### Расхождения по пунктам
- L · K5-M2c: blind = есть, final = нет

### Ошибки по категориям

**B**

**I**
- [blind] неточность: «`CHECK (single_transfer_max > 0 AND daily_max > 0 AND monthly_max > 0)` … должны ли все лимиты быть строго больше нуля (»
- [final] неточность: «`CHECK (single_transfer_max > 0 AND daily_max > 0 AND monthly_max > 0)` — все лимиты строго положительные … бизнесу нужн»

**L**
- [blind] галлюцинация: «Проект состоит из единственного файла `TransferService.java`, содержащего заглушку метода `processTransfer`.»
- [blind] галлюцинация: «**Таблица `senders`**: изменений нет (кроме FK из `sender_limits`).»
- [blind] галлюцинация: «**Сервис нотификаций**: изменений нет — отклонение обрабатывается на уровне API Gateway / контроллера.»
- [blind] искажение: «Перевод 15 000 при дневном лимите 10 000 (остаток 0) | HTTP 422, причина `DAILY_LIMIT_EXCEEDED`»
- [blind] мелкая_ошибка: «Два перевода по 15 000 при `maxSingleTransfer = 20 000` и дневном лимите 30 000 | Первый — 200, второй — 422 (дневной ли»
- [blind] нарушение_формата: «Метод `processTransfer` должен принимать `maxSingleTransfer` из кеша/таблицы, а не использовать захардкоженное значение.»
- [final] галлюцинация: «Проект состоит из единственного файла `TransferService.java`, содержащего заглушку метода `processTransfer`. … другие се»
- [final] галлюцинация: «`sender_id` BIGINT NOT NULL FK → `senders(id)` … **Таблица `senders`**: изменений нет (кроме FK из `sender_limits`).»
- [final] галлюцинация: «**Сервис нотификаций**: изменений нет — отклонение обрабатывается на уровне API Gateway / контроллера.»
- [final] искажение: «| Перевод 15 000 при дневном лимите 10 000 (остаток 0) | HTTP 422, причина `DAILY_LIMIT_EXCEEDED` |»
- [final] искажение: «| Два перевода по 15 000 при `maxSingleTransfer = 20 000` и дневном лимите 30 000 | Первый — 200, второй — 422 (дневной »
- [final] нарушение_формата: «Метод `processTransfer` должен принимать `maxSingleTransfer` из кеша/таблицы, а не использовать захардкоженное значение.»
