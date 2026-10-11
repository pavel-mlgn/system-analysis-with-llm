## 1. HTTP API (transfer-service, порт 8080; у limit-service и screening-service HTTP API нет)

### Эндпоинт: POST /transfers
**Описание:** Создаёт перевод с идентификатором, который задаёт клиент, и публикует событие NEW в топик `transfers` (`TransferController.java` → `create()`).

**Параметры запроса** (тело — JSON, `TransferRequest.java`):

| Параметр | Источник (body/path/query) | Тип | Обязательный | Описание |
|---|---|---|---|---|
| id | body | integer (int64) | да (`@NotNull`) | Идентификатор перевода, задаётся клиентом |
| senderId | body | integer (int64) | да (`@NotNull`) | Идентификатор отправителя |
| recipientId | body | integer (int64) | да (`@NotNull`) | Идентификатор получателя |
| amount | body | number | да (`@NotNull`) | Сумма перевода: `@Positive` (> 0), `@Digits(integer = 13, fraction = 2)` |
| currency | body | string | нет | Код валюты, `@Pattern("[A-Z]{3}")`. Если поле не передано или равно `null`, используется `RUB` (`TransferRequest.java` → `currencyOrDefault()`) |

**Параметры ответа (201, 200)** — объект перевода (`TransferEntity.java`):

| Поле | Тип | Nullable | Описание |
|---|---|---|---|
| id | integer (int64) | нет | Идентификатор перевода |
| senderId | integer (int64) | нет | Идентификатор отправителя |
| recipientId | integer (int64) | нет | Идентификатор получателя |
| amount | number | нет | Сумма перевода |
| currency | string | нет | Код валюты |
| status | string | нет | Статус: NEW, COMPLETED, CANCELLED, DENIED (в ответе 201 всегда NEW) |
| decidedBy | string | да | Сервис, определивший итоговое решение: limit, screening |
| messageInfo | string | да | Причина или пояснение итогового решения |
| createdAt | string | нет | Дата-время создания в формате ISO-8601 без смещения (`LocalDateTime`), например `2026-10-02T12:30:15.123456` |
| updatedAt | string | нет (колонка в БД допускает NULL) | Дата-время последнего изменения, формат как у createdAt |

**Коды ответа:**

| Код | Условие |
|---|---|
| 201 | Перевода с таким id нет: перевод сохранён, событие NEW отправлено в Kafka. Тело — объект перевода |
| 200 | Перевод с таким id уже существует с теми же senderId, recipientId, amount, currency. Тело — существующий перевод |
| 400 | Нарушены ограничения `TransferRequest`, тело отсутствует или не разбирается как JSON (`ResponseEntityExceptionHandler`). Тело — ProblemDetail |
| 409 | Перевод с таким id уже существует с другими параметрами (`TransferController.java` → `duplicate()`). Тело — ProblemDetail |
| 415 | Content-Type запроса не поддерживается (`ResponseEntityExceptionHandler`). Тело — ProblemDetail |
| 500 | Любое необработанное исключение (`ApiExceptionHandler.java` → `handleUnexpected()`). Тело — ProblemDetail |
| 503 | Ошибка или таймаут (10 с) отправки события NEW в Kafka (`TransferController.java` → `publishNew()`). Тело — ProblemDetail |

### Эндпоинт: GET /transfers
**Описание:** Возвращает все переводы, отсортированные по createdAt по убыванию (`TransferController.java` → `all()`).

**Параметры запроса:**

| Параметр | Источник (body/path/query) | Тип | Обязательный | Описание |
|---|---|---|---|---|
| — | — | — | — | Параметров нет |

**Параметры ответа (200):**

| Поле | Тип | Nullable | Описание |
|---|---|---|---|
| (корень) | array | нет | Список переводов, может быть пустым |
| [].id | integer (int64) | нет | Идентификатор перевода |
| [].senderId | integer (int64) | нет | Идентификатор отправителя |
| [].recipientId | integer (int64) | нет | Идентификатор получателя |
| [].amount | number | нет | Сумма перевода |
| [].currency | string | нет | Код валюты |
| [].status | string | нет | Статус: NEW, COMPLETED, CANCELLED, DENIED |
| [].decidedBy | string | да | Сервис, определивший итоговое решение: limit, screening |
| [].messageInfo | string | да | Причина или пояснение итогового решения |
| [].createdAt | string | нет | Дата-время создания, ISO-8601 без смещения |
| [].updatedAt | string | нет (колонка в БД допускает NULL) | Дата-время последнего изменения, ISO-8601 без смещения |

**Коды ответа:**

| Код | Условие |
|---|---|
| 200 | Список успешно прочитан |
| 500 | Необработанное исключение (`ApiExceptionHandler.java` → `handleUnexpected()`). Тело — ProblemDetail |

### Эндпоинт: GET /transfers/{id}
**Описание:** Возвращает перевод по идентификатору вместе с историей этапов обработки (`TransferController.java` → `getById()`).

**Параметры запроса:**

| Параметр | Источник (body/path/query) | Тип | Обязательный | Описание |
|---|---|---|---|---|
| id | path | integer (int64) | да (переменная пути) | Идентификатор перевода (`@PathVariable Long id`) |

**Параметры ответа (200):**

| Поле | Тип | Nullable | Описание |
|---|---|---|---|
| (корень) | object | нет | Объект с полями transfer и history (`Map<String, Object>`) |
| transfer | object | нет | Перевод (`TransferEntity`) |
| transfer.id | integer (int64) | нет | Идентификатор перевода |
| transfer.senderId | integer (int64) | нет | Идентификатор отправителя |
| transfer.recipientId | integer (int64) | нет | Идентификатор получателя |
| transfer.amount | number | нет | Сумма перевода |
| transfer.currency | string | нет | Код валюты |
| transfer.status | string | нет | Статус: NEW, COMPLETED, CANCELLED, DENIED |
| transfer.decidedBy | string | да | Сервис, определивший итоговое решение: limit, screening |
| transfer.messageInfo | string | да | Причина или пояснение итогового решения |
| transfer.createdAt | string | нет | Дата-время создания, ISO-8601 без смещения |
| transfer.updatedAt | string | нет (колонка в БД допускает NULL) | Дата-время последнего изменения, ISO-8601 без смещения |
| history | array | нет | Записи истории (`TransferHistoryEntry`), упорядочены по stage (NEW → LIMIT → SCREENING → FINAL), затем по changedAt |
| history[].id | integer (int64) | нет | Идентификатор записи истории |
| history[].transferId | integer (int64) | нет | Идентификатор перевода |
| history[].stage | string | нет | Этап: NEW, LIMIT, SCREENING, FINAL |
| history[].status | string | нет | Статус на этапе: NEW, APPROVED, DECLINED, COMPLETED, CANCELLED, DENIED |
| history[].decidedBy | string | да | Источник решения на этапе: limit, screening |
| history[].messageInfo | string | да | Причина или пояснение |
| history[].changedAt | string | нет | Дата-время записи, ISO-8601 без смещения |

**Коды ответа:**

| Код | Условие |
|---|---|
| 200 | Перевод найден |
| 404 | Перевод не найден, тело пустое |
| 400 | id не приводится к Long (`MethodArgumentTypeMismatchException`, обрабатывается `ResponseEntityExceptionHandler`). Тело — ProblemDetail |
| 500 | Необработанное исключение (`ApiExceptionHandler.java` → `handleUnexpected()`). Тело — ProblemDetail |

### Тело ответа об ошибке — ProblemDetail (Content-Type: application/problem+json)

| Поле | Тип | Nullable | Описание |
|---|---|---|---|
| type | string | нет | URI типа ошибки. В коде не задаётся, Spring подставляет `about:blank` |
| title | string | нет | Reason phrase HTTP-статуса, например `Conflict` |
| status | integer | нет | HTTP-код ответа |
| detail | string | нет | 409 — `Перевод с id=… уже существует с другими параметрами`; 503 — `Kafka недоступна, перевод сохранён — повторите запрос с тем же id`; 500 — `Внутренняя ошибка сервера, повторите запрос позже`; 400 и 415 — стандартный текст Spring MVC |
| instance | string | нет | Путь запроса (подставляет Spring MVC) |

## 2. СХЕМА БД

### Таблица: transfers (база: transferdb)
**Описание:** Переводы, созданные через HTTP API, с текущим статусом (сущность `TransferEntity`, `001-create-tables.sql` в transfer-service).

| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
|---|---|---|---|---|---|---|---|---|
| id | BIGINT | да | — | да (PK) | да | — | PK (неявный) | Идентификатор перевода, задаётся клиентом |
| sender_id | BIGINT | — | — | — | да | — | `idx_transfers_sender_id` | Идентификатор отправителя |
| recipient_id | BIGINT | — | — | — | да | — | — | Идентификатор получателя |
| amount | DECIMAL(15,2) | — | — | — | да | — | — | Сумма перевода |
| currency | VARCHAR(10) | — | — | — | да | `'RUB'` | — | Код валюты |
| status | VARCHAR(20) | — | — | — | да | — | `idx_transfers_status` | Текущий статус перевода |
| decided_by | VARCHAR(20) | — | — | — | нет | — | — | Сервис, определивший итоговое решение |
| message_info | TEXT | — | — | — | нет | — | — | Причина или пояснение итогового решения |
| created_at | TIMESTAMP | — | — | — | да | — | — | Дата-время создания |
| updated_at | TIMESTAMP | — | — | — | нет | — | — | Дата-время последнего изменения |

**Допустимые значения** (задаются кодом, CHECK-ограничений в БД нет):

| Колонка | Значения |
|---|---|
| status | NEW, COMPLETED, CANCELLED, DENIED |
| decided_by | limit, screening, NULL |
| message_info | NULL; DAILY_LIMIT_EXCEEDED, MONTHLY_LIMIT_EXCEEDED, INVALID_AMOUNT, SENDER_BLOCKED; `<причина limit>; <причина screening>`; `UNEXPECTED_RESULT: limit=…, screening=…` |

### Таблица: transfer_history (база: transferdb)
**Описание:** История этапов обработки перевода (сущность `TransferHistoryEntry`, `001-create-tables.sql` в transfer-service).

| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
|---|---|---|---|---|---|---|---|---|
| id | BIGSERIAL | да | — | да (PK) | да | автоинкремент (BIGSERIAL) | PK (неявный) | Идентификатор записи (`@GeneratedValue(strategy = IDENTITY)`) |
| transfer_id | BIGINT | — | `transfers(id)`, ON DELETE / ON UPDATE не заданы | в составе `uq_transfer_history_stage` | да | — | `idx_transfer_history_transfer_id`; входит в уникальный индекс `uq_transfer_history_stage` | Идентификатор перевода |
| status | VARCHAR(20) | — | — | — | да | — | — | Статус на этапе |
| decided_by | VARCHAR(20) | — | — | — | нет | — | — | Источник решения на этапе |
| message_info | TEXT | — | — | — | нет | — | — | Причина или пояснение |
| changed_at | TIMESTAMP | — | — | — | да | `NOW()` | — | Дата-время записи |
| stage | VARCHAR(20) | — | — | в составе `uq_transfer_history_stage` | да | — | входит в уникальный индекс `uq_transfer_history_stage` | Этап обработки |

**Ограничения уровня таблицы:**

| Ограничение | Колонки |
|---|---|
| `uq_transfer_history_stage` (UNIQUE) | transfer_id, stage |

**Допустимые значения** (задаются кодом, CHECK-ограничений в БД нет):

| Колонка | Значения |
|---|---|
| stage | NEW, LIMIT, SCREENING, FINAL (enum `HistoryStage`, `@Enumerated(EnumType.STRING)`) |
| status | NEW (этап NEW); APPROVED, DECLINED (этапы LIMIT, SCREENING); COMPLETED, CANCELLED, DENIED (этап FINAL) |
| decided_by | NULL (этап NEW); limit (LIMIT); screening (SCREENING); limit, screening, NULL (FINAL) |
| message_info | NULL; DAILY_LIMIT_EXCEEDED, MONTHLY_LIMIT_EXCEEDED, INVALID_AMOUNT (LIMIT); SENDER_BLOCKED (SCREENING); для FINAL — как в `transfers.message_info` |

### Таблица: sender_usage (база: limitdb)
**Описание:** Счётчики использования дневного и месячного лимитов по отправителю (сущность `SenderUsage`, `001-create-tables.sql` в limit-service).

| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
|---|---|---|---|---|---|---|---|---|
| sender_id | BIGINT | да | — | да (PK) | да | — | PK (неявный) | Идентификатор отправителя |
| daily_used | DECIMAL(15,2) | — | — | — | да | `0` | — | Сумма, учтённая в дневном лимите за дату `last_daily_reset` |
| monthly_used | DECIMAL(15,2) | — | — | — | да | `0` | — | Сумма, учтённая в месячном лимите за месяц `last_monthly_reset` |
| last_daily_reset | DATE | — | — | — | да | `CURRENT_DATE` | — | Дата последнего сброса дневного счётчика |
| last_monthly_reset | DATE | — | — | — | да | `CURRENT_DATE` | — | Дата последнего сброса месячного счётчика |
| version | BIGINT | — | — | — | да | `0` | — | Версия строки для оптимистической блокировки (`@Version`) |

### Таблица: reservations (база: limitdb)
**Описание:** Решение limit-service по каждому переводу и состояние резерва суммы (сущность `Reservation`, `001-create-tables.sql` в limit-service).

| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
|---|---|---|---|---|---|---|---|---|
| transfer_id | BIGINT | да | — | да (PK) | да | — | PK (неявный) | Идентификатор перевода |
| sender_id | BIGINT | — | — | — | да | — | `idx_reservations_sender_id` | Идентификатор отправителя |
| amount | DECIMAL(15,2) | — | — | — | да | — | — | Сумма резерва |
| decision | VARCHAR(10) | — | — | — | да | — | — | Решение по лимитам |
| reason | VARCHAR(50) | — | — | — | нет | — | — | Код причины отказа |
| state | VARCHAR(20) | — | — | — | да | — | — | Состояние резерва |
| reserved_on | DATE | — | — | — | да | — | — | Дата резервирования |
| created_at | TIMESTAMP | — | — | — | да | — | — | Дата-время создания |
| updated_at | TIMESTAMP | — | — | — | да | — | — | Дата-время последнего изменения |

**Допустимые значения** (задаются кодом, CHECK-ограничений в БД нет):

| Колонка | Значения |
|---|---|
| decision | APPROVED, DECLINED |
| reason | NULL, DAILY_LIMIT_EXCEEDED, MONTHLY_LIMIT_EXCEEDED, INVALID_AMOUNT |
| state | RESERVED, COMMITTED, RELEASED, NONE |

### Таблица: blocked_senders (база: screeningdb)
**Описание:** Справочник заблокированных отправителей (сущность `BlockedSender`); changeset `002-insert-test-data.sql` добавляет записи для sender_id 5, 13, 21, 34, 42.

| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
|---|---|---|---|---|---|---|---|---|
| id | BIGSERIAL | да | — | да (PK) | да | автоинкремент (BIGSERIAL) | PK (неявный) | Суррогатный ключ (`@GeneratedValue(strategy = IDENTITY)`) |
| sender_id | BIGINT | — | — | да | да | — | `idx_blocked_senders_sender_id`; неявный индекс UNIQUE | Идентификатор заблокированного отправителя |
| reason | VARCHAR(255) | — | — | — | да | — | — | Причина блокировки (свободный текст) |
| blocked_at | TIMESTAMP | — | — | — | да | `NOW()` | — | Дата-время блокировки |

### Таблица: screening_checks (база: screeningdb)
**Описание:** Результаты проверки переводов в screening-service (сущность `ScreeningCheck`, `001-create-tables.sql` в screening-service).

| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
|---|---|---|---|---|---|---|---|---|
| transfer_id | BIGINT | да | — | да (PK) | да | — | PK (неявный) | Идентификатор перевода |
| sender_id | BIGINT | — | — | — | да | — | `idx_screening_checks_sender_id` | Идентификатор отправителя |
| result | VARCHAR(10) | — | — | — | да | — | — | Результат проверки |
| reason | VARCHAR(50) | — | — | — | нет | — | — | Код причины отказа |
| checked_at | TIMESTAMP | — | — | — | да | — | — | Дата-время проверки |

**Допустимые значения** (задаются кодом, CHECK-ограничений в БД нет):

| Колонка | Значения |
|---|---|
| result | APPROVED, DECLINED |
| reason | NULL, SENDER_BLOCKED |

## 3. КОНТРАКТЫ СООБЩЕНИЙ

### Канал: transfers

| Свойство | Значение |
|---|---|
| Тип канала | Kafka-топик |
| Число партиций | 3 |
| Фактор репликации | в коде не найдено (значение брокера по умолчанию) |
| Политика хранения / очистки | `cleanup.policy=compact` (`TopicBuilder.compact()`); срок хранения — в коде не найдено |
| Объявление топика | бин `transfersTopic` в `TopicsConfig.java` всех трёх сервисов; `spring.kafka.admin.modify-topic-configs: true`; автосоздание топиков на брокере выключено (`KAFKA_AUTO_CREATE_TOPICS_ENABLE: "false"`, `docker-compose.yml`) |
| Ключ сообщения | `Transfer.id` (`Long`) — идентификатор перевода |
| Сериализация ключа | Отправка: `LongSerializer` (`application.yml`), в Kafka Streams — `Serdes.Long()`. Приём: `ErrorHandlingDeserializer` → `LongDeserializer` |
| Сериализация значения | Отправка: JSON через `JsonSerializer` (`application.yml`), в Kafka Streams — `JsonSerde` (`default.value.serde`); тип передаётся заголовком `__TypeId__` = `com.example.common.Transfer`. Приём: `ErrorHandlingDeserializer` → `JsonDeserializer`; тип по умолчанию не задан и берётся из `__TypeId__`, trusted packages `*` |
| Формат сообщения | `Transfer` — см. «Формат сообщения Transfer» |
| Отправители | transfer-service: `TransferController.java` → `publishNew()` (событие NEW); transfer-service: `DecisionTopologyConfig.java` → `decisionStream()` (Kafka Streams, итоговый статус) |
| Получатели (группа потребителей) | limit-service: `TransferEventsListener.java` → `onEvent()`, listener id `limit-transfers-listener`, группа `limit`; screening-service: `TransferEventsListener.java` → `onEvent()`, id `screening-transfers-listener`, группа `screening`; transfer-service: `TransferEventListener.java` → `onEvent()`, id `transfer-tracker`, группа `transfer-tracker` |
| Ошибки обработки у получателей | `DefaultErrorHandler` (`KafkaErrorHandlingConfig.java` → `kafkaErrorHandler()` в каждом сервисе): 3 повтора с интервалом 1 с, затем запись в `transfers.DLT` |

### Канал: limit-results

| Свойство | Значение |
|---|---|
| Тип канала | Kafka-топик |
| Число партиций | 3 |
| Фактор репликации | в коде не найдено (значение брокера по умолчанию) |
| Политика хранения / очистки | `cleanup.policy=compact`; срок хранения — в коде не найдено |
| Объявление топика | бин `limitResultsTopic` в `TopicsConfig.java` всех трёх сервисов |
| Ключ сообщения | `Transfer.id` (`Long`) |
| Сериализация ключа | Отправка: `LongSerializer`. Приём: listener — `ErrorHandlingDeserializer` → `LongDeserializer`; Kafka Streams — `Serdes.Long()` |
| Сериализация значения | Отправка: `JsonSerializer` с заголовком `__TypeId__`. Приём: listener — `ErrorHandlingDeserializer` → `JsonDeserializer`; Kafka Streams — `new JsonSerde<>(Transfer.class)` |
| Формат сообщения | `Transfer` — см. «Формат сообщения Transfer» |
| Отправители | limit-service: `LimitService.java` → `sendAndWait()` (`KafkaTemplate<Long, Transfer>`) |
| Получатели (группа потребителей) | transfer-service: `DecisionTopologyConfig.java` → `decisionStream()` — Kafka Streams, join с `screening-results`; группа = `application.id`, явно не задан, Spring Boot подставляет `spring.application.name` = `transfer-service`. transfer-service: `TransferEventListener.java` → `onEvent()`, id `transfer-tracker`, группа `transfer-tracker` |
| Ошибки обработки у получателей | listener `transfer-tracker`: `DefaultErrorHandler` → `limit-results.DLT`; Kafka Streams: ошибки десериализации обрабатывает `LogAndContinueExceptionHandler` (`application.yml`) |

### Канал: screening-results

| Свойство | Значение |
|---|---|
| Тип канала | Kafka-топик |
| Число партиций | 3 |
| Фактор репликации | в коде не найдено (значение брокера по умолчанию) |
| Политика хранения / очистки | `cleanup.policy=compact`; срок хранения — в коде не найдено |
| Объявление топика | бин `screeningResultsTopic` в `TopicsConfig.java` всех трёх сервисов |
| Ключ сообщения | `Transfer.id` (`Long`) |
| Сериализация ключа | Отправка: `LongSerializer`. Приём: listener — `ErrorHandlingDeserializer` → `LongDeserializer`; Kafka Streams — `Serdes.Long()` |
| Сериализация значения | Отправка: `JsonSerializer` с заголовком `__TypeId__`. Приём: listener — `ErrorHandlingDeserializer` → `JsonDeserializer`; Kafka Streams — `new JsonSerde<>(Transfer.class)` |
| Формат сообщения | `Transfer` — см. «Формат сообщения Transfer» |
| Отправители | screening-service: `ScreeningService.java` → `sendAndWait()` (`KafkaTemplate<Long, Transfer>`) |
| Получатели (группа потребителей) | transfer-service: `DecisionTopologyConfig.java` → `decisionStream()` — Kafka Streams, join с `limit-results`, группа `transfer-service` (как для limit-results). transfer-service: `TransferEventListener.java` → `onEvent()`, id `transfer-tracker`, группа `transfer-tracker` |
| Ошибки обработки у получателей | listener `transfer-tracker`: `DefaultErrorHandler` → `screening-results.DLT`; Kafka Streams: `LogAndContinueExceptionHandler` |

### Канал: transfers.DLT

| Свойство | Значение |
|---|---|
| Тип канала | Kafka-топик (dead letter topic) |
| Число партиций | 3 |
| Фактор репликации | в коде не найдено (значение брокера по умолчанию) |
| Политика хранения / очистки | в коде не найдено (настройки брокера) |
| Объявление топика | бин `transfersDlt` в `KafkaErrorHandlingConfig.java` всех трёх сервисов |
| Партиция записи | Совпадает с партицией исходной записи (resolver в `KafkaErrorHandlingConfig.java` → `kafkaErrorHandler()`) |
| Ключ сообщения | Ключ исходной записи: `Long`, либо исходные байты (`byte[]`) при ошибке десериализации ключа |
| Сериализация ключа | `DelegatingByTypeSerializer`: `Long` → `LongSerializer`, `byte[]` → `ByteArraySerializer` |
| Сериализация значения | Объект → `JsonSerializer`; `byte[]` (исходное значение при ошибке десериализации) → `ByteArraySerializer` |
| Формат сообщения | `Transfer` (см. «Формат сообщения Transfer») либо исходные байты; плюс стандартные заголовки `kafka_dlt-*` от `DeadLetterPublishingRecoverer` |
| Отправители | `DeadLetterPublishingRecoverer` (`KafkaErrorHandlingConfig.java` → `kafkaErrorHandler()`) в limit-service (группа `limit`), screening-service (группа `screening`) и transfer-service (группа `transfer-tracker`) |
| Получатели (группа потребителей) | в коде не найдено |

### Канал: limit-results.DLT

| Свойство | Значение |
|---|---|
| Тип канала | Kafka-топик (dead letter topic) |
| Число партиций | 3 |
| Фактор репликации | в коде не найдено (значение брокера по умолчанию) |
| Политика хранения / очистки | в коде не найдено (настройки брокера) |
| Объявление топика | бин `limitResultsDlt` в `KafkaErrorHandlingConfig.java`, только в transfer-service |
| Партиция, ключ, сериализация, формат сообщения | как у `transfers.DLT` |
| Отправители | `DeadLetterPublishingRecoverer` в transfer-service (группа `transfer-tracker`) |
| Получатели (группа потребителей) | в коде не найдено |

### Канал: screening-results.DLT

| Свойство | Значение |
|---|---|
| Тип канала | Kafka-топик (dead letter topic) |
| Число партиций | 3 |
| Фактор репликации | в коде не найдено (значение брокера по умолчанию) |
| Политика хранения / очистки | в коде не найдено (настройки брокера) |
| Объявление топика | бин `screeningResultsDlt` в `KafkaErrorHandlingConfig.java`, только в transfer-service |
| Партиция, ключ, сериализация, формат сообщения | как у `transfers.DLT` |
| Отправители | `DeadLetterPublishingRecoverer` в transfer-service (группа `transfer-tracker`) |
| Получатели (группа потребителей) | в коде не найдено |

### Канал: служебные changelog-топики Kafka Streams (transfer-service)

| Свойство | Значение |
|---|---|
| Тип канала | Внутренние топики Kafka Streams: журналы двух оконных хранилищ join в `DecisionTopologyConfig.java` → `decisionStream()` |
| Имена | В коде не заданы, генерирует Kafka Streams: `transfer-service-KSTREAM-JOINTHIS-<n>-store-changelog` и `transfer-service-KSTREAM-JOINOTHER-<n>-store-changelog` — требует уточнения на развёрнутом кластере |
| Число партиций, политика хранения / очистки | определяются Kafka Streams; в коде не найдено |
| Сериализация | `StreamJoined.with(Serdes.Long(), JsonSerde<Transfer>, JsonSerde<Transfer>)` |
| Отправители и получатели | только Kafka Streams transfer-service (`application.id` = `transfer-service`) |

### Формат сообщения Transfer (`transfer-common`, `Transfer.java`, record `com.example.common.Transfer`) — общий для всех каналов

| Поле | Тип | Nullable | Описание | Пример |
|---|---|---|---|---|
| id | Long | нет | Идентификатор перевода, совпадает с ключом сообщения | `1001` |
| senderId | Long | нет | Идентификатор отправителя | `7` |
| recipientId | Long | нет | Идентификатор получателя | `15` |
| amount | BigDecimal | нет | Сумма перевода | `1500.00` |
| currency | String | нет | Код валюты | `"RUB"` |
| status | String | нет | Статус (значения по каналам — в таблице ниже) | `"NEW"` |
| decidedBy | String | да | Источник решения | `"limit"` |
| messageInfo | String | да | Код причины или пояснение | `"DAILY_LIMIT_EXCEEDED"` |
| createdAt | LocalDateTime | нет | Дата-время создания перевода. ObjectMapper spring-kafka по умолчанию (`JsonSerializer` / `JsonSerde`) пишет его массивом чисел: год, месяц, день, час, минута, секунда, наносекунды | `[2026,10,2,12,30,15,123456000]` |

### Значения полей status / decidedBy / messageInfo по каналам

| Канал (отправитель) | status | decidedBy | messageInfo |
|---|---|---|---|
| transfers (`TransferController.java` → `publishNew()`) | NEW | null | null |
| transfers (`DecisionTopologyConfig.java`, `TransferDecisionService.java` → `decide()`) | COMPLETED, CANCELLED, DENIED | limit, screening, null | null; код причины из limit-results или screening-results; `<причина limit>; <причина screening>`; `UNEXPECTED_RESULT: limit=…, screening=…` |
| limit-results (`LimitService.java`) | APPROVED, DECLINED | limit | null, DAILY_LIMIT_EXCEEDED, MONTHLY_LIMIT_EXCEEDED, INVALID_AMOUNT |
| screening-results (`ScreeningService.java`) | APPROVED, DECLINED | screening | null, SENDER_BLOCKED |
| transfers.DLT, limit-results.DLT, screening-results.DLT | как в исходном топике | как в исходном топике | как в исходном топике |

## 4. РАСХОЖДЕНИЯ КОНТРАКТА API С КОДОМ (`transfer-service/src/main/resources/openapi/transfer-api.yaml`)

| Элемент | В контракте | В коде |
|---|---|---|
| Код 500 (все операции) | Не описан | `ApiExceptionHandler.java` → `handleUnexpected()` возвращает 500 с телом ProblemDetail (detail «Внутренняя ошибка сервера, повторите запрос позже») для любого необработанного исключения |
| GET /transfers/{id}: код 400 | Не описан (только 200 и 404) | Нечисловой `id` вызывает `MethodArgumentTypeMismatchException`, ответ 400 с телом ProblemDetail (`ApiExceptionHandler` наследует `ResponseEntityExceptionHandler`) |
| Прочие стандартные коды Spring MVC | Не описаны | `ResponseEntityExceptionHandler` возвращает их с телом ProblemDetail, например 415 для POST /transfers с Content-Type не JSON |
| `TransferRequest.amount`: дробная часть | `multipleOf: 0.01` — допустимо любое значение, кратное 0,01, в том числе с незначащими нулями (`10.100`) | `@Digits(integer = 13, fraction = 2)` в `TransferRequest.java` считает знаки дробной части `BigDecimal` вместе с незначащими нулями: на `10.100` ответ 400 |
| `TransferRequest.currency`: значение `null` | `nullable` не указан, явный `null` недопустим | `@Pattern` пропускает `null`, `TransferRequest.java` → `currencyOrDefault()` подставляет `RUB`, запрос принимается |
| `Transfer.updatedAt`: nullable | Не помечен `nullable` | Колонка `transfers.updated_at` объявлена без NOT NULL (`001-create-tables.sql`, transfer-service); при создании через `TransferTrackingService.java` → `createTransfer()` поле заполняется |