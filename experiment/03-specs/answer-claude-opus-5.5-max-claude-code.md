# Спецификации структур данных: transfer-limit-system

## 1. HTTP API

### Сервис: transfer-service (`TransferController`, базовый путь `/transfers`)

### 1.1 Эндпоинт: POST /transfers

**Описание:** создаёт перевод или возвращает уже существующий перевод с тем же `id` и теми же параметрами (`TransferController.create`).

**Параметры запроса:**

| Параметр | Источник (body/path/query) | Тип | Обязательный | Описание |
|---|---|---|---|---|
| (тело запроса) | body | object (`application/json`) | да | Объект `TransferRequest` (`com.example.transfer.controller.TransferRequest`), проверяется через `@Valid` |
| id | body | integer (int64) | да | Идентификатор перевода, задаётся клиентом; `@NotNull` |
| senderId | body | integer (int64) | да | Идентификатор отправителя; `@NotNull` |
| recipientId | body | integer (int64) | да | Идентификатор получателя; `@NotNull` |
| amount | body | number | да | Сумма перевода; `@NotNull`, `@Positive` (> 0), `@Digits(integer = 13, fraction = 2)` |
| currency | body | string | нет | Код валюты; `@Pattern(regexp = "[A-Z]{3}")`; если не передан (null), используется `"RUB"` (`TransferRequest.DEFAULT_CURRENCY`, метод `currencyOrDefault`) |

**Параметры ответа (201, 200 — объект `TransferEntity`, `application/json`):**

| Поле | Тип | Nullable | Описание |
|---|---|---|---|
| id | integer (int64) | нет | Идентификатор перевода |
| senderId | integer (int64) | нет | Идентификатор отправителя |
| recipientId | integer (int64) | нет | Идентификатор получателя |
| amount | number | нет | Сумма перевода |
| currency | string | нет | Код валюты |
| status | string | нет | Статус перевода: NEW, COMPLETED, CANCELLED, DENIED |
| decidedBy | string | да | Сервис, определивший итоговый статус: limit, screening |
| messageInfo | string | да | Пояснение к итоговому статусу |
| createdAt | string | нет | Дата-время создания (`LocalDateTime`, ISO-8601 без часового пояса, точность до микросекунд) |
| updatedAt | string | нет (колонка `transfers.updated_at` допускает NULL, но `TransferTrackingService` заполняет поле при каждом сохранении) | Дата-время последнего изменения (`LocalDateTime`, ISO-8601 без часового пояса) |

**Параметры ответа (400, 409, 500, 503 — `org.springframework.http.ProblemDetail`, `application/problem+json`):**

| Поле | Тип | Nullable | Описание |
|---|---|---|---|
| type | string | нет | URI типа проблемы; по умолчанию `"about:blank"` |
| title | string | нет | Наименование HTTP-статуса (например, `"Conflict"`) |
| status | integer | нет | HTTP-код ответа |
| detail | string | нет | Текст ошибки: 409 — «Перевод с id={id} уже существует с другими параметрами» (`TransferController.duplicate`); 503 — «Kafka недоступна, перевод сохранён — повторите запрос с тем же id» (`TransferController.KAFKA_UNAVAILABLE`); 500 — «Внутренняя ошибка сервера, повторите запрос позже» (`ApiExceptionHandler.INTERNAL_ERROR`); 400 — стандартный текст `ResponseEntityExceptionHandler` (Spring MVC) |
| instance | string | нет | URI запроса (`/transfers`), подставляется Spring MVC |

**Коды ответа:**

| Код | Условие |
|---|---|
| 201 | Перевода с указанным `id` не было, перевод создан |
| 200 | Перевод с указанным `id` уже существует с теми же `senderId`, `recipientId`, `amount`, `currency`; возвращается существующий перевод |
| 400 | Тело запроса не прошло валидацию (`MethodArgumentNotValidException`) или не читается (`HttpMessageNotReadableException`); обработка унаследована `ApiExceptionHandler` от `ResponseEntityExceptionHandler` |
| 409 | Перевод с указанным `id` уже существует с другими параметрами (`TransferController.duplicate`) |
| 503 | Ошибка или таймаут (10 с) отправки сообщения в топик `transfers` (`TransferController.publishNew`, `ResponseStatusException`) |
| 500 | Любое другое необработанное исключение (`ApiExceptionHandler.handleUnexpected`) |

### 1.2 Эндпоинт: GET /transfers

**Описание:** возвращает список всех переводов, упорядоченный по `createdAt` по убыванию (`TransferController.all`).

**Параметры запроса:**

| Параметр | Источник (body/path/query) | Тип | Обязательный | Описание |
|---|---|---|---|---|
| — | — | — | — | Параметры не предусмотрены (в коде не найдено) |

**Параметры ответа (200 — массив объектов `TransferEntity`, `application/json`):**

| Поле | Тип | Nullable | Описание |
|---|---|---|---|
| [] | array | нет | Список переводов (`List<TransferEntity>`), может быть пустым |
| [].id | integer (int64) | нет | Идентификатор перевода |
| [].senderId | integer (int64) | нет | Идентификатор отправителя |
| [].recipientId | integer (int64) | нет | Идентификатор получателя |
| [].amount | number | нет | Сумма перевода |
| [].currency | string | нет | Код валюты |
| [].status | string | нет | Статус перевода: NEW, COMPLETED, CANCELLED, DENIED |
| [].decidedBy | string | да | Сервис, определивший итоговый статус: limit, screening |
| [].messageInfo | string | да | Пояснение к итоговому статусу |
| [].createdAt | string | нет | Дата-время создания (`LocalDateTime`, ISO-8601 без часового пояса) |
| [].updatedAt | string | нет (колонка БД допускает NULL, но `TransferTrackingService` заполняет поле при каждом сохранении) | Дата-время последнего изменения (`LocalDateTime`, ISO-8601 без часового пояса) |

**Параметры ответа (500 — `ProblemDetail`, `application/problem+json`):**

| Поле | Тип | Nullable | Описание |
|---|---|---|---|
| type | string | нет | URI типа проблемы; по умолчанию `"about:blank"` |
| title | string | нет | Наименование HTTP-статуса (`"Internal Server Error"`) |
| status | integer | нет | HTTP-код ответа (500) |
| detail | string | нет | «Внутренняя ошибка сервера, повторите запрос позже» (`ApiExceptionHandler.INTERNAL_ERROR`) |
| instance | string | нет | URI запроса (`/transfers`), подставляется Spring MVC |

**Коды ответа:**

| Код | Условие |
|---|---|
| 200 | Запрос выполнен успешно |
| 500 | Необработанное исключение (`ApiExceptionHandler.handleUnexpected`) |

### 1.3 Эндпоинт: GET /transfers/{id}

**Описание:** возвращает перевод по идентификатору вместе с историей прохождения этапов (`TransferController.getById`).

**Параметры запроса:**

| Параметр | Источник (body/path/query) | Тип | Обязательный | Описание |
|---|---|---|---|---|
| id | path | integer (int64) | да (`@PathVariable`, обязателен по умолчанию) | Идентификатор перевода; аннотаций валидации нет, значение должно преобразовываться в `Long` |

**Параметры ответа (200 — объект `Map<String, Object>`, `application/json`):**

| Поле | Тип | Nullable | Описание |
|---|---|---|---|
| transfer | object | нет | Перевод (`TransferEntity`) |
| transfer.id | integer (int64) | нет | Идентификатор перевода |
| transfer.senderId | integer (int64) | нет | Идентификатор отправителя |
| transfer.recipientId | integer (int64) | нет | Идентификатор получателя |
| transfer.amount | number | нет | Сумма перевода |
| transfer.currency | string | нет | Код валюты |
| transfer.status | string | нет | Статус перевода: NEW, COMPLETED, CANCELLED, DENIED |
| transfer.decidedBy | string | да | Сервис, определивший итоговый статус: limit, screening |
| transfer.messageInfo | string | да | Пояснение к итоговому статусу |
| transfer.createdAt | string | нет | Дата-время создания (`LocalDateTime`, ISO-8601 без часового пояса) |
| transfer.updatedAt | string | нет (колонка БД допускает NULL, но `TransferTrackingService` заполняет поле при каждом сохранении) | Дата-время последнего изменения (`LocalDateTime`, ISO-8601 без часового пояса) |
| history | array | нет | История перевода (`List<TransferHistoryEntry>`), упорядочена по `stage` (NEW, LIMIT, SCREENING, FINAL), затем по `changedAt`; может быть пустой |
| history[].id | integer (int64) | нет | Идентификатор записи истории |
| history[].transferId | integer (int64) | нет | Идентификатор перевода |
| history[].stage | string | нет | Этап: NEW, LIMIT, SCREENING, FINAL (enum `HistoryStage`) |
| history[].status | string | нет | Статус на этапе: NEW, APPROVED, DECLINED, COMPLETED, CANCELLED, DENIED |
| history[].decidedBy | string | да | Сервис, принявший решение: limit, screening |
| history[].messageInfo | string | да | Пояснение к решению |
| history[].changedAt | string | нет | Дата-время записи (`LocalDateTime`, ISO-8601 без часового пояса) |

**Параметры ответа (404):**

| Поле | Тип | Nullable | Описание |
|---|---|---|---|
| — | — | — | Тело ответа отсутствует (`ResponseEntity.notFound().build()`) |

**Параметры ответа (400, 500 — `ProblemDetail`, `application/problem+json`):**

| Поле | Тип | Nullable | Описание |
|---|---|---|---|
| type | string | нет | URI типа проблемы; по умолчанию `"about:blank"` |
| title | string | нет | Наименование HTTP-статуса (`"Bad Request"`, `"Internal Server Error"`) |
| status | integer | нет | HTTP-код ответа |
| detail | string | нет | 400 — стандартный текст `ResponseEntityExceptionHandler` (Spring MVC); 500 — «Внутренняя ошибка сервера, повторите запрос позже» (`ApiExceptionHandler.INTERNAL_ERROR`) |
| instance | string | нет | URI запроса (`/transfers/{id}`), подставляется Spring MVC |

**Коды ответа:**

| Код | Условие |
|---|---|
| 200 | Перевод найден |
| 404 | Перевод с указанным `id` не найден |
| 400 | Значение `id` не преобразуется в `Long` (`MethodArgumentTypeMismatchException`, обработка унаследована от `ResponseEntityExceptionHandler`) |
| 500 | Необработанное исключение (`ApiExceptionHandler.handleUnexpected`) |

## 2. СХЕМА БД

### База transferdb (PostgreSQL 16, сервис transfer-service, Liquibase changeset `developer:001-create-tables`)

### 2.1 Таблица: transfers (база: transferdb)

**Описание:** переводы (JPA-сущность `TransferEntity`).

| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
|---|---|---|---|---|---|---|---|---|
| id | BIGINT | да | нет | да (PK) | да | — | индекс PK | Идентификатор перевода (задаётся клиентом, не генерируется) |
| sender_id | BIGINT | нет | нет | нет | да | — | idx_transfers_sender_id | Идентификатор отправителя |
| recipient_id | BIGINT | нет | нет | нет | да | — | — | Идентификатор получателя |
| amount | DECIMAL(15,2) | нет | нет | нет | да | — | — | Сумма перевода |
| currency | VARCHAR(10) | нет | нет | нет | да | 'RUB' | — | Код валюты |
| status | VARCHAR(20) | нет | нет | нет | да | — | idx_transfers_status | Статус перевода |
| decided_by | VARCHAR(20) | нет | нет | нет | нет | — | — | Сервис, определивший итоговый статус |
| message_info | TEXT | нет | нет | нет | нет | — | — | Пояснение к итоговому статусу |
| created_at | TIMESTAMP | нет | нет | нет | да | — | — | Дата-время создания |
| updated_at | TIMESTAMP | нет | нет | нет | нет | — | — | Дата-время последнего изменения |

**Допустимые значения:**

| Колонка | Значения |
|---|---|
| status | NEW, COMPLETED, CANCELLED, DENIED (CHECK-ограничения в БД нет; значения задаются в `TransferTrackingService.createTransfer` и `TransferDecisionService.decide`) |
| decided_by | limit, screening, NULL |

### 2.2 Таблица: transfer_history (база: transferdb)

**Описание:** история прохождения переводом этапов обработки (JPA-сущность `TransferHistoryEntry`).

| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
|---|---|---|---|---|---|---|---|---|
| id | BIGSERIAL | да | нет | да (PK) | да | автоинкремент (последовательность BIGSERIAL) | индекс PK | Идентификатор записи (`GenerationType.IDENTITY`) |
| transfer_id | BIGINT | нет | transfers(id) | нет (входит в составной UNIQUE) | да | — | idx_transfer_history_transfer_id; uq_transfer_history_stage (1-я колонка) | Идентификатор перевода |
| status | VARCHAR(20) | нет | нет | нет | да | — | — | Статус на этапе |
| decided_by | VARCHAR(20) | нет | нет | нет | нет | — | — | Сервис, принявший решение |
| message_info | TEXT | нет | нет | нет | нет | — | — | Пояснение к решению |
| changed_at | TIMESTAMP | нет | нет | нет | да | NOW() | — | Дата-время записи |
| stage | VARCHAR(20) | нет | нет | нет (входит в составной UNIQUE) | да | — | uq_transfer_history_stage (2-я колонка) | Этап обработки (enum `HistoryStage`, `@Enumerated(EnumType.STRING)`) |

**Ограничения уровня таблицы:**

| Ограничение | Колонки |
|---|---|
| uq_transfer_history_stage (UNIQUE) | transfer_id, stage |

**Допустимые значения:**

| Колонка | Значения |
|---|---|
| stage | NEW, LIMIT, SCREENING, FINAL (enum `HistoryStage`) |
| status | NEW (stage = NEW); APPROVED, DECLINED (stage = LIMIT, SCREENING); COMPLETED, CANCELLED, DENIED (stage = FINAL); CHECK-ограничения в БД нет |
| decided_by | limit, screening, NULL |

### База limitdb (PostgreSQL 16, сервис limit-service, Liquibase changeset `developer:001-create-tables`)

### 2.3 Таблица: sender_usage (база: limitdb)

**Описание:** накопленное использование дневного и месячного лимитов по отправителю (JPA-сущность `SenderUsage`).

| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
|---|---|---|---|---|---|---|---|---|
| sender_id | BIGINT | да | нет | да (PK) | да | — | индекс PK | Идентификатор отправителя |
| daily_used | DECIMAL(15,2) | нет | нет | нет | да | 0 | — | Сумма, учтённая в дневном лимите |
| monthly_used | DECIMAL(15,2) | нет | нет | нет | да | 0 | — | Сумма, учтённая в месячном лимите |
| last_daily_reset | DATE | нет | нет | нет | да | CURRENT_DATE | — | Дата последнего обнуления дневной суммы |
| last_monthly_reset | DATE | нет | нет | нет | да | CURRENT_DATE | — | Дата последнего обнуления месячной суммы |
| version | BIGINT | нет | нет | нет | да | 0 | — | Версия строки для оптимистической блокировки (`@Version`) |

### 2.4 Таблица: reservations (база: limitdb)

**Описание:** решения лимит-сервиса и резервы сумм по переводам (JPA-сущность `Reservation`).

| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
|---|---|---|---|---|---|---|---|---|
| transfer_id | BIGINT | да | нет | да (PK) | да | — | индекс PK | Идентификатор перевода |
| sender_id | BIGINT | нет | нет | нет | да | — | idx_reservations_sender_id | Идентификатор отправителя |
| amount | DECIMAL(15,2) | нет | нет | нет | да | — | — | Зарезервированная сумма |
| decision | VARCHAR(10) | нет | нет | нет | да | — | — | Решение лимит-сервиса |
| reason | VARCHAR(50) | нет | нет | нет | нет | — | — | Причина отказа |
| state | VARCHAR(20) | нет | нет | нет | да | — | — | Состояние резерва |
| reserved_on | DATE | нет | нет | нет | да | — | — | Дата резервирования |
| created_at | TIMESTAMP | нет | нет | нет | да | — | — | Дата-время создания |
| updated_at | TIMESTAMP | нет | нет | нет | да | — | — | Дата-время последнего изменения |

**Допустимые значения:**

| Колонка | Значения |
|---|---|
| decision | APPROVED, DECLINED (`Reservation.APPROVED`, `Reservation.DECLINED`) |
| reason | INVALID_AMOUNT, DAILY_LIMIT_EXCEEDED, MONTHLY_LIMIT_EXCEEDED, NULL (`LimitLedger.decide`, `Reservation.approve`) |
| state | RESERVED, COMMITTED, RELEASED, NONE (`Reservation.STATE_RESERVED`, `STATE_COMMITTED`, `STATE_RELEASED`, `STATE_NONE`) |

### База screeningdb (PostgreSQL 16, сервис screening-service, Liquibase changesets `developer:001-create-tables`, `developer:002-insert-test-data`)

### 2.5 Таблица: blocked_senders (база: screeningdb)

**Описание:** справочник заблокированных отправителей (JPA-сущность `BlockedSender`; начальные данные changeset `002-insert-test-data`: sender_id 5, 13, 21, 34, 42).

| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
|---|---|---|---|---|---|---|---|---|
| id | BIGSERIAL | да | нет | да (PK) | да | автоинкремент (последовательность BIGSERIAL) | индекс PK | Идентификатор записи (`GenerationType.IDENTITY`) |
| sender_id | BIGINT | нет | нет | да | да | — | idx_blocked_senders_sender_id; уникальный индекс ограничения UNIQUE | Идентификатор заблокированного отправителя |
| reason | VARCHAR(255) | нет | нет | нет | да | — | — | Причина блокировки (свободный текст, например 'Fraud detected') |
| blocked_at | TIMESTAMP | нет | нет | нет | да | NOW() | — | Дата-время блокировки |

### 2.6 Таблица: screening_checks (база: screeningdb)

**Описание:** результаты проверки (скрининга) переводов (JPA-сущность `ScreeningCheck`).

| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
|---|---|---|---|---|---|---|---|---|
| transfer_id | BIGINT | да | нет | да (PK) | да | — | индекс PK | Идентификатор перевода |
| sender_id | BIGINT | нет | нет | нет | да | — | idx_screening_checks_sender_id | Идентификатор отправителя |
| result | VARCHAR(10) | нет | нет | нет | да | — | — | Результат проверки |
| reason | VARCHAR(50) | нет | нет | нет | нет | — | — | Причина отказа |
| checked_at | TIMESTAMP | нет | нет | нет | да | — | — | Дата-время проверки |

**Допустимые значения:**

| Колонка | Значения |
|---|---|
| result | APPROVED, DECLINED (`ScreeningCheck.APPROVED`, `ScreeningCheck.DECLINED`) |
| reason | SENDER_BLOCKED, NULL (`ScreeningJournal.check`) |

## 3. КОНТРАКТЫ СООБЩЕНИЙ

### 3.1 Формат сообщения Transfer (`com.example.common.Transfer`, модуль transfer-common) — общий для всех каналов раздела 3

| Поле | Тип | Nullable | Описание | Пример |
|---|---|---|---|---|
| id | Long | нет | Идентификатор перевода; совпадает с ключом сообщения | 1001 |
| senderId | Long | нет | Идентификатор отправителя | 7 |
| recipientId | Long | нет | Идентификатор получателя | 12 |
| amount | BigDecimal | нет | Сумма перевода (JSON number) | 1500.00 |
| currency | String | нет | Код валюты | "RUB" |
| status | String | нет | Статус; набор значений зависит от канала (см. таблицу ниже) | "NEW" |
| decidedBy | String | да | Сервис, принявший решение | "limit" |
| messageInfo | String | да | Пояснение к решению / код причины | "DAILY_LIMIT_EXCEEDED" |
| createdAt | LocalDateTime | нет | Дата-время создания перевода (из `transfers.created_at`); `ObjectMapper` Spring Kafka по умолчанию (`JacksonUtils.enhancedObjectMapper`, JavaTimeModule) сериализует его как массив чисел [год, месяц, день, час, минута, секунда, наносекунды] | [2026,11,12,10,15,30,123456000] |

**Значения полей по каналам:**

| Канал / отправитель | status | decidedBy | messageInfo |
|---|---|---|---|
| transfers — `TransferController.publishNew` | NEW | null | null |
| transfers — Kafka Streams `DecisionTopologyConfig.decisionStream` | COMPLETED, CANCELLED, DENIED | null, "limit", "screening" | null или строка-пояснение, формируемая `TransferDecisionService.decide` |
| limit-results — `LimitService.sendAndWait` | APPROVED, DECLINED | "limit" | null, "INVALID_AMOUNT", "DAILY_LIMIT_EXCEEDED", "MONTHLY_LIMIT_EXCEEDED" |
| screening-results — `ScreeningService.sendAndWait` | APPROVED, DECLINED | "screening" | null, "SENDER_BLOCKED" |
| transfers.DLT, limit-results.DLT, screening-results.DLT | как в исходном топике | как в исходном топике | как в исходном топике |

### 3.2 Канал: transfers

| Свойство | Значение |
|---|---|
| Тип канала | Kafka-топик (брокер confluentinc/cp-kafka:7.6.0, KRaft; автосоздание топиков отключено — `KAFKA_AUTO_CREATE_TOPICS_ENABLE: "false"`, топик создаётся бином `NewTopic`) |
| Объявление | `TopicsConfig.transfersTopic` в transfer-service, limit-service, screening-service |
| Число партиций | 3 |
| Фактор репликации | в коде не задан (`TopicBuilder` без `replicas`) |
| Политика хранения / очистки | `cleanup.policy=compact` (`TopicBuilder.compact()`); параметры retention в коде не заданы |
| Ключ сообщения | `Transfer.id` (Long) — идентификатор перевода |
| Сериализация ключа | `LongSerializer` (`spring.kafka.producer.key-serializer`); в Kafka Streams — `Serdes$LongSerde` (`default.key.serde`); у потребителей — `ErrorHandlingDeserializer` → `LongDeserializer` |
| Сериализация значения | JSON: `JsonSerializer` (`spring.kafka.producer.value-serializer`); в Kafka Streams — `JsonSerde` (`default.value.serde`); у потребителей — `ErrorHandlingDeserializer` → `JsonDeserializer` (`spring.json.trusted.packages: "*"`) |
| Заголовки | `__TypeId__` = `com.example.common.Transfer` (добавляется `JsonSerializer` по умолчанию; тип по умолчанию для `JsonDeserializer` в конфигурации не задан) |
| Отправители | 1) transfer-service: `TransferController.publishNew` (`KafkaTemplate<Long, Transfer>`) — status NEW; 2) transfer-service: Kafka Streams `DecisionTopologyConfig.decisionStream` (`.to("transfers")`) — итоговый статус |
| Получатели (группа потребителей) | 1) limit-service: `TransferEventsListener.onEvent` (listener id `limit-transfers-listener`), группа `limit`; 2) screening-service: `TransferEventsListener.onEvent` (listener id `screening-transfers-listener`), группа `screening`; 3) transfer-service: `TransferEventListener.onEvent` (listener id `transfer-tracker`), группа `transfer-tracker` |
| Начальная позиция чтения | `auto-offset-reset: earliest` (application.yml всех сервисов) |
| Канал ошибок | transfers.DLT (`DefaultErrorHandler` + `DeadLetterPublishingRecoverer`, `KafkaErrorHandlingConfig.kafkaErrorHandler` каждого сервиса) |
| Формат сообщения | Transfer (см. 3.1) |

### 3.3 Канал: limit-results

| Свойство | Значение |
|---|---|
| Тип канала | Kafka-топик (создаётся бином `NewTopic`) |
| Объявление | `TopicsConfig.limitResultsTopic` в transfer-service, limit-service, screening-service |
| Число партиций | 3 |
| Фактор репликации | в коде не задан (`TopicBuilder` без `replicas`) |
| Политика хранения / очистки | `cleanup.policy=compact` (`TopicBuilder.compact()`); параметры retention в коде не заданы |
| Ключ сообщения | `Transfer.id` (Long) — идентификатор перевода |
| Сериализация ключа | `LongSerializer` (limit-service); у получателей — `Serdes.Long()` (Kafka Streams, `DecisionTopologyConfig`) и `ErrorHandlingDeserializer` → `LongDeserializer` (`TransferEventListener`) |
| Сериализация значения | JSON: `JsonSerializer` (limit-service); у получателей — `JsonSerde<Transfer>` (Kafka Streams) и `ErrorHandlingDeserializer` → `JsonDeserializer` (`TransferEventListener`) |
| Заголовки | `__TypeId__` = `com.example.common.Transfer` (добавляется `JsonSerializer` по умолчанию) |
| Отправители | limit-service: `LimitService.sendAndWait` (`KafkaTemplate<Long, Transfer>`) |
| Получатели (группа потребителей) | 1) transfer-service: Kafka Streams `DecisionTopologyConfig.decisionStream` (join с screening-results), группа = `application.id` `transfer-service` (`spring.kafka.streams.application-id` не задан, используется `spring.application.name`); ошибки десериализации — `LogAndContinueExceptionHandler`; 2) transfer-service: `TransferEventListener.onEvent` (listener id `transfer-tracker`), группа `transfer-tracker` |
| Начальная позиция чтения | `earliest` для группы `transfer-tracker`; для Kafka Streams в коде не задана |
| Канал ошибок | limit-results.DLT (только для группы `transfer-tracker`) |
| Формат сообщения | Transfer (см. 3.1) |

### 3.4 Канал: screening-results

| Свойство | Значение |
|---|---|
| Тип канала | Kafka-топик (создаётся бином `NewTopic`) |
| Объявление | `TopicsConfig.screeningResultsTopic` в transfer-service, limit-service, screening-service |
| Число партиций | 3 |
| Фактор репликации | в коде не задан (`TopicBuilder` без `replicas`) |
| Политика хранения / очистки | `cleanup.policy=compact` (`TopicBuilder.compact()`); параметры retention в коде не заданы |
| Ключ сообщения | `Transfer.id` (Long) — идентификатор перевода |
| Сериализация ключа | `LongSerializer` (screening-service); у получателей — `Serdes.Long()` (Kafka Streams, `DecisionTopologyConfig`) и `ErrorHandlingDeserializer` → `LongDeserializer` (`TransferEventListener`) |
| Сериализация значения | JSON: `JsonSerializer` (screening-service); у получателей — `JsonSerde<Transfer>` (Kafka Streams) и `ErrorHandlingDeserializer` → `JsonDeserializer` (`TransferEventListener`) |
| Заголовки | `__TypeId__` = `com.example.common.Transfer` (добавляется `JsonSerializer` по умолчанию) |
| Отправители | screening-service: `ScreeningService.sendAndWait` (`KafkaTemplate<Long, Transfer>`) |
| Получатели (группа потребителей) | 1) transfer-service: Kafka Streams `DecisionTopologyConfig.decisionStream` (join с limit-results), группа = `application.id` `transfer-service`; ошибки десериализации — `LogAndContinueExceptionHandler`; 2) transfer-service: `TransferEventListener.onEvent` (listener id `transfer-tracker`), группа `transfer-tracker` |
| Начальная позиция чтения | `earliest` для группы `transfer-tracker`; для Kafka Streams в коде не задана |
| Канал ошибок | screening-results.DLT (только для группы `transfer-tracker`) |
| Формат сообщения | Transfer (см. 3.1) |

### 3.5 Канал: transfers.DLT

| Свойство | Значение |
|---|---|
| Тип канала | Kafka-топик, Dead Letter Topic (создаётся бином `NewTopic`) |
| Объявление | `KafkaErrorHandlingConfig.transfersDlt` в transfer-service, limit-service, screening-service |
| Число партиций | 3 |
| Фактор репликации | в коде не задан (`TopicBuilder` без `replicas`) |
| Политика хранения / очистки | в коде не задана (`TopicBuilder` без `compact()` — действует значение брокера по умолчанию) |
| Партиция назначения | совпадает с партицией исходной записи (`record.partition()` в `KafkaErrorHandlingConfig.kafkaErrorHandler`) |
| Ключ сообщения | ключ исходной записи (`Transfer.id`) либо исходные байты ключа, если его не удалось десериализовать |
| Сериализация ключа | `DelegatingByTypeSerializer`: Long → `LongSerializer`, byte[] → `ByteArraySerializer` (`KafkaErrorHandlingConfig.keySerializer`) |
| Сериализация значения | по типу значения: byte[] → `ByteArraySerializer` (исходные байты при ошибке десериализации), прочие объекты → `JsonSerializer` |
| Заголовки | стандартные заголовки `DeadLetterPublishingRecoverer` с префиксом `kafka_dlt-` (исходные топик, партиция, offset, сведения об исключении) |
| Отправители | `DeadLetterPublishingRecoverer` (`KafkaErrorHandlingConfig.kafkaErrorHandler`, `DefaultErrorHandler` с `FixedBackOff(1000 мс, 3 повтора)`) в limit-service (группа `limit`), screening-service (группа `screening`), transfer-service (группа `transfer-tracker`) |
| Получатели (группа потребителей) | в коде не найдено |
| Формат сообщения | Transfer (см. 3.1) либо исходные байты сообщения, не прошедшего десериализацию |

### 3.6 Канал: limit-results.DLT

| Свойство | Значение |
|---|---|
| Тип канала | Kafka-топик, Dead Letter Topic (создаётся бином `NewTopic`) |
| Объявление | `KafkaErrorHandlingConfig.limitResultsDlt` (только transfer-service) |
| Число партиций | 3 |
| Фактор репликации | в коде не задан (`TopicBuilder` без `replicas`) |
| Политика хранения / очистки | в коде не задана (`TopicBuilder` без `compact()` — действует значение брокера по умолчанию) |
| Партиция назначения | совпадает с партицией исходной записи |
| Ключ сообщения | ключ исходной записи (`Transfer.id`) либо исходные байты ключа, если его не удалось десериализовать |
| Сериализация ключа | `DelegatingByTypeSerializer`: Long → `LongSerializer`, byte[] → `ByteArraySerializer` |
| Сериализация значения | byte[] → `ByteArraySerializer`, прочие объекты → `JsonSerializer` |
| Заголовки | стандартные заголовки `DeadLetterPublishingRecoverer` с префиксом `kafka_dlt-` |
| Отправители | `DeadLetterPublishingRecoverer` transfer-service (`KafkaErrorHandlingConfig.kafkaErrorHandler`) для группы `transfer-tracker` |
| Получатели (группа потребителей) | в коде не найдено |
| Формат сообщения | Transfer (см. 3.1) либо исходные байты сообщения, не прошедшего десериализацию |

### 3.7 Канал: screening-results.DLT

| Свойство | Значение |
|---|---|
| Тип канала | Kafka-топик, Dead Letter Topic (создаётся бином `NewTopic`) |
| Объявление | `KafkaErrorHandlingConfig.screeningResultsDlt` (только transfer-service) |
| Число партиций | 3 |
| Фактор репликации | в коде не задан (`TopicBuilder` без `replicas`) |
| Политика хранения / очистки | в коде не задана (`TopicBuilder` без `compact()` — действует значение брокера по умолчанию) |
| Партиция назначения | совпадает с партицией исходной записи |
| Ключ сообщения | ключ исходной записи (`Transfer.id`) либо исходные байты ключа, если его не удалось десериализовать |
| Сериализация ключа | `DelegatingByTypeSerializer`: Long → `LongSerializer`, byte[] → `ByteArraySerializer` |
| Сериализация значения | byte[] → `ByteArraySerializer`, прочие объекты → `JsonSerializer` |
| Заголовки | стандартные заголовки `DeadLetterPublishingRecoverer` с префиксом `kafka_dlt-` |
| Отправители | `DeadLetterPublishingRecoverer` transfer-service (`KafkaErrorHandlingConfig.kafkaErrorHandler`) для группы `transfer-tracker` |
| Получатели (группа потребителей) | в коде не найдено |
| Формат сообщения | Transfer (см. 3.1) либо исходные байты сообщения, не прошедшего десериализацию |

### 3.8 Канал: служебные топики Kafka Streams (transfer-service)

| Свойство | Значение |
|---|---|
| Тип канала | Внутренние changelog-топики оконных хранилищ join limit-results ⋈ screening-results (`DecisionTopologyConfig.decisionStream`, `StreamJoined`) |
| Имена | в коде явно не заданы (генерируются Kafka Streams с префиксом `application.id` `transfer-service`) — требует уточнения |
| Число партиций | в коде не задано |
| Политика хранения / очистки | в коде не задана |
| Ключ сообщения | Long (идентификатор перевода) |
| Сериализация ключа и значения | `StreamJoined.with(Serdes.Long(), transferSerde, transferSerde)`: ключ — `Serdes.Long()`, значение — `JsonSerde<Transfer>` |
| Отправители / получатели | только Kafka Streams-приложение transfer-service (`application.id` `transfer-service`) |
| Формат сообщения | Transfer (см. 3.1) |

## 4. РАСХОЖДЕНИЯ КОНТРАКТА API С КОДОМ

### Контракт: `transfer-service/src/main/resources/openapi/transfer-api.yaml` (OpenAPI 3.0.3)

| Элемент | В контракте | В коде |
|---|---|---|
| Код ответа 500 (POST /transfers, GET /transfers, GET /transfers/{id}) | Не описан ни для одной операции | `ApiExceptionHandler.handleUnexpected` возвращает 500 с телом `ProblemDetail` (detail «Внутренняя ошибка сервера, повторите запрос позже») на любое необработанное исключение, в т.ч. на повторно выброшенное `DataIntegrityViolationException` в `TransferController.create` |
| GET /transfers/{id}: код ответа 400 | Не описан (только 200 и 404) | Если `id` не преобразуется в `Long`, возвращается 400 с телом `ProblemDetail` (`MethodArgumentTypeMismatchException`, обработка унаследована `ApiExceptionHandler` от `ResponseEntityExceptionHandler`) |
| `TransferRequest.currency` = null | `nullable` не указан, то есть явный null недопустим (OpenAPI 3.0.3) | `@Pattern` не проверяет null: явный `"currency": null` принимается и заменяется на `"RUB"` (`TransferRequest.currencyOrDefault`) |
| `TransferRequest.amount`: число знаков после запятой | `multipleOf: 0.01`: допустимо любое значение, кратное 0.01, в т.ч. с незначащими нулями (например, `100.500`) | `@Digits(integer = 13, fraction = 2)` проверяет масштаб `BigDecimal`: значение с более чем двумя знаками после запятой (например, `100.500`) отклоняется с кодом 400 |

