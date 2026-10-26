## 1. HTTP API

### Эндпоинт: POST /transfers

**Описание:** Создаёт перевод и возвращает его сохранённое представление (`transfer-service/src/main/java/com/example/transfer/controller/TransferController.java`, `TransferController.create`).

#### Параметры запроса

| Параметр | Источник (body/path/query) | Тип | Обязательный | Описание |
|---|---|---|---|---|
| id | body | integer (int64) | да (`@NotNull`) | Идентификатор перевода. |
| senderId | body | integer (int64) | да (`@NotNull`) | Идентификатор отправителя. |
| recipientId | body | integer (int64) | да (`@NotNull`) | Идентификатор получателя. |
| amount | body | number | да (`@NotNull`, `@Positive`, `@Digits`) | Сумма больше нуля; не более 13 цифр в целой части и 2 цифр в дробной части. |
| currency | body | string | нет | Код валюты по шаблону `[A-Z]{3}`; при `null` используется `RUB` (`TransferRequest.currencyOrDefault`). |

#### Параметры ответа

| Поле | Тип | Nullable | Описание |
|---|---|---|---|
| id | integer (int64) | нет | Идентификатор перевода. |
| senderId | integer (int64) | нет | Идентификатор отправителя. |
| recipientId | integer (int64) | нет | Идентификатор получателя. |
| amount | number | нет | Сумма перевода. |
| currency | string | нет | Код валюты. |
| status | string | нет | `NEW`, `COMPLETED`, `CANCELLED` или `DENIED`. |
| decidedBy | string | да | Источник решения; в коде используются `limit` и `screening`. |
| messageInfo | string | да | Дополнительная информация о решении. |
| createdAt | string | нет | Дата и время создания (`LocalDateTime`). |
| updatedAt | string | да | Дата и время обновления (`LocalDateTime`); колонка БД допускает `NULL`. |

#### Коды ответа

| Код | Условие |
|---|---|
| 201 | Создан новый перевод; тело содержит структуру из таблицы параметров ответа. |
| 200 | Перевод с тем же `id` и теми же параметрами уже существует; возвращается существующая запись. |
| 400 | Тело запроса не десериализовано или не прошло валидацию `TransferRequest`; возвращается `ProblemDetail`. |
| 409 | Перевод с таким `id` уже существует, но параметры запроса отличаются; возвращается `ProblemDetail`. |
| 503 | Не удалось опубликовать сообщение в Kafka; возвращается `ProblemDetail`. |
| 500 | Неперехваченное исключение; возвращается `ProblemDetail` из `ApiExceptionHandler.handleUnexpected`. |

### Эндпоинт: GET /transfers

**Описание:** Возвращает массив переводов (`transfer-service/src/main/java/com/example/transfer/controller/TransferController.java`, `TransferController.all`).

#### Параметры запроса

| Параметр | Источник (body/path/query) | Тип | Обязательный | Описание |
|---|---|---|---|---|
| — | — | — | — | Параметры в коде не найдены. |

#### Параметры ответа

| Поле | Тип | Nullable | Описание |
|---|---|---|---|
| [] | array\<object\> | нет | Корневой массив переводов. |
| [].id | integer (int64) | нет | Идентификатор перевода. |
| [].senderId | integer (int64) | нет | Идентификатор отправителя. |
| [].recipientId | integer (int64) | нет | Идентификатор получателя. |
| [].amount | number | нет | Сумма перевода. |
| [].currency | string | нет | Код валюты. |
| [].status | string | нет | `NEW`, `COMPLETED`, `CANCELLED` или `DENIED`. |
| [].decidedBy | string | да | Источник решения; в коде используются `limit` и `screening`. |
| [].messageInfo | string | да | Дополнительная информация о решении. |
| [].createdAt | string | нет | Дата и время создания (`LocalDateTime`). |
| [].updatedAt | string | да | Дата и время обновления (`LocalDateTime`). |

#### Коды ответа

| Код | Условие |
|---|---|
| 200 | Возвращён массив переводов; массив может быть пустым. |
| 500 | Неперехваченное исключение; возвращается `ProblemDetail` из `ApiExceptionHandler.handleUnexpected`. |

### Эндпоинт: GET /transfers/{id}

**Описание:** Возвращает перевод и массив записей его истории (`transfer-service/src/main/java/com/example/transfer/controller/TransferController.java`, `TransferController.getById`).

#### Параметры запроса

| Параметр | Источник (body/path/query) | Тип | Обязательный | Описание |
|---|---|---|---|---|
| id | path | integer (int64) | да | Идентификатор перевода; дополнительные ограничения в коде не найдены. |

#### Параметры ответа

| Поле | Тип | Nullable | Описание |
|---|---|---|---|
| transfer | object | нет | Данные перевода. |
| transfer.id | integer (int64) | нет | Идентификатор перевода. |
| transfer.senderId | integer (int64) | нет | Идентификатор отправителя. |
| transfer.recipientId | integer (int64) | нет | Идентификатор получателя. |
| transfer.amount | number | нет | Сумма перевода. |
| transfer.currency | string | нет | Код валюты. |
| transfer.status | string | нет | `NEW`, `COMPLETED`, `CANCELLED` или `DENIED`. |
| transfer.decidedBy | string | да | Источник решения. |
| transfer.messageInfo | string | да | Дополнительная информация о решении. |
| transfer.createdAt | string | нет | Дата и время создания (`LocalDateTime`). |
| transfer.updatedAt | string | да | Дата и время обновления (`LocalDateTime`). |
| history | array\<object\> | нет | Массив записей истории; может быть пустым. |
| history[].id | integer (int64) | нет | Идентификатор записи истории. |
| history[].transferId | integer (int64) | нет | Идентификатор перевода. |
| history[].stage | string | нет | `NEW`, `LIMIT`, `SCREENING` или `FINAL`. |
| history[].status | string | нет | `NEW`, `APPROVED`, `DECLINED`, `COMPLETED`, `CANCELLED` или `DENIED`. |
| history[].decidedBy | string | да | Источник решения. |
| history[].messageInfo | string | да | Дополнительная информация о решении. |
| history[].changedAt | string | нет | Дата и время записи (`LocalDateTime`). |

#### Коды ответа

| Код | Условие |
|---|---|
| 200 | Перевод найден; возвращаются `transfer` и `history`. |
| 400 | Значение `id` не может быть преобразовано в `Long`. |
| 404 | Перевод с указанным `id` не найден; тело ответа отсутствует. |
| 500 | Неперехваченное исключение; возвращается `ProblemDetail` из `ApiExceptionHandler.handleUnexpected`. |

### Базовая структура ProblemDetail

| Поле | Тип | Nullable | Описание |
|---|---|---|---|
| type | string | нет | URI типа проблемы. |
| title | string | да | Краткое название проблемы. |
| status | integer | нет | HTTP-код ответа. |
| detail | string | да | Подробное описание ошибки. |
| instance | string | да | URI экземпляра проблемы; явное заполнение в коде не найдено. |

## 2. СХЕМА БД

### Таблица: sender_usage (база: limitdb)

**Описание:** Хранит суммы использования и даты сбросов для отправителя (`limit-service/src/main/resources/db/changelog/001-create-tables.sql`).

| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
|---|---|---|---|---|---|---|---|---|
| sender_id | BIGINT | да | нет | да (PK) | да (PK) | нет | да (PK; имя в коде не найдено) | Идентификатор отправителя. |
| daily_used | DECIMAL(15,2) | нет | нет | нет | да | 0 | нет | Дневная сумма использования. |
| monthly_used | DECIMAL(15,2) | нет | нет | нет | да | 0 | нет | Месячная сумма использования. |
| last_daily_reset | DATE | нет | нет | нет | да | CURRENT_DATE | нет | Дата последнего дневного сброса. |
| last_monthly_reset | DATE | нет | нет | нет | да | CURRENT_DATE | нет | Дата последнего месячного сброса. |
| version | BIGINT | нет | нет | нет | да | 0 | нет | Версия записи. |

### Таблица: reservations (база: limitdb)

**Описание:** Хранит решение и состояние резервирования по переводу (`limit-service/src/main/resources/db/changelog/001-create-tables.sql`).

| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
|---|---|---|---|---|---|---|---|---|
| transfer_id | BIGINT | да | нет | да (PK) | да (PK) | нет | да (PK; имя в коде не найдено) | Идентификатор перевода. |
| sender_id | BIGINT | нет | нет | нет | да | нет | idx_reservations_sender_id | Идентификатор отправителя. |
| amount | DECIMAL(15,2) | нет | нет | нет | да | нет | нет | Сумма резервирования. |
| decision | VARCHAR(10) | нет | нет | нет | да | нет | нет | Решение по переводу. |
| reason | VARCHAR(50) | нет | нет | нет | нет | нет | нет | Причина отклонения. |
| state | VARCHAR(20) | нет | нет | нет | да | нет | нет | Состояние резервирования. |
| reserved_on | DATE | нет | нет | нет | да | нет | нет | Дата резервирования. |
| created_at | TIMESTAMP | нет | нет | нет | да | нет | нет | Дата и время создания записи. |
| updated_at | TIMESTAMP | нет | нет | нет | да | нет | нет | Дата и время обновления записи. |

#### Допустимые значения

| Колонка | Значения |
|---|---|
| decision | `APPROVED`, `DECLINED` |
| reason | `NULL`, `INVALID_AMOUNT`, `DAILY_LIMIT_EXCEEDED`, `MONTHLY_LIMIT_EXCEEDED` |
| state | `RESERVED`, `COMMITTED`, `RELEASED`, `NONE` |

### Таблица: blocked_senders (база: screeningdb)

**Описание:** Хранит идентификаторы заблокированных отправителей и причины блокировки (`screening-service/src/main/resources/db/changelog/001-create-tables.sql`).

| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
|---|---|---|---|---|---|---|---|---|
| id | BIGSERIAL | да | нет | да (PK) | да (PK) | автогенерация BIGSERIAL | да (PK; имя в коде не найдено) | Идентификатор записи. |
| sender_id | BIGINT | нет | нет | да | да | нет | idx_blocked_senders_sender_id; также индекс UNIQUE | Идентификатор отправителя. |
| reason | VARCHAR(255) | нет | нет | нет | да | нет | нет | Причина блокировки. |
| blocked_at | TIMESTAMP | нет | нет | нет | да | NOW() | нет | Дата и время блокировки. |

### Таблица: screening_checks (база: screeningdb)

**Описание:** Хранит результат проверки перевода (`screening-service/src/main/resources/db/changelog/001-create-tables.sql`).

| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
|---|---|---|---|---|---|---|---|---|
| transfer_id | BIGINT | да | нет | да (PK) | да (PK) | нет | да (PK; имя в коде не найдено) | Идентификатор перевода. |
| sender_id | BIGINT | нет | нет | нет | да | нет | idx_screening_checks_sender_id | Идентификатор отправителя. |
| result | VARCHAR(10) | нет | нет | нет | да | нет | нет | Результат проверки. |
| reason | VARCHAR(50) | нет | нет | нет | нет | нет | нет | Причина отклонения. |
| checked_at | TIMESTAMP | нет | нет | нет | да | нет | нет | Дата и время проверки. |

#### Допустимые значения

| Колонка | Значения |
|---|---|
| result | `APPROVED`, `DECLINED` |
| reason | `NULL`, `SENDER_BLOCKED` |

### Таблица: transfers (база: transferdb)

**Описание:** Хранит основные атрибуты и текущее представление перевода (`transfer-service/src/main/resources/db/changelog/001-create-tables.sql`).

| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
|---|---|---|---|---|---|---|---|---|
| id | BIGINT | да | нет | да (PK) | да (PK) | нет | да (PK; имя в коде не найдено) | Идентификатор перевода. |
| sender_id | BIGINT | нет | нет | нет | да | нет | idx_transfers_sender_id | Идентификатор отправителя. |
| recipient_id | BIGINT | нет | нет | нет | да | нет | нет | Идентификатор получателя. |
| amount | DECIMAL(15,2) | нет | нет | нет | да | нет | нет | Сумма перевода. |
| currency | VARCHAR(10) | нет | нет | нет | да | 'RUB' | нет | Код валюты. |
| status | VARCHAR(20) | нет | нет | нет | да | нет | idx_transfers_status | Текущий статус перевода. |
| decided_by | VARCHAR(20) | нет | нет | нет | нет | нет | нет | Источник решения. |
| message_info | TEXT | нет | нет | нет | нет | нет | нет | Дополнительная информация о решении. |
| created_at | TIMESTAMP | нет | нет | нет | да | нет | нет | Дата и время создания. |
| updated_at | TIMESTAMP | нет | нет | нет | нет | нет | нет | Дата и время обновления. |

#### Допустимые значения

| Колонка | Значения |
|---|---|
| status | `NEW`, `COMPLETED`, `CANCELLED`, `DENIED` |
| decided_by | `NULL`, `limit`, `screening` |

### Таблица: transfer_history (база: transferdb)

**Описание:** Хранит записи истории по этапам перевода (`transfer-service/src/main/resources/db/changelog/001-create-tables.sql`).

| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
|---|---|---|---|---|---|---|---|---|
| id | BIGSERIAL | да | нет | да (PK) | да (PK) | автогенерация BIGSERIAL | да (PK; имя в коде не найдено) | Идентификатор записи истории. |
| transfer_id | BIGINT | нет | transfers(id) | нет | да | нет | idx_transfer_history_transfer_id; uq_transfer_history_stage | Идентификатор перевода. |
| status | VARCHAR(20) | нет | нет | нет | да | нет | нет | Статус на соответствующем этапе. |
| decided_by | VARCHAR(20) | нет | нет | нет | нет | нет | нет | Источник решения. |
| message_info | TEXT | нет | нет | нет | нет | нет | нет | Дополнительная информация о решении. |
| changed_at | TIMESTAMP | нет | нет | нет | да | NOW() | нет | Дата и время изменения. |
| stage | VARCHAR(20) | нет | нет | нет | да | нет | uq_transfer_history_stage | Этап истории. |

#### Ограничения уровня таблицы

| Ограничение | Колонки |
|---|---|
| uq_transfer_history_stage — UNIQUE | transfer_id, stage |

#### Допустимые значения

| Колонка | Значения |
|---|---|
| stage | `NEW`, `LIMIT`, `SCREENING`, `FINAL` |
| status | `NEW`, `APPROVED`, `DECLINED`, `COMPLETED`, `CANCELLED`, `DENIED` |
| decided_by | `NULL`, `limit`, `screening` |

## 3. КОНТРАКТЫ СООБЩЕНИЙ

### Канал: transfers

| Свойство | Значение |
|---|---|
| Тип канала | Kafka topic |
| Число партиций | 3 |
| Политика хранения / очистки | `cleanup.policy=compact`; настройки `retention.*` в коде не найдены |
| Ключ сообщения | `Long` — идентификатор перевода `Transfer.id` |
| Сериализация ключа | `LongSerializer`, `LongDeserializer` через `ErrorHandlingDeserializer`, `Serdes.Long` / `LongSerde` |
| Сериализация значения | JSON: `JsonSerializer`, `JsonDeserializer` через `ErrorHandlingDeserializer`, `JsonSerde<Transfer>` |
| Отправители | `transfer-service`: `TransferController.java — TransferController.publishNew`;<br>`transfer-service`: `DecisionTopologyConfig.java — DecisionTopologyConfig.decisionStream` |
| Получатели | `limit-service`: `TransferEventsListener.java — TransferEventsListener.onEvent`, группа `limit`;<br>`screening-service`: `TransferEventsListener.java — TransferEventsListener.onEvent`, группа `screening`;<br>`transfer-service`: `TransferEventListener.java — TransferEventListener.onEvent`, группа `transfer-tracker` |
| Значения status | `NEW`, `COMPLETED`, `CANCELLED`, `DENIED` |
| Формат сообщения | `Transfer` — см. общую таблицу ниже |

### Канал: limit-results

| Свойство | Значение |
|---|---|
| Тип канала | Kafka topic |
| Число партиций | 3 |
| Политика хранения / очистки | `cleanup.policy=compact`; настройки `retention.*` в коде не найдены |
| Ключ сообщения | `Long` — идентификатор перевода `Transfer.id` |
| Сериализация ключа | `LongSerializer`, `LongDeserializer` через `ErrorHandlingDeserializer`, `Serdes.Long` |
| Сериализация значения | JSON: `JsonSerializer`, `JsonDeserializer` через `ErrorHandlingDeserializer`, `JsonSerde<Transfer>` |
| Отправители | `limit-service`: `LimitService.java — LimitService.sendAndWait` |
| Получатели | `transfer-service`: `DecisionTopologyConfig.java — DecisionTopologyConfig.decisionStream`, группа определяется `application.id`, значение в коде не найдено;<br>`transfer-service`: `TransferEventListener.java — TransferEventListener.onEvent`, группа `transfer-tracker` |
| Значения status | `APPROVED`, `DECLINED` |
| Формат сообщения | `Transfer` — см. общую таблицу ниже |

### Канал: screening-results

| Свойство | Значение |
|---|---|
| Тип канала | Kafka topic |
| Число партиций | 3 |
| Политика хранения / очистки | `cleanup.policy=compact`; настройки `retention.*` в коде не найдены |
| Ключ сообщения | `Long` — идентификатор перевода `Transfer.id` |
| Сериализация ключа | `LongSerializer`, `LongDeserializer` через `ErrorHandlingDeserializer`, `Serdes.Long` |
| Сериализация значения | JSON: `JsonSerializer`, `JsonDeserializer` через `ErrorHandlingDeserializer`, `JsonSerde<Transfer>` |
| Отправители | `screening-service`: `ScreeningService.java — ScreeningService.sendAndWait` |
| Получатели | `transfer-service`: `DecisionTopologyConfig.java — DecisionTopologyConfig.decisionStream`, группа определяется `application.id`, значение в коде не найдено;<br>`transfer-service`: `TransferEventListener.java — TransferEventListener.onEvent`, группа `transfer-tracker` |
| Значения status | `APPROVED`, `DECLINED` |
| Формат сообщения | `Transfer` — см. общую таблицу ниже |

### Канал: transfers.DLT

| Свойство | Значение |
|---|---|
| Тип канала | Kafka dead-letter topic |
| Число партиций | 3 |
| Политика хранения / очистки | `cleanup.policy` и настройки `retention.*` в коде не заданы |
| Ключ сообщения | Ключ исходной записи: `Long` или `byte[]` |
| Сериализация ключа | `DelegatingByTypeSerializer`: `LongSerializer` или `ByteArraySerializer` |
| Сериализация значения | `JsonSerializer` для объектов либо `ByteArraySerializer` для `byte[]` |
| Отправители | `limit-service`, `screening-service`, `transfer-service`: `KafkaErrorHandlingConfig.java — KafkaErrorHandlingConfig.kafkaErrorHandler` |
| Получатели | В коде не найдено |
| Формат сообщения | Общий формат DLT — см. таблицу ниже |

### Канал: limit-results.DLT

| Свойство | Значение |
|---|---|
| Тип канала | Kafka dead-letter topic |
| Число партиций | 3 |
| Политика хранения / очистки | `cleanup.policy` и настройки `retention.*` в коде не заданы |
| Ключ сообщения | Ключ исходной записи: `Long` или `byte[]` |
| Сериализация ключа | `DelegatingByTypeSerializer`: `LongSerializer` или `ByteArraySerializer` |
| Сериализация значения | `JsonSerializer` для объектов либо `ByteArraySerializer` для `byte[]` |
| Отправители | `transfer-service`: `KafkaErrorHandlingConfig.java — KafkaErrorHandlingConfig.kafkaErrorHandler` |
| Получатели | В коде не найдено |
| Формат сообщения | Общий формат DLT — см. таблицу ниже |

### Канал: screening-results.DLT

| Свойство | Значение |
|---|---|
| Тип канала | Kafka dead-letter topic |
| Число партиций | 3 |
| Политика хранения / очистки | `cleanup.policy` и настройки `retention.*` в коде не заданы |
| Ключ сообщения | Ключ исходной записи: `Long` или `byte[]` |
| Сериализация ключа | `DelegatingByTypeSerializer`: `LongSerializer` или `ByteArraySerializer` |
| Сериализация значения | `JsonSerializer` для объектов либо `ByteArraySerializer` для `byte[]` |
| Отправители | `transfer-service`: `KafkaErrorHandlingConfig.java — KafkaErrorHandlingConfig.kafkaErrorHandler` |
| Получатели | В коде не найдено |
| Формат сообщения | Общий формат DLT — см. таблицу ниже |

### Формат сообщения Transfer

**Каналы:** `transfers`, `limit-results`, `screening-results`.

| Поле | Тип | Nullable | Описание | Пример |
|---|---|---|---|---|
| id | Long | нет | Идентификатор перевода. | `1` |
| senderId | Long | нет | Идентификатор отправителя. | `10` |
| recipientId | Long | нет | Идентификатор получателя. | `20` |
| amount | BigDecimal | да | Сумма перевода; `null` явно обрабатывается в `LimitLedger.decide`, хотя HTTP-источник задаёт `@NotNull`. | `1250.50` |
| currency | String | нет | Код валюты; для сообщений, созданных через HTTP API, пустое значение заменяется на `RUB`. | `"RUB"` |
| status | String | нет | `NEW`, `APPROVED`, `DECLINED`, `COMPLETED`, `CANCELLED` или `DENIED`. | `"APPROVED"` |
| decidedBy | String | да | Источник решения; используются `limit` и `screening`. | `"limit"` |
| messageInfo | String | да | Дополнительная информация или причина решения. | `"DAILY_LIMIT_EXCEEDED"` |
| createdAt | LocalDateTime | нет | Дата и время создания перевода. | `"2026-01-15T10:15:30"` |

### Формат сообщения DLT

**Каналы:** `transfers.DLT`, `limit-results.DLT`, `screening-results.DLT`.

| Поле | Тип | Nullable | Описание | Пример |
|---|---|---|---|---|
| Значение сообщения | Transfer или byte[] | требует уточнения | Для `Transfer` используются поля общей таблицы; структура варианта `byte[]` в коде не найдена. | См. `Transfer`; пример структуры `byte[]` в коде не найден |

## 4. РАСХОЖДЕНИЯ КОНТРАКТА API С КОДОМ

| Элемент | В контракте | В коде |
|---|---|---|
| Ответ 500 для `POST /transfers`, `GET /transfers`, `GET /transfers/{id}` | Не описан | `ApiExceptionHandler.java — ApiExceptionHandler.handleUnexpected` возвращает `ProblemDetail` со статусом 500. |
| Ответ 400 для `GET /transfers/{id}` | Не описан | `TransferController.java — TransferController.getById` принимает `id` как `Long`; ошибка преобразования path-параметра приводит к ответу 400. |
| `Transfer.updatedAt` | Указан `type: string`; `nullable: true` отсутствует | `TransferEntity.java — TransferEntity.updatedAt` имеет тип `LocalDateTime`; в `transfer-service/src/main/resources/db/changelog/001-create-tables.sql` колонка `updated_at TIMESTAMP` допускает `NULL`. |