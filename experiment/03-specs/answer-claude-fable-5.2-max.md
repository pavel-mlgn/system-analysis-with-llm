Изучаю приложенный код и собираю спецификации: HTTP API, схема БД, контракты сообщений и сверка OpenAPI с кодом.

## 1. HTTP API

**Наличие HTTP API по сервисам**

| Сервис | HTTP API | Основание в коде |
|---|---|---|
| transfer-service | 3 эндпоинта, базовый путь `/transfers` | `TransferController`; `spring-boot-starter-web` в `transfer-service/pom.xml`; порт 8080 проброшен в `docker-compose.yml`, `server.port` в `application.yml` не задан |
| limit-service | в коде не найдено | Контроллеров нет; `spring-boot-starter-web` в `limit-service/pom.xml` отсутствует |
| screening-service | в коде не найдено | Контроллеров нет; `spring-boot-starter-web` в `screening-service/pom.xml` отсутствует |

### Эндпоинт: POST /transfers

**Описание:** Создаёт перевод с идентификатором, заданным клиентом, либо возвращает уже существующий перевод с тем же `id`.
**Источник:** transfer-service, `TransferController.create()`; тело запроса — `TransferRequest`, тело ответа — `TransferEntity`.

**Параметры запроса:**

| Параметр | Источник (body/path/query) | Тип | Обязательный | Описание |
|---|---|---|---|---|
| id | body | number (целое, int64) | да (`@NotNull`) | Идентификатор перевода; задаётся клиентом |
| senderId | body | number (целое, int64) | да (`@NotNull`) | Идентификатор отправителя |
| recipientId | body | number (целое, int64) | да (`@NotNull`) | Идентификатор получателя |
| amount | body | number | да (`@NotNull`) | Сумма перевода; `@Positive`, `@Digits(integer = 13, fraction = 2)` |
| currency | body | string | нет (если не передан — `RUB`, `TransferRequest.currencyOrDefault()`) | Код валюты; `@Pattern(regexp = "[A-Z]{3}")` |

**Параметры ответа** (тело при 201 и 200 — объект `TransferEntity`):

| Поле | Тип | Nullable | Описание |
|---|---|---|---|
| id | number (целое, int64) | нет | Идентификатор перевода |
| senderId | number (целое, int64) | нет | Идентификатор отправителя |
| recipientId | number (целое, int64) | нет | Идентификатор получателя |
| amount | number | нет | Сумма перевода |
| currency | string | нет | Код валюты |
| status | string | нет | Статус перевода: `NEW`, `COMPLETED`, `CANCELLED`, `DENIED` |
| decidedBy | string | да | Сервис, решение которого определило итог: `limit`, `screening` |
| messageInfo | string | да | Пояснение к итоговому статусу (коды причин) |
| createdAt | string | нет | Время создания (`LocalDateTime`); формат сериализации в коде явно не задан |
| updatedAt | string | да (колонка `transfers.updated_at` допускает NULL; `TransferTrackingService` заполняет всегда) | Время последнего изменения (`LocalDateTime`); формат в коде явно не задан |

**Коды ответа:**

| Код | Условие |
|---|---|
| 201 | Перевода с таким `id` не было: запись создана, сообщение опубликовано в топик `transfers` |
| 200 | Перевод с таким `id` уже существует, `senderId`, `recipientId`, `amount`, `currency` совпадают; возвращается существующий перевод |
| 400 | Нарушены ограничения `TransferRequest` (`@Valid`) или тело запроса не читается; отдельного обработчика в коде проекта нет — отвечает базовый `ResponseEntityExceptionHandler`, который наследует `ApiExceptionHandler` |
| 409 | Перевод с таким `id` уже существует с другими параметрами (`TransferController.duplicate()`) |
| 503 | Сообщение не удалось опубликовать в Kafka (`TransferController.publishNew()`, `ResponseStatusException`) |
| 500 | Любое другое необработанное исключение (`ApiExceptionHandler.handleUnexpected()`) |

### Эндпоинт: GET /transfers

**Описание:** Возвращает все переводы, отсортированные по времени создания от новых к старым.
**Источник:** transfer-service, `TransferController.all()`, `TransferRepository.findAllByOrderByCreatedAtDesc()`.

**Параметры запроса:**

| Параметр | Источник (body/path/query) | Тип | Обязательный | Описание |
|---|---|---|---|---|
| — | — | — | — | Параметры в коде не найдены |

**Параметры ответа** (тело при 200 — массив объектов `TransferEntity`):

| Поле | Тип | Nullable | Описание |
|---|---|---|---|
| (корень) | array | нет | Массив переводов; может быть пустым |
| `[].id` | number (целое, int64) | нет | Идентификатор перевода |
| `[].senderId` | number (целое, int64) | нет | Идентификатор отправителя |
| `[].recipientId` | number (целое, int64) | нет | Идентификатор получателя |
| `[].amount` | number | нет | Сумма перевода |
| `[].currency` | string | нет | Код валюты |
| `[].status` | string | нет | Статус перевода: `NEW`, `COMPLETED`, `CANCELLED`, `DENIED` |
| `[].decidedBy` | string | да | Сервис, решение которого определило итог: `limit`, `screening` |
| `[].messageInfo` | string | да | Пояснение к итоговому статусу |
| `[].createdAt` | string | нет | Время создания (`LocalDateTime`); формат в коде явно не задан |
| `[].updatedAt` | string | да (колонка допускает NULL; в коде заполняется всегда) | Время последнего изменения (`LocalDateTime`) |

**Коды ответа:**

| Код | Условие |
|---|---|
| 200 | Список получен (в том числе пустой) |
| 500 | Необработанное исключение (`ApiExceptionHandler.handleUnexpected()`) |

### Эндпоинт: GET /transfers/{id}

**Описание:** Возвращает перевод по идентификатору вместе с историей его статусов.
**Источник:** transfer-service, `TransferController.getById()`; элементы истории — `TransferHistoryEntry`.

**Параметры запроса:**

| Параметр | Источник (body/path/query) | Тип | Обязательный | Описание |
|---|---|---|---|---|
| id | path | number (целое, int64) | да (path-переменная; дополнительная валидация отсутствует) | Идентификатор перевода |

**Параметры ответа** (тело при 200):

| Поле | Тип | Nullable | Описание |
|---|---|---|---|
| transfer | object | нет | Перевод (`TransferEntity`) |
| `transfer.id` | number (целое, int64) | нет | Идентификатор перевода |
| `transfer.senderId` | number (целое, int64) | нет | Идентификатор отправителя |
| `transfer.recipientId` | number (целое, int64) | нет | Идентификатор получателя |
| `transfer.amount` | number | нет | Сумма перевода |
| `transfer.currency` | string | нет | Код валюты |
| `transfer.status` | string | нет | Статус перевода: `NEW`, `COMPLETED`, `CANCELLED`, `DENIED` |
| `transfer.decidedBy` | string | да | Сервис, решение которого определило итог: `limit`, `screening` |
| `transfer.messageInfo` | string | да | Пояснение к итоговому статусу |
| `transfer.createdAt` | string | нет | Время создания (`LocalDateTime`); формат в коде явно не задан |
| `transfer.updatedAt` | string | да (колонка допускает NULL; в коде заполняется всегда) | Время последнего изменения (`LocalDateTime`) |
| history | array | нет | Записи истории; порядок — по `stage` (порядок значений `HistoryStage`), затем по `changedAt` |
| `history[].id` | number (целое, int64) | нет | Идентификатор записи истории |
| `history[].transferId` | number (целое, int64) | нет | Идентификатор перевода |
| `history[].stage` | string | нет | Этап: `NEW`, `LIMIT`, `SCREENING`, `FINAL` |
| `history[].status` | string | нет | Статус на этапе: `NEW`, `APPROVED`, `DECLINED`, `COMPLETED`, `CANCELLED`, `DENIED` |
| `history[].decidedBy` | string | да | Сервис — источник решения: `limit`, `screening` |
| `history[].messageInfo` | string | да | Код причины или пояснение |
| `history[].changedAt` | string | нет | Время записи (`LocalDateTime`); формат в коде явно не задан |

**Коды ответа:**

| Код | Условие |
|---|---|
| 200 | Перевод найден |
| 404 | Перевод с указанным `id` не найден; тело пустое (`ResponseEntity.notFound().build()`) |
| 400 | `id` не приводится к `Long`; отдельной обработки в коде проекта нет — отвечает базовый `ResponseEntityExceptionHandler`, который наследует `ApiExceptionHandler` (требует проверки) |
| 500 | Необработанное исключение (`ApiExceptionHandler.handleUnexpected()`) |

### Тело ответа при ошибках 400, 409, 503, 500 (общее для всех эндпоинтов)

**Источник:** класс Spring `ProblemDetail`; `TransferController.duplicate()`, `TransferController.publishNew()`, `ApiExceptionHandler.handleUnexpected()`.

| Поле | Тип | Nullable | Описание |
|---|---|---|---|
| type | string | требует уточнения | В коде проекта не задаётся; заполняется фреймворком |
| title | string | требует уточнения | В коде проекта не задаётся; заполняется фреймворком |
| status | number (целое) | нет | HTTP-код ответа; задаётся в коде для 409, 503, 500 |
| detail | string | требует уточнения | Текст ошибки. 409: «Перевод с id=N уже существует с другими параметрами»; 503: «Kafka недоступна, перевод сохранён — повторите запрос с тем же id»; 500: «Внутренняя ошибка сервера, повторите запрос позже»; для 400 формируется фреймворком |
| instance | string | требует уточнения | В коде проекта не задаётся; заполняется фреймворком |

## 2. СХЕМА БД

**Базы данных**

| База | СУБД | Сервис-владелец | Миграции (Liquibase) | Таблицы |
|---|---|---|---|---|
| transferdb | PostgreSQL (образ `postgres:16-alpine`, `docker-compose.yml`) | transfer-service | `transfer-service/src/main/resources/db/changelog/001-create-tables.sql` | transfers, transfer_history |
| limitdb | PostgreSQL (тот же образ) | limit-service | `limit-service/src/main/resources/db/changelog/001-create-tables.sql` | sender_usage, reservations |
| screeningdb | PostgreSQL (тот же образ) | screening-service | `screening-service/src/main/resources/db/changelog/001-create-tables.sql`, `002-insert-test-data.sql` | blocked_senders, screening_checks |

### Таблица: transfers (база: transferdb)

**Описание:** Переводы, принятые transfer-service, с их текущим статусом.
**Источник:** changeset `001-create-tables` transfer-service; сущность `TransferEntity`.

| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
|---|---|---|---|---|---|---|---|---|
| id | BIGINT | да | нет | да (PK) | да (PK) | — | неявный индекс PK | Идентификатор перевода; приходит от клиента, в БД не генерируется |
| sender_id | BIGINT | нет | нет | нет | да | — | idx_transfers_sender_id | Идентификатор отправителя |
| recipient_id | BIGINT | нет | нет | нет | да | — | — | Идентификатор получателя |
| amount | DECIMAL(15,2) | нет | нет | нет | да | — | — | Сумма перевода |
| currency | VARCHAR(10) | нет | нет | нет | да | 'RUB' | — | Код валюты |
| status | VARCHAR(20) | нет | нет | нет | да | — | idx_transfers_status | Текущий статус перевода |
| decided_by | VARCHAR(20) | нет | нет | нет | нет | — | — | Сервис, решение которого определило итоговый статус |
| message_info | TEXT | нет | нет | нет | нет | — | — | Пояснение к итоговому статусу |
| created_at | TIMESTAMP | нет | нет | нет | да | — | — | Время создания перевода |
| updated_at | TIMESTAMP | нет | нет | нет | нет | — | — | Время последнего изменения записи |

**Допустимые значения** (CHECK в DDL нет, значения задаются в коде):

| Колонка | Значения |
|---|---|
| status | `NEW` (`TransferTrackingService.createTransfer()`); `COMPLETED`, `CANCELLED`, `DENIED` (`TransferDecisionService.decide()`) |
| decided_by | NULL, `limit`, `screening` (`TransferDecisionService.decide()`) |
| message_info | NULL; `INVALID_AMOUNT`, `DAILY_LIMIT_EXCEEDED`, `MONTHLY_LIMIT_EXCEEDED`; `SENDER_BLOCKED`; `<причина limit>; <причина screening>`; `UNEXPECTED_RESULT: limit=<статус>, screening=<статус>` (`TransferDecisionService.decide()`) |

### Таблица: transfer_history (база: transferdb)

**Описание:** История статусов перевода по этапам обработки, не более одной записи на этап.
**Источник:** changeset `001-create-tables` transfer-service; сущность `TransferHistoryEntry`.

| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
|---|---|---|---|---|---|---|---|---|
| id | BIGSERIAL | да | нет | да (PK) | да (PK) | автоинкремент (BIGSERIAL) | неявный индекс PK | Идентификатор записи истории |
| transfer_id | BIGINT | нет | transfers(id) | в составе uq_transfer_history_stage | да | — | idx_transfer_history_transfer_id | Перевод, к которому относится запись |
| status | VARCHAR(20) | нет | нет | нет | да | — | — | Статус или решение, зафиксированное на этапе |
| decided_by | VARCHAR(20) | нет | нет | нет | нет | — | — | Сервис — источник решения |
| message_info | TEXT | нет | нет | нет | нет | — | — | Код причины или пояснение |
| changed_at | TIMESTAMP | нет | нет | нет | да | NOW() | — | Время записи |
| stage | VARCHAR(20) | нет | нет | в составе uq_transfer_history_stage | да | — | — | Этап обработки перевода |

**Ограничения уровня таблицы:**

| Ограничение | Колонки |
|---|---|
| uq_transfer_history_stage (UNIQUE) | transfer_id, stage |

**Допустимые значения** (CHECK в DDL нет):

| Колонка | Значения |
|---|---|
| stage | `NEW`, `LIMIT`, `SCREENING`, `FINAL` (enum `HistoryStage`, `@Enumerated(EnumType.STRING)`) |
| status | `NEW` (этап NEW); `APPROVED`, `DECLINED` (этапы LIMIT, SCREENING); `COMPLETED`, `CANCELLED`, `DENIED` (этап FINAL) |
| decided_by | NULL (этап NEW); `limit` (этап LIMIT); `screening` (этап SCREENING); NULL, `limit`, `screening` (этап FINAL) |

### Таблица: sender_usage (база: limitdb)

**Описание:** Накопленное использование дневного и месячного лимитов по отправителю.
**Источник:** changeset `001-create-tables` limit-service; сущность `SenderUsage`.

| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
|---|---|---|---|---|---|---|---|---|
| sender_id | BIGINT | да | нет | да (PK) | да (PK) | — | неявный индекс PK | Идентификатор отправителя |
| daily_used | DECIMAL(15,2) | нет | нет | нет | да | 0 | — | Сумма, учтённая в дневном лимите |
| monthly_used | DECIMAL(15,2) | нет | нет | нет | да | 0 | — | Сумма, учтённая в месячном лимите |
| last_daily_reset | DATE | нет | нет | нет | да | CURRENT_DATE | — | Дата последнего сброса дневного счётчика |
| last_monthly_reset | DATE | нет | нет | нет | да | CURRENT_DATE | — | Дата последнего сброса месячного счётчика |
| version | BIGINT | нет | нет | нет | да | 0 | — | Версия строки для оптимистической блокировки (`@Version` в `SenderUsage`) |

### Таблица: reservations (база: limitdb)

**Описание:** Решение limit-service по переводу и состояние резерва лимита.
**Источник:** changeset `001-create-tables` limit-service; сущность `Reservation`.

| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
|---|---|---|---|---|---|---|---|---|
| transfer_id | BIGINT | да | нет | да (PK) | да (PK) | — | неявный индекс PK | Идентификатор перевода (`Transfer.id` из сообщения); FK не объявлен |
| sender_id | BIGINT | нет | нет | нет | да | — | idx_reservations_sender_id | Идентификатор отправителя; FK на sender_usage не объявлен |
| amount | DECIMAL(15,2) | нет | нет | нет | да | — | — | Сумма перевода (резерва) |
| decision | VARCHAR(10) | нет | нет | нет | да | — | — | Решение по лимиту |
| reason | VARCHAR(50) | нет | нет | нет | нет | — | — | Код причины отказа |
| state | VARCHAR(20) | нет | нет | нет | да | — | — | Состояние резерва |
| reserved_on | DATE | нет | нет | нет | да | — | — | Дата принятия решения (резервирования) |
| created_at | TIMESTAMP | нет | нет | нет | да | — | — | Время создания записи |
| updated_at | TIMESTAMP | нет | нет | нет | да | — | — | Время последнего изменения записи |

**Допустимые значения** (CHECK в DDL нет):

| Колонка | Значения |
|---|---|
| decision | `APPROVED`, `DECLINED` (константы `Reservation`) |
| state | `RESERVED`, `COMMITTED`, `RELEASED`, `NONE` (константы `Reservation`) |
| reason | NULL, `INVALID_AMOUNT`, `DAILY_LIMIT_EXCEEDED`, `MONTHLY_LIMIT_EXCEEDED` (`LimitLedger.decide()`) |

### Таблица: blocked_senders (база: screeningdb)

**Описание:** Заблокированные отправители; changeset `002-insert-test-data` добавляет 5 тестовых строк (sender_id 5, 13, 21, 34, 42).
**Источник:** changeset `001-create-tables` screening-service; сущность `BlockedSender`.

| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
|---|---|---|---|---|---|---|---|---|
| id | BIGSERIAL | да | нет | да (PK) | да (PK) | автоинкремент (BIGSERIAL) | неявный индекс PK | Идентификатор записи |
| sender_id | BIGINT | нет | нет | да | да | — | idx_blocked_senders_sender_id; неявный индекс UNIQUE | Идентификатор заблокированного отправителя |
| reason | VARCHAR(255) | нет | нет | нет | да | — | — | Причина блокировки (произвольный текст) |
| blocked_at | TIMESTAMP | нет | нет | нет | да | NOW() | — | Время блокировки |

### Таблица: screening_checks (база: screeningdb)

**Описание:** Результат проверки перевода сервисом screening-service.
**Источник:** changeset `001-create-tables` screening-service; сущность `ScreeningCheck`.

| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
|---|---|---|---|---|---|---|---|---|
| transfer_id | BIGINT | да | нет | да (PK) | да (PK) | — | неявный индекс PK | Идентификатор перевода (`Transfer.id` из сообщения); FK не объявлен |
| sender_id | BIGINT | нет | нет | нет | да | — | idx_screening_checks_sender_id | Идентификатор отправителя |
| result | VARCHAR(10) | нет | нет | нет | да | — | — | Результат проверки |
| reason | VARCHAR(50) | нет | нет | нет | нет | — | — | Код причины отказа |
| checked_at | TIMESTAMP | нет | нет | нет | да | — | — | Время проверки |

**Допустимые значения** (CHECK в DDL нет):

| Колонка | Значения |
|---|---|
| result | `APPROVED`, `DECLINED` (константы `ScreeningCheck`) |
| reason | NULL, `SENDER_BLOCKED` (`ScreeningJournal.check()`) |

## 3. КОНТРАКТЫ СООБЩЕНИЙ

### Канал: transfers

| Свойство | Значение |
|---|---|
| Тип канала | Топик Apache Kafka |
| Объявление в коде | `TopicsConfig.transfersTopic()` в transfer-service, limit-service, screening-service |
| Число партиций | 3 |
| Фактор репликации | в коде не задан |
| Политика хранения / очистки | `cleanup.policy=compact` (`TopicBuilder.compact()`); срок хранения в коде не задан |
| Ключ сообщения | `Long` — идентификатор перевода (`Transfer.id`) |
| Сериализация ключа | Отправители: `LongSerializer` (KafkaTemplate), `Serdes.Long()` (Kafka Streams); получатели: `LongDeserializer` через `ErrorHandlingDeserializer` |
| Сериализация значения | JSON. Отправители: `JsonSerializer` (KafkaTemplate), `JsonSerde` (Kafka Streams, `default.value.serde`); получатели: `JsonDeserializer` через `ErrorHandlingDeserializer`, `spring.json.trusted.packages: "*"` |
| Отправители | transfer-service: `TransferController.publishNew()` — новый перевод; transfer-service: топология `DecisionTopologyConfig.decisionStream()`, `.to("transfers")` — итоговое решение |
| Получатели (группа потребителей) | limit-service: `TransferEventsListener.onEvent()` — группа `limit`; screening-service: `TransferEventsListener.onEvent()` — группа `screening`; transfer-service: `TransferEventListener.onEvent()` — группа `transfer-tracker` |
| Формат сообщения | `Transfer`, см. «Формат сообщения Transfer» |

### Канал: limit-results

| Свойство | Значение |
|---|---|
| Тип канала | Топик Apache Kafka |
| Объявление в коде | `TopicsConfig.limitResultsTopic()` в transfer-service, limit-service, screening-service |
| Число партиций | 3 |
| Фактор репликации | в коде не задан |
| Политика хранения / очистки | `cleanup.policy=compact` (`TopicBuilder.compact()`); срок хранения в коде не задан |
| Ключ сообщения | `Long` — идентификатор перевода (`Transfer.id`) |
| Сериализация ключа | Отправитель: `LongSerializer`; получатели: `Serdes.Long()` (Kafka Streams), `LongDeserializer` через `ErrorHandlingDeserializer` (слушатель) |
| Сериализация значения | JSON. Отправитель: `JsonSerializer`; получатели: `JsonSerde<Transfer>` (Kafka Streams), `JsonDeserializer` через `ErrorHandlingDeserializer` (слушатель) |
| Отправители | limit-service: `LimitService.sendAndWait()`, вызов из `LimitService.onNewTransfer()` |
| Получатели (группа потребителей) | transfer-service: топология `DecisionTopologyConfig.decisionStream()` — группа равна `application.id` Kafka Streams; `spring.kafka.streams.application-id` в коде не задан, по умолчанию Spring Boot берёт `spring.application.name` = `transfer-service` (требует уточнения); transfer-service: `TransferEventListener.onEvent()` — группа `transfer-tracker` |
| Формат сообщения | `Transfer`, см. «Формат сообщения Transfer» |

### Канал: screening-results

| Свойство | Значение |
|---|---|
| Тип канала | Топик Apache Kafka |
| Объявление в коде | `TopicsConfig.screeningResultsTopic()` в transfer-service, limit-service, screening-service |
| Число партиций | 3 |
| Фактор репликации | в коде не задан |
| Политика хранения / очистки | `cleanup.policy=compact` (`TopicBuilder.compact()`); срок хранения в коде не задан |
| Ключ сообщения | `Long` — идентификатор перевода (`Transfer.id`) |
| Сериализация ключа | Отправитель: `LongSerializer`; получатели: `Serdes.Long()` (Kafka Streams), `LongDeserializer` через `ErrorHandlingDeserializer` (слушатель) |
| Сериализация значения | JSON. Отправитель: `JsonSerializer`; получатели: `JsonSerde<Transfer>` (Kafka Streams), `JsonDeserializer` через `ErrorHandlingDeserializer` (слушатель) |
| Отправители | screening-service: `ScreeningService.sendAndWait()`, вызов из `ScreeningService.onNewTransfer()` |
| Получатели (группа потребителей) | transfer-service: топология `DecisionTopologyConfig.decisionStream()` — группа равна `application.id` Kafka Streams (явно не задан, см. limit-results); transfer-service: `TransferEventListener.onEvent()` — группа `transfer-tracker` |
| Формат сообщения | `Transfer`, см. «Формат сообщения Transfer» |

### Формат сообщения Transfer (общий для transfers, limit-results, screening-results)

| Свойство | Значение |
|---|---|
| Класс | record `com.example.common.Transfer`, модуль transfer-common |
| Кодирование | JSON-объект, имена полей совпадают с компонентами record |
| Заголовки | В коде явно не задаются; целевой тип для `JsonDeserializer` слушателей в конфигурации не указан (`spring.json.value.default.type` отсутствует) — тип берётся из заголовков, которые `JsonSerializer` добавляет по умолчанию (поведение библиотеки, требует уточнения) |
| Ограничения на null | В record не заданы; колонка Nullable ниже заполнена по коду отправителей |
| Неизменяемые поля | `id`, `senderId`, `recipientId`, `amount`, `currency`, `createdAt` переносятся из исходного сообщения без изменений (`Transfer.withDecision()`) |

| Поле | Тип | Nullable | Описание | Пример |
|---|---|---|---|---|
| id | Long | нет | Идентификатор перевода; совпадает с ключом сообщения | 1001 |
| senderId | Long | нет | Идентификатор отправителя | 7 |
| recipientId | Long | нет | Идентификатор получателя | 8 |
| amount | BigDecimal | нет | Сумма перевода | 1500.00 |
| currency | String | нет | Код валюты | "RUB" |
| status | String | нет | Статус перевода или решение сервиса; набор значений зависит от канала | "NEW" |
| decidedBy | String | да | Сервис — источник решения | "limit" |
| messageInfo | String | да | Код причины или пояснение | "DAILY_LIMIT_EXCEEDED" |
| createdAt | LocalDateTime | нет | Время создания перевода (`transfers.created_at`) | `2026-11-15T10:15:30.123456`; вид в JSON в коде явно не задан — при настройках `JsonSerializer` по умолчанию ожидается массив чисел `[2026,11,15,10,15,30,123456000]` (требует уточнения) |

**Значения полей status, decidedBy, messageInfo по каналам**

| Канал | Отправитель | status | decidedBy | messageInfo |
|---|---|---|---|---|
| transfers | `TransferController.publishNew()` | `NEW` | null | null |
| transfers | `TransferDecisionService.decide()` | `COMPLETED` | null | null |
| transfers | `TransferDecisionService.decide()` | `DENIED` | null | `<причина limit>; <причина screening>` |
| transfers | `TransferDecisionService.decide()` | `CANCELLED` | `limit` | причина из limit-results |
| transfers | `TransferDecisionService.decide()` | `CANCELLED` | `screening` | причина из screening-results |
| transfers | `TransferDecisionService.decide()` | `CANCELLED` | null | `UNEXPECTED_RESULT: limit=<статус>, screening=<статус>` |
| limit-results | `LimitService.onNewTransfer()` | `APPROVED` | `limit` | null |
| limit-results | `LimitService.onNewTransfer()` | `DECLINED` | `limit` | `INVALID_AMOUNT`, `DAILY_LIMIT_EXCEEDED` или `MONTHLY_LIMIT_EXCEEDED` |
| screening-results | `ScreeningService.onNewTransfer()` | `APPROVED` | `screening` | null |
| screening-results | `ScreeningService.onNewTransfer()` | `DECLINED` | `screening` | `SENDER_BLOCKED` |

### Канал: transfers.DLT

| Свойство | Значение |
|---|---|
| Тип канала | Топик Apache Kafka (dead letter для `transfers`) |
| Объявление в коде | `KafkaErrorHandlingConfig.transfersDlt()` в transfer-service, limit-service, screening-service |
| Число партиций | 3 |
| Фактор репликации | в коде не задан |
| Политика хранения / очистки | в коде не задана (`TopicBuilder` без `compact()`) |
| Ключ сообщения | Ключ исходной записи (идентификатор перевода) |
| Выбор партиции | Номер партиции исходной записи (`record.partition()`) |
| Сериализация ключа | `DelegatingByTypeSerializer`: `Long` → `LongSerializer`, `byte[]` → `ByteArraySerializer` |
| Сериализация значения | `byte[]` → `ByteArraySerializer`; остальные объекты → `JsonSerializer` |
| Отправители | `DeadLetterPublishingRecoverer` из `KafkaErrorHandlingConfig.kafkaErrorHandler()` в limit-service, screening-service, transfer-service — записи топика `transfers`, обработка которых слушателем завершилась ошибкой |
| Получатели (группа потребителей) | в коде не найдено |
| Формат сообщения | см. «Формат сообщения DLT» |

### Канал: limit-results.DLT

| Свойство | Значение |
|---|---|
| Тип канала | Топик Apache Kafka (dead letter для `limit-results`) |
| Объявление в коде | `KafkaErrorHandlingConfig.limitResultsDlt()` только в transfer-service |
| Число партиций | 3 |
| Фактор репликации | в коде не задан |
| Политика хранения / очистки | в коде не задана (`TopicBuilder` без `compact()`) |
| Ключ сообщения | Ключ исходной записи (идентификатор перевода) |
| Выбор партиции | Номер партиции исходной записи (`record.partition()`) |
| Сериализация ключа | `DelegatingByTypeSerializer`: `Long` → `LongSerializer`, `byte[]` → `ByteArraySerializer` |
| Сериализация значения | `byte[]` → `ByteArraySerializer`; остальные объекты → `JsonSerializer` |
| Отправители | transfer-service: `DeadLetterPublishingRecoverer` из `KafkaErrorHandlingConfig.kafkaErrorHandler()` — записи `limit-results`, не обработанные слушателем `TransferEventListener.onEvent()` |
| Получатели (группа потребителей) | в коде не найдено |
| Формат сообщения | см. «Формат сообщения DLT» |

### Канал: screening-results.DLT

| Свойство | Значение |
|---|---|
| Тип канала | Топик Apache Kafka (dead letter для `screening-results`) |
| Объявление в коде | `KafkaErrorHandlingConfig.screeningResultsDlt()` только в transfer-service |
| Число партиций | 3 |
| Фактор репликации | в коде не задан |
| Политика хранения / очистки | в коде не задана (`TopicBuilder` без `compact()`) |
| Ключ сообщения | Ключ исходной записи (идентификатор перевода) |
| Выбор партиции | Номер партиции исходной записи (`record.partition()`) |
| Сериализация ключа | `DelegatingByTypeSerializer`: `Long` → `LongSerializer`, `byte[]` → `ByteArraySerializer` |
| Сериализация значения | `byte[]` → `ByteArraySerializer`; остальные объекты → `JsonSerializer` |
| Отправители | transfer-service: `DeadLetterPublishingRecoverer` из `KafkaErrorHandlingConfig.kafkaErrorHandler()` — записи `screening-results`, не обработанные слушателем `TransferEventListener.onEvent()` |
| Получатели (группа потребителей) | в коде не найдено |
| Формат сообщения | см. «Формат сообщения DLT» |

### Формат сообщения DLT (общий для transfers.DLT, limit-results.DLT, screening-results.DLT)

| Поле | Тип | Nullable | Описание | Пример |
|---|---|---|---|---|
| (ключ) | Long или byte[] | как в исходной записи | Ключ исходной записи без изменений | 1001 |
| (значение) | Transfer (JSON) или byte[] | как в исходной записи | Значение исходной записи: объект `Transfer` либо исходные байты записи | см. «Формат сообщения Transfer» |
| (заголовки) | требует уточнения | требует уточнения | В коде проекта не задаются; состав определяется `DeadLetterPublishingRecoverer` | в коде не найдено |

## 4. РАСХОЖДЕНИЯ КОНТРАКТА API С КОДОМ

**Источник:** `transfer-service/src/main/resources/openapi/transfer-api.yaml`, сверка с `TransferController`, `TransferRequest`, `TransferEntity`, `TransferHistoryEntry`, `ApiExceptionHandler`.

| Элемент | В контракте | В коде |
|---|---|---|
| Код ответа 500 (POST /transfers, GET /transfers, GET /transfers/{id}) | Не описан ни для одной операции | `ApiExceptionHandler.handleUnexpected()` возвращает 500 с телом ProblemDetail при любом необработанном исключении |
| Код ответа 400 для GET /transfers/{id} | Описаны только 200 и 404 | `TransferController.getById()` принимает `@PathVariable Long id`; при значении, не приводимом к `Long`, ответ 400 формирует базовый `ResponseEntityExceptionHandler`, который наследует `ApiExceptionHandler` (явного обработчика в коде проекта нет — требует проверки) |
| Поле `status` в схемах `Transfer` и `HistoryEntry` | `enum` с фиксированным набором значений | Тип `String` (`TransferEntity.status`, `TransferHistoryEntry.status`); значение копируется из сообщения Kafka без проверки (`TransferTrackingService.applyFinalStatus()`, `recordServiceResult()`); формируемые в коде значения совпадают с контрактом |
| Остальное: пути и методы, схемы `TransferRequest`, `Transfer`, `TransferWithHistory`, `HistoryEntry`, `Problem`, коды 200, 201, 400, 404, 409, 503 | Соответствуют коду | Расхождений не найдено |

---

Сохранить спецификацию в Claude Docs не удалось: в режиме инкогнито документы нельзя создавать и изменять, поскольку они остаются в аккаунте после завершения чата, — это можно сделать в новом чате с выключенным инкогнито.