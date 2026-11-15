## 1. HTTP API

#### Эндпоинт: POST /transfers
Описание: создание перевода (источник — `TransferController.create`, `transfer-service/src/main/java/com/example/transfer/controller/TransferController.java`).

Параметры запроса (тело — `TransferRequest`, `transfer-service/src/main/java/com/example/transfer/controller/TransferRequest.java`):

| Параметр | Источник (body/path/query) | Тип | Обязательный | Описание |
|---|---|---|---|---|
| id | body | integer (int64) | да (`@NotNull`) | Идентификатор перевода, назначается клиентом |
| senderId | body | integer (int64) | да (`@NotNull`) | Идентификатор отправителя |
| recipientId | body | integer (int64) | да (`@NotNull`) | Идентификатор получателя |
| amount | body | number (decimal) | да (`@NotNull`, `@Positive`, `@Digits(integer=13, fraction=2)`) | Сумма перевода |
| currency | body | string | нет (опционально, null → «RUB») | Валюта, паттерн `[A-Z]{3}` |

Параметры ответа (тело 201/200 — сериализованный `TransferEntity`):

| Поле | Тип | Nullable | Описание |
|---|---|---|---|
| id | integer (int64) | нет | Идентификатор перевода |
| senderId | integer (int64) | нет | Идентификатор отправителя |
| recipientId | integer (int64) | нет | Идентификатор получателя |
| amount | number (decimal) | нет | Сумма перевода |
| currency | string | нет | Валюта |
| status | string | нет | Статус (при создании — «NEW») |
| decidedBy | string | да | Сервис, принявший решение |
| messageInfo | string | да | Причина решения |
| createdAt | string (ISO-8601) | нет | Время создания |
| updatedAt | string (ISO-8601) | нет | Время обновления |

Коды ответа:

| Код | Условие |
|---|---|
| 201 | Перевод создан и опубликован в Kafka (`ResponseEntity.status(HttpStatus.CREATED)`) |
| 200 | Дубликат с теми же параметрами (`duplicate`, идемпотентный ответ) |
| 409 | Дубликат id с другими параметрами (`duplicate`, ProblemDetail) |
| 400 | Ошибка валидации тела запроса (`@Valid` / стандартный механизм Spring) |
| 503 | Ошибка публикации в Kafka (`publishNew` → `ResponseStatusException(SERVICE_UNAVAILABLE)`) |
| 500 | Необработанное исключение (`ApiExceptionHandler.handleUnexpected`) |

#### Эндпоинт: GET /transfers
Описание: получение списка всех переводов (`TransferController.all`).

Параметры запроса: отсутствуют.

Параметры ответа (200 — массив объектов `TransferEntity`):

| Поле | Тип | Nullable | Описание |
|---|---|---|---|
| [элемент массива] | object (`TransferEntity`) | — | Поля те же, что в таблице ответа POST /transfers |

Коды ответа:

| Код | Условие |
|---|---|
| 200 | Список переводов возвращён |
| 500 | Необработанное исключение (`ApiExceptionHandler.handleUnexpected`) |

#### Эндпоинт: GET /transfers/{id}
Описание: получение перевода по id с историей изменений (`TransferController.getById`).

Параметры запроса:

| Параметр | Источник (body/path/query) | Тип | Обязательный | Описание |
|---|---|---|---|---|
| id | path | integer (int64, `@PathVariable Long`) | да | Идентификатор перевода |

Параметры ответа (200 — `Map<String, Object>` с двумя ключами):

| Поле | Тип | Nullable | Описание |
|---|---|---|---|
| transfer | object (`TransferEntity`) | нет | Поля те же, что в таблице ответа POST /transfers |
| history | array of object (`TransferHistoryEntry`) | нет | Отсортировано по stage, затем по changedAt |
| history[].id | integer (int64) | нет | Идентификатор записи истории |
| history[].transferId | integer (int64) | нет | Идентификатор перевода |
| history[].stage | string (enum: NEW, LIMIT, SCREENING, FINAL) | нет | Этап обработки |
| history[].status | string | нет | Статус на этапе |
| history[].decidedBy | string | да | Сервис, принявший решение |
| history[].messageInfo | string | да | Причина решения |
| history[].changedAt | string (ISO-8601) | нет | Время изменения |

Коды ответа:

| Код | Условие |
|---|---|
| 200 | Перевод найден |
| 404 | Перевод не найден (`ResponseEntity.notFound()`, тело отсутствует) |
| 500 | Необработанное исключение (`ApiExceptionHandler.handleUnexpected`) |

## 2. СХЕМА БД

#### Таблица: transfers (база: transferdb)
Описание: переводы (Liquibase: `transfer-service/src/main/resources/db/changelog/001-create-tables.sql`).

| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
|---|---|---|---|---|---|---|---|---|
| id | BIGINT | да | нет | да (PK) | да | нет | нет (только PK) | Идентификатор перевода |
| sender_id | BIGINT | нет | нет | нет | да | нет | да (idx_transfers_sender_id) | Идентификатор отправителя |
| recipient_id | BIGINT | нет | нет | нет | да | нет | нет | Идентификатор получателя |
| amount | DECIMAL(15,2) | нет | нет | нет | да | нет | нет | Сумма перевода |
| currency | VARCHAR(10) | нет | нет | нет | да | 'RUB' | нет | Валюта |
| status | VARCHAR(20) | нет | нет | нет | да | нет | да (idx_transfers_status) | Статус перевода |
| decided_by | VARCHAR(20) | нет | нет | нет | нет | нет | нет | Сервис, принявший решение |
| message_info | TEXT | нет | нет | нет | нет | нет | нет | Причина решения |
| created_at | TIMESTAMP | нет | нет | нет | да | нет | нет | Время создания |
| updated_at | TIMESTAMP | нет | нет | нет | нет | нет | нет | Время обновления |

#### Таблица: transfer_history (база: transferdb)
Описание: история смены статусов перевода (тот же changelog).

| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
|---|---|---|---|---|---|---|---|---|
| id | BIGSERIAL | да | нет | да (PK) | да | нет | нет (только PK) | Суррогатный идентификатор записи |
| transfer_id | BIGINT | нет | да → transfers(id) | нет | да | нет | да (idx_transfer_history_transfer_id) | Идентификатор перевода |
| status | VARCHAR(20) | нет | нет | нет | да | нет | нет | Статус на этапе |
| decided_by | VARCHAR(20) | нет | нет | нет | нет | нет | нет | Сервис, принявший решение |
| message_info | TEXT | нет | нет | нет | нет | нет | нет | Причина решения |
| changed_at | TIMESTAMP | нет | нет | нет | да | NOW() | нет | Время изменения |
| stage | VARCHAR(20) | нет | нет | нет | да | нет | нет | Этап обработки |

Ограничения уровня таблицы:

| Ограничение | Колонки |
|---|---|
| uq_transfer_history_stage (UNIQUE) | transfer_id, stage |

Допустимые значения (в БД нет CHECK-ограничений, наборы значений определены только в коде):

| Колонка | Значения |
|---|---|
| transfers.status | NEW, COMPLETED, CANCELLED, DENIED (Transfer.NEW; TransferDecisionService.decide; TransferTrackingService.applyFinalStatus) |
| transfer_history.stage | NEW, LIMIT, SCREENING, FINAL (enum HistoryStage) |
| transfer_history.status | NEW, APPROVED, DECLINED, COMPLETED, CANCELLED, DENIED (история статусов этапов) |

#### Таблица: sender_usage (база: limitdb)
Описание: накопленное использование лимитов по отправителю (Liquibase: `limit-service/src/main/resources/db/changelog/001-create-tables.sql`).

| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
|---|---|---|---|---|---|---|---|---|
| sender_id | BIGINT | да | нет | да (PK) | да | нет | нет (только PK) | Идентификатор отправителя |
| daily_used | DECIMAL(15,2) | нет | нет | нет | да | 0 | нет | Использовано за день |
| monthly_used | DECIMAL(15,2) | нет | нет | нет | да | 0 | нет | Использовано за месяц |
| last_daily_reset | DATE | нет | нет | нет | да | CURRENT_DATE | нет | Дата последнего дневного сброса |
| last_monthly_reset | DATE | нет | нет | нет | да | CURRENT_DATE | нет | Дата последнего месячного сброса |
| version | BIGINT | нет | нет | нет | да | 0 | нет | Версия записи (JPA optimistic locking, `@Version`) |

#### Таблица: reservations (база: limitdb)
Описание: решения limit-сервиса по переводам (тот же changelog).

| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
|---|---|---|---|---|---|---|---|---|
| transfer_id | BIGINT | да | нет | да (PK) | да | нет | нет (только PK) | Идентификатор перевода |
| sender_id | BIGINT | нет | нет | нет | да | нет | да (idx_reservations_sender_id) | Идентификатор отправителя |
| amount | DECIMAL(15,2) | нет | нет | нет | да | нет | нет | Зарезервированная сумма |
| decision | VARCHAR(10) | нет | нет | нет | да | нет | нет | Решение |
| reason | VARCHAR(50) | нет | нет | нет | нет | нет | нет | Причина решения |
| state | VARCHAR(20) | нет | нет | нет | да | нет | нет | Состояние резервирования |
| reserved_on | DATE | нет | нет | нет | да | нет | нет | Дата резервирования |
| created_at | TIMESTAMP | нет | нет | нет | да | нет | нет | Время создания |
| updated_at | TIMESTAMP | нет | нет | нет | да | нет | нет | Время обновления |

Допустимые значения (константы `Reservation.java`):

| Колонка | Значения |
|---|---|
| decision | APPROVED, DECLINED |
| state | RESERVED, COMMITTED, RELEASED, NONE |

#### Таблица: blocked_senders (база: screeningdb)
Описание: заблокированные отправители (Liquibase: `screening-service/src/main/resources/db/changelog/001-create-tables.sql`; тестовые данные — `002-insert-test-data.sql`).

| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
|---|---|---|---|---|---|---|---|---|
| id | BIGSERIAL | да | нет | да (PK) | да | нет | нет (только PK) | Суррогатный идентификатор |
| sender_id | BIGINT | нет | нет | да (UNIQUE) | да | нет | да (idx_blocked_senders_sender_id) | Идентификатор отправителя |
| reason | VARCHAR(255) | нет | нет | нет | да | нет | нет | Причина блокировки |
| blocked_at | TIMESTAMP | нет | нет | нет | да | NOW() | нет | Время блокировки |

#### Таблица: screening_checks (база: screeningdb)
Описание: решения screening-сервиса по переводам (тот же changelog).

| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
|---|---|---|---|---|---|---|---|---|
| transfer_id | BIGINT | да | нет | да (PK) | да | нет | нет (только PK) | Идентификатор перевода |
| sender_id | BIGINT | нет | нет | нет | да | нет | да (idx_screening_checks_sender_id) | Идентификатор отправителя |
| result | VARCHAR(10) | нет | нет | нет | да | нет | нет | Результат проверки |
| reason | VARCHAR(50) | нет | нет | нет | нет | нет | нет | Причина решения |
| checked_at | TIMESTAMP | нет | нет | нет | да | нет | нет | Время проверки |

Допустимые значения (константы `ScreeningCheck.java`):

| Колонка | Значения |
|---|---|
| result | APPROVED, DECLINED |

## 3. КОНТРАКТЫ СООБЩЕНИЙ

#### Канал: transfers
| Свойство | Значение |
|---|---|
| Тип канала | Apache Kafka topic (создаётся в TopicsConfig каждого сервиса; `KAFKA_AUTO_CREATE_TOPICS_ENABLE: "false"` в docker-compose.yml) |
| Число партиций | 3 |
| Политика хранения / очистки | compact |
| Ключ сообщения | transferId (Long) |
| Сериализация ключа | LongSerializer (продюсеры), LongDeserializer (консьюмеры), Serdes.Long() (Kafka Streams) |
| Сериализация значения | JsonSerializer / JsonDeserializer (через ErrorHandlingDeserializer), JsonSerde<Transfer> (Streams), trusted packages: "*" |
| Отправители | transfer-service (TransferController.publishNew), transfer-service Kafka Streams (DecisionTopologyConfig.decisionStream, результат join) |
| Получатели | limit-service (groupId: limit, TransferEventsListener), screening-service (groupId: screening, TransferEventsListener), transfer-service (groupId: transfer-tracker, TransferEventListener) |

#### Канал: limit-results
| Свойство | Значение |
|---|---|
| Тип канала | Apache Kafka topic (TopicsConfig) |
| Число партиций | 3 |
| Политика хранения / очистки | compact |
| Ключ сообщения | transferId (Long) |
| Сериализация ключа | LongSerializer / LongDeserializer / Serdes.Long() |
| Сериализация значения | JsonSerializer / JsonDeserializer / JsonSerde<Transfer>, trusted packages: "*" |
| Отправители | limit-service (LimitService.sendAndWait) |
| Получатели | transfer-service Kafka Streams (DecisionTopologyConfig.decisionStream), transfer-service (groupId: transfer-tracker, TransferEventListener) |

#### Канал: screening-results
| Свойство | Значение |
|---|---|
| Тип канала | Apache Kafka topic (TopicsConfig) |
| Число партиций | 3 |
| Политика хранения / очистки | compact |
| Ключ сообщения | transferId (Long) |
| Сериализация ключа | LongSerializer / LongDeserializer / Serdes.Long() |
| Сериализация значения | JsonSerializer / JsonDeserializer / JsonSerde<Transfer>, trusted packages: "*" |
| Отправители | screening-service (ScreeningService.sendAndWait) |
| Получатели | transfer-service Kafka Streams (DecisionTopologyConfig.decisionStream), transfer-service (groupId: transfer-tracker, TransferEventListener) |

#### Канал: transfers.DLT
| Свойство | Значение |
|---|---|
| Тип канала | Apache Kafka topic, dead letter topic (создаётся в KafkaErrorHandlingConfig limit-service, screening-service и transfer-service) |
| Число партиций | 3 |
| Политика хранения / очистки | compact не задан; retention — в коде не найдено (настройки по умолчанию) |
| Ключ сообщения | исходный ключ сообщения (byte[] или Long) |
| Сериализация ключа | DelegatingByTypeSerializer: byte[] → ByteArraySerializer, Long → LongSerializer |
| Сериализация значения | DelegatingByTypeSerializer: byte[] → ByteArraySerializer, Object → JsonSerializer |
| Отправители | DeadLetterPublishingRecoverer (DefaultErrorHandler, KafkaErrorHandlingConfig каждого сервиса) при исчерпании повторов (FixedBackOff 1000 мс, 3 повтора) |
| Получатели | в коде не найдено |

#### Канал: limit-results.DLT
| Свойство | Значение |
|---|---|
| Тип канала | Apache Kafka topic, dead letter topic (создаётся только в KafkaErrorHandlingConfig transfer-service) |
| Число партиций | 3 |
| Политика хранения / очистки | compact не задан; retention — в коде не найдено |
| Ключ сообщения | исходный ключ сообщения (byte[] или Long) |
| Сериализация ключа | DelegatingByTypeSerializer (byte[] / Long) |
| Сериализация значения | DelegatingByTypeSerializer: byte[] → ByteArraySerializer, Object → JsonSerializer |
| Отправители | DeadLetterPublishingRecoverer (DefaultErrorHandler) при неудачной обработке сообщений из limit-results |
| Получатели | в коде не найдено |

#### Канал: screening-results.DLT
| Свойство | Значение |
|---|---|
| Тип канала | Apache Kafka topic, dead letter topic (создаётся только в KafkaErrorHandlingConfig transfer-service) |
| Число партиций | 3 |
| Политика хранения / очистки | compact не задан; retention — в коде не найдено |
| Ключ сообщения | исходный ключ сообщения (byte[] или Long) |
| Сериализация ключа | DelegatingByTypeSerializer (byte[] / Long) |
| Сериализация значения | DelegatingByTypeSerializer: byte[] → ByteArraySerializer, Object → JsonSerializer |
| Отправители | DeadLetterPublishingRecoverer (DefaultErrorHandler) при неудачной обработке сообщений из screening-results |
| Получатели | в коде не найдено |

Формат сообщения (общий для всех топиков — record `Transfer`, `transfer-common/src/main/java/com/example/common/Transfer.java`):

| Поле | Тип | Nullable | Описание | Пример |
|---|---|---|---|---|
| id | Long | нет | Идентификатор перевода | 1001 |
| senderId | Long | нет | Идентификатор отправителя | 42 |
| recipientId | Long | нет | Идентификатор получателя | 7 |
| amount | BigDecimal | нет | Сумма перевода | 1500.00 |
| currency | String | нет | Валюта | "RUB" |
| status | String | нет | Статус/решение: NEW, APPROVED, DECLINED, COMPLETED, CANCELLED, DENIED | "APPROVED" |
| decidedBy | String | да | Сервис, принявший решение ("limit", "screening" или null) | "limit" |
| messageInfo | String | да | Причина решения (например, DAILY_LIMIT_EXCEEDED, MONTHLY_LIMIT_EXCEEDED, INVALID_AMOUNT, SENDER_BLOCKED) | "DAILY_LIMIT_EXCEEDED" |
| createdAt | LocalDateTime | нет | Время создания перевода | "2026-11-15T19:03:34" |

## 4. РАСХОЖДЕНИЯ КОНТРАКТА API С КОДОМ

Файл контракта: `transfer-service/src/main/resources/openapi/transfer-api.yaml` (OpenAPI 3.0.3).

| Элемент | В контракте | В коде |
|---|---|---|
| Все три эндпоинта — ответ 500 | В контракте не описан | `ApiExceptionHandler.handleUnexpected` перехватывает любые исключения и возвращает 500 с `application/problem+json` |
| Transfer.createdAt / Transfer.updatedAt / HistoryEntry.changedAt | type: string (без format) | В коде `LocalDateTime`, сериализуется в ISO-8601; формат в контракте не зафиксирован — требует уточнения |
| HistoryEntry.stage | enum (NEW, LIMIT, SCREENING, FINAL) | В коде `HistoryStage` — enum, сериализуется строкой (`@Enumerated(EnumType.STRING)`); расхождений по составу значений нет |
| POST /transfers — 400 | Задекларирован с `application/problem+json` | Валидационные ошибки обрабатываются стандартным механизмом Spring (`ApiExceptionHandler` наследует `ResponseEntityExceptionHandler`, `spring.mvc.problemdetails.enabled: true`) — соответствует контракту |

Остальные элементы (paths, схемы TransferRequest / Transfer / TransferWithHistory / HistoryEntry, коды 200/201/404/409/503, паттерн и default для currency, ограничения amount) расхождений с кодом не выявили.