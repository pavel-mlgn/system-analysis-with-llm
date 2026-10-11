# 1. HTTP API

## Эндпоинт: POST /transfers

**Описание:** Создаёт ресурс перевода и возвращает его представление; источник — `transfer-service/src/main/java/com/example/transfer/controller/TransferController.java`, `TransferController#create`.

### Параметры запроса

| Параметр | Источник (body/path/query) | Тип | Обязательный | Описание |
|---|---|---|---|---|
| id | body | integer | да | `TransferRequest.id`; `@NotNull`. |
| senderId | body | integer | да | `TransferRequest.senderId`; `@NotNull`. |
| recipientId | body | integer | да | `TransferRequest.recipientId`; `@NotNull`. |
| amount | body | number | да | `TransferRequest.amount`; `@NotNull`, `@Positive`, `@Digits(integer=13, fraction=2)`. |
| currency | body | string | нет | `TransferRequest.currency`; при наличии проверяется по `[A-Z]{3}`; при `null` используется `"RUB"` в `TransferRequest#currencyOrDefault`. |

### Параметры ответа

| Поле | Тип | Nullable | Описание |
|---|---|---|---|
| id | integer | нет | Идентификатор; `TransferEntity.id`. |
| senderId | integer | нет | `TransferEntity.senderId`. |
| recipientId | integer | нет | `TransferEntity.recipientId`. |
| amount | number | нет | `TransferEntity.amount`. |
| currency | string | нет | `TransferEntity.currency`. |
| status | string | нет | `TransferEntity.status`. |
| decidedBy | string | да | `TransferEntity.decidedBy`. |
| messageInfo | string | да | `TransferEntity.messageInfo`. |
| createdAt | string | нет | JSON-представление `LocalDateTime`; `TransferEntity.createdAt`. |
| updatedAt | string | да | JSON-представление `LocalDateTime`; поле Java допускает `null`, колонка БД также nullable. |

### Коды ответа

| Код | Условие |
|---|---|
| 201 | Новый ресурс создан; `TransferController#create`. |
| 200 | Ресурс с тем же `id` и совпадающими входными параметрами уже существует; `TransferController#duplicate`. |
| 400 | Нарушена валидация `TransferRequest` при `@Valid`; обработка наследуется через `ResponseEntityExceptionHandler`. |
| 409 | Ресурс с тем же `id` существует, но входные параметры отличаются; тело — `ProblemDetail`; `TransferController#duplicate`. |
| 503 | Ошибка публикации в Kafka; `TransferController#publishNew`. |
| 500 | Необработанное исключение; `ApiExceptionHandler#handleUnexpected`. |

## Эндпоинт: GET /transfers

**Описание:** Возвращает массив представлений переводов; источник — `transfer-service/src/main/java/com/example/transfer/controller/TransferController.java`, `TransferController#all`.

### Параметры запроса

| Параметр | Источник (body/path/query) | Тип | Обязательный | Описание |
|---|---|---|---|---|
| в коде не найдено | — | — | — | Параметры запроса методом `TransferController#all` не объявлены. |

### Параметры ответа

| Поле | Тип | Nullable | Описание |
|---|---|---|---|
| [] | array<object> | нет | Массив `TransferEntity`. |
| [].id | integer | нет | `TransferEntity.id`. |
| [].senderId | integer | нет | `TransferEntity.senderId`. |
| [].recipientId | integer | нет | `TransferEntity.recipientId`. |
| [].amount | number | нет | `TransferEntity.amount`. |
| [].currency | string | нет | `TransferEntity.currency`. |
| [].status | string | нет | `TransferEntity.status`. |
| [].decidedBy | string | да | `TransferEntity.decidedBy`. |
| [].messageInfo | string | да | `TransferEntity.messageInfo`. |
| [].createdAt | string | нет | JSON-представление `LocalDateTime`. |
| [].updatedAt | string | да | JSON-представление `LocalDateTime`; поле Java и колонка БД допускают `null`. |

### Коды ответа

| Код | Условие |
|---|---|
| 200 | Метод `TransferController#all` завершён успешно. |
| 500 | Необработанное исключение; `ApiExceptionHandler#handleUnexpected`. |

## Эндпоинт: GET /transfers/{id}

**Описание:** Возвращает объект перевода и массив его записей истории; источник — `transfer-service/src/main/java/com/example/transfer/controller/TransferController.java`, `TransferController#getById`.

### Параметры запроса

| Параметр | Источник (body/path/query) | Тип | Обязательный | Описание |
|---|---|---|---|---|
| id | path | integer | да | `Long id`; обязательность определяется шаблоном `/{id}`; дополнительная валидация значения в коде отсутствует. |

### Параметры ответа

| Поле | Тип | Nullable | Описание |
|---|---|---|---|
| transfer | object | нет | Объект `TransferEntity`. |
| transfer.id | integer | нет | `TransferEntity.id`. |
| transfer.senderId | integer | нет | `TransferEntity.senderId`. |
| transfer.recipientId | integer | нет | `TransferEntity.recipientId`. |
| transfer.amount | number | нет | `TransferEntity.amount`. |
| transfer.currency | string | нет | `TransferEntity.currency`. |
| transfer.status | string | нет | `TransferEntity.status`. |
| transfer.decidedBy | string | да | `TransferEntity.decidedBy`. |
| transfer.messageInfo | string | да | `TransferEntity.messageInfo`. |
| transfer.createdAt | string | нет | JSON-представление `LocalDateTime`. |
| transfer.updatedAt | string | да | JSON-представление `LocalDateTime`; поле Java и колонка БД допускают `null`. |
| history | array<object> | нет | Массив `TransferHistoryEntry`. |
| history[].id | integer | нет | `TransferHistoryEntry.id`. |
| history[].transferId | integer | нет | `TransferHistoryEntry.transferId`. |
| history[].stage | string | нет | `TransferHistoryEntry.stage`; Java-тип `HistoryStage`. |
| history[].status | string | нет | `TransferHistoryEntry.status`. |
| history[].decidedBy | string | да | `TransferHistoryEntry.decidedBy`. |
| history[].messageInfo | string | да | `TransferHistoryEntry.messageInfo`. |
| history[].changedAt | string | нет | JSON-представление `LocalDateTime`; `TransferHistoryEntry.changedAt`. |

### Коды ответа

| Код | Условие |
|---|---|
| 200 | Ресурс с указанным `id` найден; `TransferController#getById`. |
| 404 | Ресурс с указанным `id` не найден; `TransferController#getById`. |
| 500 | Необработанное исключение; `ApiExceptionHandler#handleUnexpected`. |

# 2. СХЕМА БД

## Таблица: sender_usage (база: limitdb)

**Описание:** Таблица состояния использования по отправителю; источник — `limit-service/src/main/resources/db/changelog/001-create-tables.sql`.

| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
|---|---|---|---|---|---|---|---|---|
| sender_id | BIGINT | да | нет | да (PK) | да | — | PRIMARY KEY | Идентификатор отправителя. |
| daily_used | DECIMAL(15,2) | нет | нет | нет | да | 0 | нет | Числовое значение дневного использования. |
| monthly_used | DECIMAL(15,2) | нет | нет | нет | да | 0 | нет | Числовое значение месячного использования. |
| last_daily_reset | DATE | нет | нет | нет | да | CURRENT_DATE | нет | Дата дневного сброса. |
| last_monthly_reset | DATE | нет | нет | нет | да | CURRENT_DATE | нет | Дата месячного сброса. |
| version | BIGINT | нет | нет | нет | да | 0 | нет | Версия записи; отображается как `@Version` в `SenderUsage`. |

## Таблица: reservations (база: limitdb)

**Описание:** Таблица записей резервирования по переводам; источник — `limit-service/src/main/resources/db/changelog/001-create-tables.sql`, отображение — `Reservation`.

| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
|---|---|---|---|---|---|---|---|---|
| transfer_id | BIGINT | да | нет | да (PK) | да | — | PRIMARY KEY | Идентификатор перевода. |
| sender_id | BIGINT | нет | нет | нет | да | — | idx_reservations_sender_id | Идентификатор отправителя. |
| amount | DECIMAL(15,2) | нет | нет | нет | да | — | нет | Сумма. |
| decision | VARCHAR(10) | нет | нет | нет | да | — | нет | Решение. |
| reason | VARCHAR(50) | нет | нет | нет | нет | — | нет | Причина решения. |
| state | VARCHAR(20) | нет | нет | нет | да | — | нет | Состояние записи. |
| reserved_on | DATE | нет | нет | нет | да | — | нет | Дата резервирования. |
| created_at | TIMESTAMP | нет | нет | нет | да | — | нет | Время создания. |
| updated_at | TIMESTAMP | нет | нет | нет | да | — | нет | Время изменения. |

### Допустимые значения

| Колонка | Значения |
|---|---|
| decision | `APPROVED`, `DECLINED` — константы `Reservation`; SQL `CHECK` в коде не найден. |
| state | `RESERVED`, `COMMITTED`, `RELEASED`, `NONE` — константы `Reservation`; SQL `CHECK` в коде не найден. |

## Таблица: blocked_senders (база: screeningdb)

**Описание:** Таблица записей заблокированных отправителей; источник — `screening-service/src/main/resources/db/changelog/001-create-tables.sql`, отображение — `BlockedSender`.

| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
|---|---|---|---|---|---|---|---|---|
| id | BIGSERIAL | да | нет | да (PK) | да | последовательность BIGSERIAL | PRIMARY KEY | Идентификатор записи. |
| sender_id | BIGINT | нет | нет | да | да | — | idx_blocked_senders_sender_id; UNIQUE | Идентификатор отправителя. |
| reason | VARCHAR(255) | нет | нет | нет | да | — | нет | Текст причины. |
| blocked_at | TIMESTAMP | нет | нет | нет | да | NOW() | нет | Время блокировки. |

## Таблица: screening_checks (база: screeningdb)

**Описание:** Таблица результатов проверок переводов; источник — `screening-service/src/main/resources/db/changelog/001-create-tables.sql`, отображение — `ScreeningCheck`.

| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
|---|---|---|---|---|---|---|---|---|
| transfer_id | BIGINT | да | нет | да (PK) | да | — | PRIMARY KEY | Идентификатор перевода. |
| sender_id | BIGINT | нет | нет | нет | да | — | idx_screening_checks_sender_id | Идентификатор отправителя. |
| result | VARCHAR(10) | нет | нет | нет | да | — | нет | Результат проверки. |
| reason | VARCHAR(50) | нет | нет | нет | нет | — | нет | Причина результата. |
| checked_at | TIMESTAMP | нет | нет | нет | да | — | нет | Время проверки. |

### Допустимые значения

| Колонка | Значения |
|---|---|
| result | `APPROVED`, `DECLINED` — константы `ScreeningCheck`; SQL `CHECK` в коде не найден. |

## Таблица: transfers (база: transferdb)

**Описание:** Таблица представлений переводов; источник — `transfer-service/src/main/resources/db/changelog/001-create-tables.sql`, отображение — `TransferEntity`.

| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
|---|---|---|---|---|---|---|---|---|
| id | BIGINT | да | нет | да (PK) | да | — | PRIMARY KEY | Идентификатор перевода. |
| sender_id | BIGINT | нет | нет | нет | да | — | idx_transfers_sender_id | Идентификатор отправителя. |
| recipient_id | BIGINT | нет | нет | нет | да | — | нет | Идентификатор получателя. |
| amount | DECIMAL(15,2) | нет | нет | нет | да | — | нет | Сумма. |
| currency | VARCHAR(10) | нет | нет | нет | да | `'RUB'` | нет | Валюта. |
| status | VARCHAR(20) | нет | нет | нет | да | — | idx_transfers_status | Статус. |
| decided_by | VARCHAR(20) | нет | нет | нет | нет | — | нет | Источник решения. |
| message_info | TEXT | нет | нет | нет | нет | — | нет | Дополнительная текстовая информация. |
| created_at | TIMESTAMP | нет | нет | нет | да | — | нет | Время создания. |
| updated_at | TIMESTAMP | нет | нет | нет | нет | — | нет | Время изменения. |

### Допустимые значения

| Колонка | Значения |
|---|---|
| status | `NEW`, `COMPLETED`, `CANCELLED`, `DENIED` — значения, формируемые `TransferTrackingService#createTransfer` и `TransferDecisionService#decide`; SQL `CHECK` в коде не найден. |

## Таблица: transfer_history (база: transferdb)

**Описание:** Таблица записей истории перевода; источник — `transfer-service/src/main/resources/db/changelog/001-create-tables.sql`, отображение — `TransferHistoryEntry`.

| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
|---|---|---|---|---|---|---|---|---|
| id | BIGSERIAL | да | нет | да (PK) | да | последовательность BIGSERIAL | PRIMARY KEY | Идентификатор записи истории. |
| transfer_id | BIGINT | нет | transfers(id) | нет отдельно | да | — | idx_transfer_history_transfer_id; uq_transfer_history_stage | Идентификатор перевода. |
| status | VARCHAR(20) | нет | нет | нет | да | — | нет | Статус записи истории. |
| decided_by | VARCHAR(20) | нет | нет | нет | нет | — | нет | Источник решения. |
| message_info | TEXT | нет | нет | нет | нет | — | нет | Дополнительная текстовая информация. |
| changed_at | TIMESTAMP | нет | нет | нет | да | NOW() | нет | Время изменения. |
| stage | VARCHAR(20) | нет | нет | нет отдельно | да | — | uq_transfer_history_stage | Этап; Java-тип `HistoryStage`. |

### Ограничения уровня таблицы

| Ограничение | Колонки |
|---|---|
| uq_transfer_history_stage — UNIQUE | transfer_id, stage |

### Допустимые значения

| Колонка | Значения |
|---|---|
| stage | `NEW`, `LIMIT`, `SCREENING`, `FINAL` — `HistoryStage`. |
| status | `NEW`, `APPROVED`, `DECLINED`, `COMPLETED`, `CANCELLED`, `DENIED` — значения, используемые кодом; SQL `CHECK` в коде не найден. |

# 3. КОНТРАКТЫ СООБЩЕНИЙ

## Канал: transfers

| Свойство | Значение |
|---|---|
| Тип канала | Kafka topic |
| Число партиций | 3; `TopicsConfig#transfersTopic`. |
| Политика хранения / очистки | `cleanup.policy=compact`; параметр retention в коде не найден. |
| Ключ сообщения | `Transfer.id()` / ключ Kafka Streams; тип `Long`. |
| Сериализация ключа | `LongSerializer`; для Kafka Streams — `Serdes.Long()`. |
| Сериализация значения | `JsonSerializer<Transfer>`; для Kafka Streams — `JsonSerde<Transfer>`. |
| Отправители | `transfer-service`: `TransferController#publishNew`; `DecisionTopologyConfig#decisionStream`. |
| Получатели | `limit-service`: `TransferEventsListener#onEvent`, группа `limit`; `screening-service`: `TransferEventsListener#onEvent`, группа `screening`; `transfer-service`: `TransferEventListener#onEvent`, группа `transfer-tracker`. |
| Формат сообщения | `Transfer` — единый формат приведён ниже. |

## Канал: limit-results

| Свойство | Значение |
|---|---|
| Тип канала | Kafka topic |
| Число партиций | 3; `TopicsConfig#limitResultsTopic`. |
| Политика хранения / очистки | `cleanup.policy=compact`; параметр retention в коде не найден. |
| Ключ сообщения | `transfer.id()`; тип `Long`. |
| Сериализация ключа | `LongSerializer`; у Kafka Streams-потребителя — `Serdes.Long()`. |
| Сериализация значения | `JsonSerializer<Transfer>`; у Kafka Streams-потребителя — `JsonSerde<Transfer>`. |
| Отправители | `limit-service`: `LimitService#sendAndWait`. |
| Получатели | `transfer-service`: `TransferEventListener#onEvent`, группа `transfer-tracker`; `DecisionTopologyConfig#decisionStream`, группа Kafka Streams — в коде не найдена. |
| Формат сообщения | `Transfer` — единый формат приведён ниже. |

## Канал: screening-results

| Свойство | Значение |
|---|---|
| Тип канала | Kafka topic |
| Число партиций | 3; `TopicsConfig#screeningResultsTopic`. |
| Политика хранения / очистки | `cleanup.policy=compact`; параметр retention в коде не найден. |
| Ключ сообщения | `transfer.id()`; тип `Long`. |
| Сериализация ключа | `LongSerializer`; у Kafka Streams-потребителя — `Serdes.Long()`. |
| Сериализация значения | `JsonSerializer<Transfer>`; у Kafka Streams-потребителя — `JsonSerde<Transfer>`. |
| Отправители | `screening-service`: `ScreeningService#sendAndWait`. |
| Получатели | `transfer-service`: `TransferEventListener#onEvent`, группа `transfer-tracker`; `DecisionTopologyConfig#decisionStream`, группа Kafka Streams — в коде не найдена. |
| Формат сообщения | `Transfer` — единый формат приведён ниже. |

## Канал: transfers.DLT

| Свойство | Значение |
|---|---|
| Тип канала | Kafka topic, dead-letter topic |
| Число партиций | 3; `KafkaErrorHandlingConfig#transfersDlt`. |
| Политика хранения / очистки | в коде не найдено |
| Ключ сообщения | Исходный ключ записи; поддерживаемые кодом типы — `Long` или `byte[]`. |
| Сериализация ключа | `DelegatingByTypeSerializer`: `LongSerializer` для `Long`, `ByteArraySerializer` для `byte[]`. |
| Сериализация значения | `JsonSerializer<Object>` либо `ByteArraySerializer` для `byte[]`. |
| Отправители | `limit-service`, `screening-service`, `transfer-service`: `KafkaErrorHandlingConfig#kafkaErrorHandler`; адрес назначения формируется как `record.topic() + ".DLT"`. |
| Получатели | в коде не найдено |
| Формат сообщения | `Transfer` при JSON-варианте либо сырой `byte[]`; оба формата приведены ниже. |

## Канал: limit-results.DLT

| Свойство | Значение |
|---|---|
| Тип канала | Kafka topic, dead-letter topic |
| Число партиций | 3; `transfer-service` `KafkaErrorHandlingConfig#limitResultsDlt`. |
| Политика хранения / очистки | в коде не найдено |
| Ключ сообщения | Исходный ключ записи; поддерживаемые кодом типы — `Long` или `byte[]`. |
| Сериализация ключа | `DelegatingByTypeSerializer`: `LongSerializer` либо `ByteArraySerializer`. |
| Сериализация значения | `JsonSerializer<Object>` либо `ByteArraySerializer`. |
| Отправители | `transfer-service`: `KafkaErrorHandlingConfig#kafkaErrorHandler`. |
| Получатели | в коде не найдено |
| Формат сообщения | `Transfer` при JSON-варианте либо сырой `byte[]`; оба формата приведены ниже. |

## Канал: screening-results.DLT

| Свойство | Значение |
|---|---|
| Тип канала | Kafka topic, dead-letter topic |
| Число партиций | 3; `transfer-service` `KafkaErrorHandlingConfig#screeningResultsDlt`. |
| Политика хранения / очистки | в коде не найдено |
| Ключ сообщения | Исходный ключ записи; поддерживаемые кодом типы — `Long` или `byte[]`. |
| Сериализация ключа | `DelegatingByTypeSerializer`: `LongSerializer` либо `ByteArraySerializer`. |
| Сериализация значения | `JsonSerializer<Object>` либо `ByteArraySerializer`. |
| Отправители | `transfer-service`: `KafkaErrorHandlingConfig#kafkaErrorHandler`. |
| Получатели | в коде не найдено |
| Формат сообщения | `Transfer` при JSON-варианте либо сырой `byte[]`; оба формата приведены ниже. |

## Формат сообщения: Transfer

| Поле | Тип | Nullable | Описание | Пример |
|---|---|---|---|---|
| id | Long | да | Идентификатор; nullability явно не ограничена в `Transfer`. | в коде не найдено |
| senderId | Long | да | Идентификатор отправителя; nullability явно не ограничена в `Transfer`. | в коде не найдено |
| recipientId | Long | да | Идентификатор получателя; nullability явно не ограничена в `Transfer`. | в коде не найдено |
| amount | BigDecimal | да | Сумма; nullability явно не ограничена в `Transfer`. | в коде не найдено |
| currency | String | да | Код валюты; nullability явно не ограничена в `Transfer`. | `RUB` |
| status | String | да | Статус сообщения; известные значения из кода: `NEW`, `APPROVED`, `DECLINED`, `COMPLETED`, `CANCELLED`, `DENIED`; тип не ограничен enum. | `NEW` |
| decidedBy | String | да | Источник решения; код формирует в том числе `limit`, `screening` и `null`. | `limit` |
| messageInfo | String | да | Дополнительная текстовая информация. | `INVALID_AMOUNT` |
| createdAt | LocalDateTime | да | Время создания; nullability явно не ограничена в `Transfer`. | в коде не найдено |

## Формат сообщения: сырой DLT payload

| Поле | Тип | Nullable | Описание | Пример |
|---|---|---|---|---|
| payload | byte[] | да | Исходное значение записи, сериализуемое `ByteArraySerializer`; внутренние поля отсутствуют. | в коде не найдено |

# 4. РАСХОЖДЕНИЯ КОНТРАКТА API С КОДОМ

| Элемент | В контракте | В коде |
|---|---|---|
| `POST /transfers`, `TransferRequest.currency`: nullable | Поле необязательное, но `nullable: true` отсутствует; при передаче явного JSON `null` контракт OpenAPI 3.0 его не разрешает. | `TransferRequest.currency` не имеет `@NotNull`; `@Pattern` оставляет `null` допустимым, а `TransferRequest#currencyOrDefault` явно обрабатывает `null` и возвращает `RUB`. |
| `Transfer.updatedAt`: nullable | `type: string`, `nullable: true` отсутствует. | `TransferEntity.updatedAt` имеет ссылочный тип `LocalDateTime` без ограничения nullability, а колонка `transfers.updated_at` объявлена без `NOT NULL`. |
| HTTP 500 для API | Ответ `500` не объявлен ни для `POST /transfers`, ни для `GET /transfers`, ни для `GET /transfers/{id}`. | `ApiExceptionHandler#handleUnexpected` обрабатывает `Exception` и формирует `ProblemDetail` со статусом `500 INTERNAL_SERVER_ERROR`. |