# 1. HTTP API

## Эндпоинт: POST /transfers
Описание: Создание записи перевода с переданным клиентом идентификатором (`TransferController.create`, `transfer-service/src/main/java/com/example/transfer/controller/TransferController.java`).

**Параметры запроса:**

| Параметр | Источник (body/path/query) | Тип | Обязательный | Описание |
|---|---|---|---|---|
| id | body (application/json) | integer (int64) | да — `@NotNull` (`TransferRequest.id`) | Идентификатор перевода, задаётся клиентом |
| senderId | body (application/json) | integer (int64) | да — `@NotNull` | Идентификатор отправителя |
| recipientId | body (application/json) | integer (int64) | да — `@NotNull` | Идентификатор получателя |
| amount | body (application/json) | number (decimal) | да — `@NotNull`, `@Positive`, `@Digits(integer=13, fraction=2)` | Сумма: > 0, максимум 13 цифр целой части и 2 знака дробной |
| currency | body (application/json) | string | нет — `@Pattern("[A-Z]{3}")` применяется только к заданному значению | Код валюты; при отсутствии подставляется `"RUB"` (`TransferRequest.currencyOrDefault`) |

**Параметры ответа (201/200, application/json — `TransferEntity`):**

| Поле | Тип | Nullable | Описание |
|---|---|---|---|
| id | integer (int64) | нет | Идентификатор перевода |
| senderId | integer (int64) | нет | Идентификатор отправителя |
| recipientId | integer (int64) | нет | Идентификатор получателя |
| amount | number (decimal) | нет | Сумма перевода |
| currency | string | нет | Код валюты |
| status | string | нет | `NEW`, `COMPLETED`, `CANCELLED`, `DENIED` |
| decidedBy | string | да | Источник решения: `limit`, `screening` |
| messageInfo | string | да | Код/текст причины решения |
| createdAt | string (date-time) | нет | Момент создания (`LocalDateTime`) |
| updatedAt | string (date-time) | да | Момент обновления; колонка в БД nullable, в коде заполняется |

**Коды ответа:**

| Код | Условие |
|---|---|
| 201 | Создана новая запись перевода (`TransferController.create`) |
| 200 | Запись с таким id уже существует и параметры совпадают (`TransferController.duplicate`) |
| 400 | Не пройдена валидация `@Valid` / некорректное тело запроса (базовый `ResponseEntityExceptionHandler`, `spring.mvc.problemdetails.enabled=true`) → application/problem+json |
| 409 | Тот же id при других senderId/recipientId/amount/currency (`TransferController.duplicate`) → `ProblemDetail` |
| 503 | Ошибка или таймаут отправки в Kafka (`TransferController.publishNew`, `ResponseStatusException(KAFKA_UNAVAILABLE)`) → application/problem+json |
| 500 | Любое иное необработанное исключение (`ApiExceptionHandler.handleUnexpected`) → `ProblemDetail` |

## Эндпоинт: GET /transfers
Описание: Возврат списка всех переводов, упорядоченного по `createdAt` по убыванию (`TransferController.all`, `TransferRepository.findAllByOrderByCreatedAtDesc`).

**Параметры запроса:**

| Параметр | Источник (body/path/query) | Тип | Обязательный | Описание |
|---|---|---|---|---|
| — | — | — | — | Параметры не принимаются (`TransferController.all`) |

**Параметры ответа (200, application/json — массив `TransferEntity`):**

| Поле | Тип | Nullable | Описание |
|---|---|---|---|
| [] | array of object | нет | Массив переводов, может быть пустым |
| [].id | integer (int64) | нет | Идентификатор перевода |
| [].senderId | integer (int64) | нет | Идентификатор отправителя |
| [].recipientId | integer (int64) | нет | Идентификатор получателя |
| [].amount | number (decimal) | нет | Сумма перевода |
| [].currency | string | нет | Код валюты |
| [].status | string | нет | `NEW`, `COMPLETED`, `CANCELLED`, `DENIED` |
| [].decidedBy | string | да | `limit`, `screening` |
| [].messageInfo | string | да | Код/текст причины решения |
| [].createdAt | string (date-time) | нет | Момент создания |
| [].updatedAt | string (date-time) | да | Момент обновления |

**Коды ответа:**

| Код | Условие |
|---|---|
| 200 | Список возвращён (в том числе пустой) |
| 500 | Необработанное исключение (`ApiExceptionHandler.handleUnexpected`) → `ProblemDetail` |

## Эндпоинт: GET /transfers/{id}
Описание: Возврат перевода и связанных записей истории по идентификатору (`TransferController.getById`).

**Параметры запроса:**

| Параметр | Источник (body/path/query) | Тип | Обязательный | Описание |
|---|---|---|---|---|
| id | path | integer (int64) | да — `@PathVariable` (ограничений на значение в коде нет) | Идентификатор перевода |

**Параметры ответа (200, application/json — `Map<String, Object>`):**

| Поле | Тип | Nullable | Описание |
|---|---|---|---|
| transfer | object | нет | Перевод (`TransferEntity`) |
| transfer.id | integer (int64) | нет | Идентификатор перевода |
| transfer.senderId | integer (int64) | нет | Идентификатор отправителя |
| transfer.recipientId | integer (int64) | нет | Идентификатор получателя |
| transfer.amount | number (decimal) | нет | Сумма перевода |
| transfer.currency | string | нет | Код валюты |
| transfer.status | string | нет | `NEW`, `COMPLETED`, `CANCELLED`, `DENIED` |
| transfer.decidedBy | string | да | `limit`, `screening` |
| transfer.messageInfo | string | да | Код/текст причины решения |
| transfer.createdAt | string (date-time) | нет | Момент создания |
| transfer.updatedAt | string (date-time) | да | Момент обновления |
| history | array of object | нет | Записи `TransferHistoryEntry`, сортировка по `stage` (порядок enum), затем по `changedAt` |
| history[].id | integer (int64) | нет | Идентификатор записи истории |
| history[].transferId | integer (int64) | нет | Идентификатор перевода |
| history[].stage | string (enum) | нет | `NEW`, `LIMIT`, `SCREENING`, `FINAL` (`HistoryStage`) |
| history[].status | string | нет | `NEW`, `APPROVED`, `DECLINED`, `COMPLETED`, `CANCELLED`, `DENIED` |
| history[].decidedBy | string | да | `limit`, `screening` |
| history[].messageInfo | string | да | Код/текст причины решения |
| history[].changedAt | string (date-time) | нет | Момент записи этапа |

**Коды ответа:**

| Код | Условие |
|---|---|
| 200 | Перевод найден, тело `{transfer, history}` |
| 404 | Перевод не найден — `ResponseEntity.notFound().build()`, тело отсутствует |
| 400 | Значение `id` не приводится к `Long` (`TypeMismatchException`, базовый `ResponseEntityExceptionHandler` класса `ApiExceptionHandler`) → application/problem+json |
| 500 | Необработанное исключение (`ApiExceptionHandler.handleUnexpected`) → `ProblemDetail` |

## Общий формат тела ошибки (`ProblemDetail`, application/problem+json)

| Поле | Тип | Nullable | Описание |
|---|---|---|---|
| type | string (URI) | нет | Задаётся Spring, в коде не переопределяется |
| title | string | да | Задаётся Spring по HTTP-статусу, в коде не переопределяется |
| status | integer | нет | HTTP-код (`HttpStatus`, заданный в коде) |
| detail | string | да | Текст: `ApiExceptionHandler.INTERNAL_ERROR` (500), `TransferController.KAFKA_UNAVAILABLE` (503), текст конфликта в `TransferController.duplicate` (409); для 400 — текст Spring |
| instance | string (URI) | да | Задаётся Spring, в коде не переопределяется |

# 2. СХЕМА БД

## Таблица: transfers (база: transferdb)
Описание: Хранение переводов и их текущих статусов (`transfer-service/src/main/resources/db/changelog/001-create-tables.sql`, сущность `TransferEntity`).

| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
|---|---|---|---|---|---|---|---|---|
| id | BIGINT | да | — | да (PK) | да | — | PK (неявный уникальный индекс) | Идентификатор перевода, задаётся клиентом |
| sender_id | BIGINT | — | — | — | да | — | idx_transfers_sender_id | Идентификатор отправителя |
| recipient_id | BIGINT | — | — | — | да | — | — | Идентификатор получателя |
| amount | DECIMAL(15,2) | — | — | — | да | — | — | Сумма перевода |
| currency | VARCHAR(10) | — | — | — | да | 'RUB' | — | Код валюты |
| status | VARCHAR(20) | — | — | — | да | — | idx_transfers_status | Текущий статус перевода |
| decided_by | VARCHAR(20) | — | — | — | нет | — | — | Источник итогового решения |
| message_info | TEXT | — | — | — | нет | — | — | Код/текст причины решения |
| created_at | TIMESTAMP | — | — | — | да | — | — | Момент создания |
| updated_at | TIMESTAMP | — | — | — | нет | — | — | Момент последнего обновления |

**Допустимые значения:**

| Колонка | Значения |
|---|---|
| status | `NEW`, `COMPLETED`, `CANCELLED`, `DENIED` (`TransferTrackingService.createTransfer`, `TransferDecisionService.decide`); CHECK-ограничения в DDL нет |
| decided_by | `limit`, `screening`, NULL (`TransferDecisionService.decide`); CHECK-ограничения в DDL нет |

## Таблица: transfer_history (база: transferdb)
Описание: Хранение записей об этапах обработки перевода (`transfer-service/src/main/resources/db/changelog/001-create-tables.sql`, сущность `TransferHistoryEntry`).

| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
|---|---|---|---|---|---|---|---|---|
| id | BIGSERIAL | да | — | да (PK) | да | nextval последовательности (BIGSERIAL) | PK | Суррогатный ключ (`@GeneratedValue(IDENTITY)`) |
| transfer_id | BIGINT | — | да → transfers(id) | нет (входит в составной UNIQUE) | да | — | idx_transfer_history_transfer_id, uq_transfer_history_stage | Ссылка на перевод |
| status | VARCHAR(20) | — | — | — | да | — | — | Статус, зафиксированный на этапе |
| decided_by | VARCHAR(20) | — | — | — | нет | — | — | Источник решения |
| message_info | TEXT | — | — | — | нет | — | — | Код/текст причины решения |
| changed_at | TIMESTAMP | — | — | — | да | NOW() | — | Момент записи этапа (`TransferTrackingService.now`, микросекунды) |
| stage | VARCHAR(20) | — | — | нет (входит в составной UNIQUE) | да | — | uq_transfer_history_stage | Этап (`HistoryStage`, `@Enumerated(STRING)`) |

**Ограничения уровня таблицы:**

| Ограничение | Колонки |
|---|---|
| PRIMARY KEY | id |
| FOREIGN KEY → transfers(id) | transfer_id |
| UNIQUE uq_transfer_history_stage | transfer_id, stage |

**Допустимые значения:**

| Колонка | Значения |
|---|---|
| stage | `NEW`, `LIMIT`, `SCREENING`, `FINAL` (`HistoryStage`); CHECK-ограничения в DDL нет |
| status | `NEW`, `APPROVED`, `DECLINED`, `COMPLETED`, `CANCELLED`, `DENIED` (`TransferTrackingService`, `TransferDecisionService`); CHECK-ограничения в DDL нет |
| decided_by | `limit`, `screening`, NULL |

## Таблица: sender_usage (база: limitdb)
Описание: Хранение накопленного использования лимитов отправителем (`limit-service/src/main/resources/db/changelog/001-create-tables.sql`, сущность `SenderUsage`).

| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
|---|---|---|---|---|---|---|---|---|
| sender_id | BIGINT | да | — | да (PK) | да | — | PK | Идентификатор отправителя |
| daily_used | DECIMAL(15,2) | — | — | — | да | 0 | — | Использованная сумма за день |
| monthly_used | DECIMAL(15,2) | — | — | — | да | 0 | — | Использованная сумма за месяц |
| last_daily_reset | DATE | — | — | — | да | CURRENT_DATE | — | Дата последнего дневного сброса |
| last_monthly_reset | DATE | — | — | — | да | CURRENT_DATE | — | Дата последнего месячного сброса |
| version | BIGINT | — | — | — | да | 0 | — | Счётчик оптимистичной блокировки (`@Version`, `SenderUsage.version`) |

## Таблица: reservations (база: limitdb)
Описание: Хранение решения по лимитам и состояния резерва для перевода (`limit-service/src/main/resources/db/changelog/001-create-tables.sql`, сущность `Reservation`).

| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
|---|---|---|---|---|---|---|---|---|
| transfer_id | BIGINT | да | — | да (PK) | да | — | PK | Идентификатор перевода |
| sender_id | BIGINT | — | — | — | да | — | idx_reservations_sender_id | Идентификатор отправителя |
| amount | DECIMAL(15,2) | — | — | — | да | — | — | Сумма резервирования |
| decision | VARCHAR(10) | — | — | — | да | — | — | Решение по лимитам |
| reason | VARCHAR(50) | — | — | — | нет | — | — | Код причины отказа |
| state | VARCHAR(20) | — | — | — | да | — | — | Состояние резерва |
| reserved_on | DATE | — | — | — | да | — | — | Дата резервирования |
| created_at | TIMESTAMP | — | — | — | да | — | — | Момент создания записи |
| updated_at | TIMESTAMP | — | — | — | да | — | — | Момент обновления записи |

**Допустимые значения:**

| Колонка | Значения |
|---|---|
| decision | `APPROVED`, `DECLINED` (`Reservation.APPROVED`, `Reservation.DECLINED`); CHECK-ограничения в DDL нет |
| state | `RESERVED`, `COMMITTED`, `RELEASED`, `NONE` (`Reservation.STATE_*`); CHECK-ограничения в DDL нет |
| reason | `INVALID_AMOUNT`, `DAILY_LIMIT_EXCEEDED`, `MONTHLY_LIMIT_EXCEEDED`, NULL (`LimitLedger.decide`) |

## Таблица: blocked_senders (база: screeningdb)
Описание: Хранение списка заблокированных отправителей (`screening-service/src/main/resources/db/changelog/001-create-tables.sql`, сущность `BlockedSender`).

| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
|---|---|---|---|---|---|---|---|---|
| id | BIGSERIAL | да | — | да (PK) | да | nextval последовательности (BIGSERIAL) | PK | Суррогатный ключ (`@GeneratedValue(IDENTITY)`) |
| sender_id | BIGINT | — | — | да (UNIQUE) | да | — | неявный UNIQUE-индекс, idx_blocked_senders_sender_id | Идентификатор заблокированного отправителя |
| reason | VARCHAR(255) | — | — | — | да | — | — | Причина блокировки, свободный текст (значения в `002-insert-test-data.sql`) |
| blocked_at | TIMESTAMP | — | — | — | да | NOW() | — | Момент блокировки |

## Таблица: screening_checks (база: screeningdb)
Описание: Хранение результатов скрининга по каждому переводу (`screening-service/src/main/resources/db/changelog/001-create-tables.sql`, сущность `ScreeningCheck`).

| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
|---|---|---|---|---|---|---|---|---|
| transfer_id | BIGINT | да | — | да (PK) | да | — | PK | Идентификатор перевода |
| sender_id | BIGINT | — | — | — | да | — | idx_screening_checks_sender_id | Идентификатор отправителя |
| result | VARCHAR(10) | — | — | — | да | — | — | Результат проверки |
| reason | VARCHAR(50) | — | — | — | нет | — | — | Код причины отказа |
| checked_at | TIMESTAMP | — | — | — | да | — | — | Момент проверки |

**Допустимые значения:**

| Колонка | Значения |
|---|---|
| result | `APPROVED`, `DECLINED` (`ScreeningCheck.APPROVED`, `ScreeningCheck.DECLINED`); CHECK-ограничения в DDL нет |
| reason | `SENDER_BLOCKED`, NULL (`ScreeningJournal.check`) |

# 3. КОНТРАКТЫ СООБЩЕНИЙ

## Общие параметры Kafka-окружения

| Свойство | Значение |
|---|---|
| Транспорт | Apache Kafka; брокер `confluentinc/cp-kafka:7.6.0`, KRaft, один узел (`docker-compose.yml`, сервис `broker`) |
| Bootstrap-серверы | профиль docker: `broker:29092` (`application-docker.yml` всех трёх сервисов); вне docker в `application.yml` не заданы, порт `9092` проброшен на localhost (`docker-compose.yml`) |
| Автосоздание топиков | отключено — `KAFKA_AUTO_CREATE_TOPICS_ENABLE: "false"` (`docker-compose.yml`) |
| Объявление топиков | `NewTopic`-бины в `TopicsConfig` и `KafkaErrorHandlingConfig` всех трёх сервисов; `spring.kafka.admin.modify-topic-configs: true` |
| Сериализация ключа (общая) | producer: `LongSerializer`; consumer: `ErrorHandlingDeserializer` → делегат `LongDeserializer` |
| Сериализация значения (общая) | producer: `JsonSerializer`; consumer: `ErrorHandlingDeserializer` → делегат `JsonDeserializer`, `spring.json.trusted.packages: "*"` |
| auto-offset-reset | `earliest` (все три сервиса) |
| Таймауты producer | `max.block.ms=5000`, `request.timeout.ms=5000`, `delivery.timeout.ms=10000`; синхронное ожидание `.get(10, TimeUnit.SECONDS)` (`TransferController.publishNew`, `LimitService.sendAndWait`, `ScreeningService.sendAndWait`) |
| Обработка ошибок слушателей | `DefaultErrorHandler` + `FixedBackOff(1000 мс, 3 попытки)` + `DeadLetterPublishingRecoverer` → `<topic>.DLT`, партиция исходной записи (`KafkaErrorHandlingConfig` во всех сервисах) |
| Kafka Streams | только transfer-service (`@EnableKafkaStreams`, `DecisionTopologyConfig`); `state-dir=/tmp/transfer-service-streams`, `default.key.serde=Serdes$LongSerde`, `default.value.serde=JsonSerde`, `default.deserialization.exception.handler=LogAndContinueExceptionHandler` |
| Заголовки типов сообщений | в коде не переопределены — используются значения по умолчанию `JsonSerializer`/`JsonDeserializer` |

## Канал: transfers

| Свойство | Значение |
|---|---|
| Тип канала | Kafka-топик |
| Число партиций | 3 (`TopicBuilder.name("transfers").partitions(3).compact()` — `TopicsConfig` в transfer-service, limit-service, screening-service) |
| Политика хранения / очистки | `compact` (`cleanup.policy=compact`); retention.ms/retention.bytes в коде не заданы |
| Фактор репликации | в коде не задан (`TopicBuilder.replicas` не вызывается) |
| Ключ сообщения | `Long` — `Transfer.id` |
| Сериализация ключа | `LongSerializer` (`KafkaTemplate`), `Serdes.Long` (Streams); чтение — `ErrorHandlingDeserializer` → `LongDeserializer` |
| Сериализация значения | `JsonSerializer` (`KafkaTemplate`), `JsonSerde<Transfer>` (Streams); чтение — `ErrorHandlingDeserializer` → `JsonDeserializer` |
| Отправители | transfer-service: `TransferController.publishNew` (status `NEW`); transfer-service: `DecisionTopologyConfig.decisionStream` → `.to("transfers")` (итоговые статусы) |
| Получатели (группы) | limit-service — group `limit`, id `limit-transfers-listener` (`TransferEventsListener.onEvent`); screening-service — group `screening`, id `screening-transfers-listener` (`TransferEventsListener.onEvent`); transfer-service — group `transfer-tracker`, id `transfer-tracker` (`TransferEventListener.onEvent`) |
| Используемые заголовки | `KafkaHeaders.RECEIVED_TOPIC` (`TransferEventListener.onEvent`) |
| Формат сообщения | `Transfer` — см. «Формат сообщения Transfer» |
| Канал ошибок | `transfers.DLT` |

## Канал: limit-results

| Свойство | Значение |
|---|---|
| Тип канала | Kafka-топик |
| Число партиций | 3 (`TopicsConfig.limitResultsTopic` во всех трёх сервисах) |
| Политика хранения / очистки | `compact`; retention в коде не задан |
| Фактор репликации | в коде не задан |
| Ключ сообщения | `Long` — `Transfer.id` (`LimitService.sendAndWait`) |
| Сериализация ключа | `LongSerializer`; чтение — `ErrorHandlingDeserializer` → `LongDeserializer`, в Streams — `Serdes.Long` |
| Сериализация значения | `JsonSerializer`; чтение — `ErrorHandlingDeserializer` → `JsonDeserializer`, в Streams — `JsonSerde<Transfer>` (`Consumed.with(...)`) |
| Отправители | limit-service: `LimitService.sendAndWait` (`decidedBy="limit"`) |
| Получатели (группы) | transfer-service Kafka Streams — `DecisionTopologyConfig.decisionStream`, `builder.stream("limit-results", ...)`, group = `application.id` (явно не задан: `spring.kafka.streams.application-id` отсутствует, используется `spring.application.name=transfer-service`); transfer-service — group `transfer-tracker`, id `transfer-tracker` (`TransferEventListener`) |
| Формат сообщения | `Transfer` — см. «Формат сообщения Transfer» |
| Канал ошибок | `limit-results.DLT` |

## Канал: screening-results

| Свойство | Значение |
|---|---|
| Тип канала | Kafka-топик |
| Число партиций | 3 (`TopicsConfig.screeningResultsTopic` во всех трёх сервисах) |
| Политика хранения / очистки | `compact`; retention в коде не задан |
| Фактор репликации | в коде не задан |
| Ключ сообщения | `Long` — `Transfer.id` (`ScreeningService.sendAndWait`) |
| Сериализация ключа | `LongSerializer`; чтение — `ErrorHandlingDeserializer` → `LongDeserializer`, в Streams — `Serdes.Long` |
| Сериализация значения | `JsonSerializer`; чтение — `ErrorHandlingDeserializer` → `JsonDeserializer`, в Streams — `JsonSerde<Transfer>` |
| Отправители | screening-service: `ScreeningService.sendAndWait` (`decidedBy="screening"`) |
| Получатели (группы) | transfer-service Kafka Streams — `DecisionTopologyConfig.decisionStream`, `builder.stream("screening-results", ...)`, group = `application.id` (явно не задан, используется `spring.application.name=transfer-service`); transfer-service — group `transfer-tracker`, id `transfer-tracker` (`TransferEventListener`) |
| Формат сообщения | `Transfer` — см. «Формат сообщения Transfer» |
| Канал ошибок | `screening-results.DLT` |

## Канал: transfers.DLT

| Свойство | Значение |
|---|---|
| Тип канала | Kafka-топик (dead letter) |
| Число партиций | 3 (`KafkaErrorHandlingConfig.transfersDlt` в transfer-service, limit-service, screening-service) |
| Политика хранения / очистки | в коде не задана (`TopicBuilder` без `.compact()`) — применяется политика брокера по умолчанию |
| Фактор репликации | в коде не задан |
| Ключ сообщения | ключ исходной записи без изменений (`Long`, либо `byte[]` при ошибке десериализации) |
| Сериализация ключа | `DelegatingByTypeSerializer`: `byte[]` → `ByteArraySerializer`, `Long` → `LongSerializer` |
| Сериализация значения | `byte[]` → `ByteArraySerializer` (rawTemplate); иначе `JsonSerializer` (jsonTemplate) |
| Партиционирование | `new TopicPartition(record.topic() + ".DLT", record.partition())` — номер партиции исходной записи |
| Отправители | `DeadLetterPublishingRecoverer` из `KafkaErrorHandlingConfig` limit-service, screening-service, transfer-service |
| Получатели (группы) | в коде не найдено |
| Формат сообщения | см. «Формат сообщения DLT» |

## Канал: limit-results.DLT

| Свойство | Значение |
|---|---|
| Тип канала | Kafka-топик (dead letter) |
| Число партиций | 3 (`KafkaErrorHandlingConfig.limitResultsDlt` — только в transfer-service) |
| Политика хранения / очистки | в коде не задана — политика брокера по умолчанию |
| Фактор репликации | в коде не задан |
| Ключ сообщения | ключ исходной записи без изменений (`Long` / `byte[]`) |
| Сериализация ключа | `DelegatingByTypeSerializer` (`ByteArraySerializer` / `LongSerializer`) |
| Сериализация значения | `ByteArraySerializer` для `byte[]`, иначе `JsonSerializer` |
| Партиционирование | партиция исходной записи (`record.partition()`) |
| Отправители | transfer-service: `DeadLetterPublishingRecoverer` (`KafkaErrorHandlingConfig.kafkaErrorHandler`) для слушателя `transfer-tracker`; в limit-service топик не объявлен и не используется |
| Получатели (группы) | в коде не найдено |
| Формат сообщения | см. «Формат сообщения DLT» |

## Канал: screening-results.DLT

| Свойство | Значение |
|---|---|
| Тип канала | Kafka-топик (dead letter) |
| Число партиций | 3 (`KafkaErrorHandlingConfig.screeningResultsDlt` — только в transfer-service) |
| Политика хранения / очистки | в коде не задана — политика брокера по умолчанию |
| Фактор репликации | в коде не задан |
| Ключ сообщения | ключ исходной записи без изменений (`Long` / `byte[]`) |
| Сериализация ключа | `DelegatingByTypeSerializer` (`ByteArraySerializer` / `LongSerializer`) |
| Сериализация значения | `ByteArraySerializer` для `byte[]`, иначе `JsonSerializer` |
| Партиционирование | партиция исходной записи (`record.partition()`) |
| Отправители | transfer-service: `DeadLetterPublishingRecoverer` (`KafkaErrorHandlingConfig.kafkaErrorHandler`) для слушателя `transfer-tracker`; в screening-service топик не объявлен и не используется |
| Получатели (группы) | в коде не найдено |
| Формат сообщения | см. «Формат сообщения DLT» |

## Формат сообщения Transfer (каналы: transfers, limit-results, screening-results)

| Поле | Тип | Nullable | Описание | Пример |
|---|---|---|---|---|
| id | Long | нет | Идентификатор перевода, он же ключ сообщения (`Transfer.id`, `transfer-common/src/main/java/com/example/common/Transfer.java`) | 1001 |
| senderId | Long | нет | Идентификатор отправителя | 7 |
| recipientId | Long | нет | Идентификатор получателя | 15 |
| amount | BigDecimal | нет | Сумма перевода | 1500.00 |
| currency | String | нет | Код валюты | "RUB" |
| status | String | нет | `NEW` (transfers, публикация из API); `APPROVED`/`DECLINED` (limit-results, screening-results); `COMPLETED`/`CANCELLED`/`DENIED` (transfers, выход Streams) | "APPROVED" |
| decidedBy | String | да | `limit` (`LimitService`), `screening` (`ScreeningService`); NULL для `NEW`, `COMPLETED`, `DENIED` (`TransferDecisionService.decide`) | "limit" |
| messageInfo | String | да | Код причины: `INVALID_AMOUNT`, `DAILY_LIMIT_EXCEEDED`, `MONTHLY_LIMIT_EXCEEDED`, `SENDER_BLOCKED`, их конкатенация через `"; "`, строка `UNEXPECTED_RESULT: limit=..., screening=...` (`TransferDecisionService.decide`) | "DAILY_LIMIT_EXCEEDED" |
| createdAt | LocalDateTime | нет | Момент создания перевода; JSR-310 (`jackson-datatype-jsr310` в pom limit-service/screening-service, `spring-boot-starter-web` в transfer-service) | "2026-11-17T10:15:30" |

## Формат сообщения DLT (каналы: transfers.DLT, limit-results.DLT, screening-results.DLT)

| Поле | Тип | Nullable | Описание | Пример |
|---|---|---|---|---|
| key | Long / byte[] | да | Ключ исходной записи без изменений; `byte[]` при ошибке десериализации ключа (`KafkaErrorHandlingConfig.keySerializer`) | 1001 |
| value | byte[] / JSON (`Object`) | да | Тело исходной записи: для `Transfer` — см. «Формат сообщения Transfer»; при ошибке десериализации — «сырые» байты (`rawTemplate`) | — |
| headers | заголовки Kafka-записи | да | Служебные заголовки DLT добавляются `DeadLetterPublishingRecoverer`; в коде не переопределены, состав в коде не найден | — |

# 4. РАСХОЖДЕНИЯ КОНТРАКТА API С КОДОМ

| Элемент | В контракте | В коде |
|---|---|---|
| POST /transfers, ответ 500 | не описан (только 201, 200, 400, 409, 503) | `ApiExceptionHandler.handleUnexpected(Exception)` возвращает `ProblemDetail` 500 |
| GET /transfers, ответ 500 | не описан (только 200) | `ApiExceptionHandler.handleUnexpected` возвращает `ProblemDetail` 500 |
| GET /transfers/{id}, ответ 500 | не описан (только 200, 404) | `ApiExceptionHandler.handleUnexpected` возвращает `ProblemDetail` 500 |
| GET /transfers/{id}, ответ 400 | не описан | Нечисловой `id` → `TypeMismatchException`, обрабатывается базовым `ResponseEntityExceptionHandler` (`ApiExceptionHandler`) → `ProblemDetail` 400 |
| Transfer.createdAt, Transfer.updatedAt | `type: string`, `format` не указан | `LocalDateTime` (`TransferEntity`), сериализация JSR-310 в строку ISO-8601 date-time; `format: date-time` в контракте отсутствует |
| HistoryEntry.changedAt | `type: string`, `format` не указан | `LocalDateTime` (`TransferHistoryEntry`), строка ISO-8601 date-time |
| Обязательность полей ответа Transfer / HistoryEntry | список `required` не задан; `nullable: true` только у `decidedBy`, `messageInfo` | В DDL NOT NULL у `id`, `sender_id`, `recipient_id`, `amount`, `currency`, `status`, `created_at` (transfers) и `id`, `transfer_id`, `stage`, `status`, `changed_at` (transfer_history); `updated_at` — nullable |
| Базовый URL / servers | секция `servers` отсутствует | Порт `8080` (`docker-compose.yml`, transfer-service); `server.servlet.context-path` в `application.yml` не задан |
| Тексты ошибок `Problem.detail` | не определены (задана только схема `Problem`) | Фиксированные строки: `TransferController.KAFKA_UNAVAILABLE` (503), текст конфликта в `TransferController.duplicate` (409), `ApiExceptionHandler.INTERNAL_ERROR` (500) |
| Использование файла контракта | `transfer-service/src/main/resources/openapi/transfer-api.yaml` как статический документ | В `transfer-service/pom.xml` нет springdoc/openapi-зависимостей и генераторов; контракт не публикуется и не участвует в валидации запросов |
| Кодировка русскоязычных текстов | `summary`/`description` в yaml сохранены в искажённой кодировке (mojibake) | Аналогично искажены строковые константы `TransferController.KAFKA_UNAVAILABLE`, текст 409, `ApiExceptionHandler.INTERNAL_ERROR` |