### 1. HTTP API

Эндпоинт: POST /transfers
Описание: Создание нового перевода и публикация события в Kafka (`transfer-service/.../controller/TransferController.java`, метод `create`).

Параметры запроса:
| Параметр | Источник (body/path/query) | Тип | Обязательный | Описание |
| :--- | :--- | :--- | :--- | :--- |
| id | body | number (integer) | да (`@NotNull`) | Идентификатор перевода |
| senderId | body | number (integer) | да (`@NotNull`) | Идентификатор отправителя |
| recipientId | body | number (integer) | да (`@NotNull`) | Идентификатор получателя |
| amount | body | number (decimal) | да (`@NotNull`, `@Positive`, `@Digits(integer = 13, fraction = 2)`) | Сумма перевода |
| currency | body | string | нет (`@Pattern(regexp = "[A-Z]{3}")`, по умолчанию `"RUB"`) | Валюта перевода (3 заглавные латинские буквы) |

Параметры ответа:
| Поле | Тип | Nullable | Описание |
| :--- | :--- | :--- | :--- |
| id | number (integer) | нет | Идентификатор перевода (при кодах 200, 201) |
| senderId | number (integer) | нет | Идентификатор отправителя (при кодах 200, 201) |
| recipientId | number (integer) | нет | Идентификатор получателя (при кодах 200, 201) |
| amount | number (decimal) | нет | Сумма перевода (при кодах 200, 201) |
| currency | string | нет | Валюта перевода (при кодах 200, 201) |
| status | string | нет | Статус перевода (при кодах 200, 201) |
| decidedBy | string | да | Сервис, принявший решение об отклонении (при кодах 200, 201) |
| messageInfo | string | да | Дополнительная информация о решении / причине отказа (при кодах 200, 201) |
| createdAt | string | нет | Дата и время создания перевода (при кодах 200, 201) |
| updatedAt | string | да | Дата и время последнего обновления перевода (при кодах 200, 201) |
| type | string | да | URI типа ошибки в формате RFC 7807 `ProblemDetail` (при кодах 400, 409, 500, 503) |
| title | string | да | Краткое наименование HTTP-статуса ошибки (при кодах 400, 409, 500, 503) |
| status | number (integer) | да | HTTP-код ошибки (при кодах 400, 409, 500, 503) |
| detail | string | да | Детальное описание ошибки (при кодах 400, 409, 500, 503) |
| instance | string | да | URI ресурса, на котором возникла ошибка (при кодах 400, 409, 500, 503) |

Коды ответа:
| Код | Условие |
| :--- | :--- |
| 201 | Новый перевод успешно создан в БД и отправлен в топик `transfers` |
| 200 | Перевод с указанным `id` и идентичными параметрами уже существует |
| 400 | Ошибка валидации тела запроса (`ResponseEntityExceptionHandler`) |
| 409 | Перевод с указанным `id` уже существует с другими параметрами (`TransferController#duplicate`) |
| 500 | Непредвиденная внутренняя ошибка сервера (`ApiExceptionHandler#handleUnexpected`) |
| 503 | Ошибка или таймаут при публикации сообщения в Kafka (`TransferController#publishNew`) |

Эндпоинт: GET /transfers
Описание: Получение списка всех переводов, отсортированных по дате создания по убыванию (`transfer-service/.../controller/TransferController.java`, метод `all`).

Параметры запроса:
| Параметр | Источник (body/path/query) | Тип | Обязательный | Описание |
| :--- | :--- | :--- | :--- | :--- |
| — | — | — | — | Параметры отсутствуют |

Параметры ответа:
| Поле | Тип | Nullable | Описание |
| :--- | :--- | :--- | :--- |
| [].id | number (integer) | нет | Идентификатор перевода |
| [].senderId | number (integer) | нет | Идентификатор отправителя |
| [].recipientId | number (integer) | нет | Идентификатор получателя |
| [].amount | number (decimal) | нет | Сумма перевода |
| [].currency | string | нет | Валюта перевода |
| [].status | string | нет | Статус перевода |
| [].decidedBy | string | да | Сервис, принявший решение |
| [].messageInfo | string | да | Дополнительная информация о решении |
| [].createdAt | string | нет | Дата и время создания перевода |
| [].updatedAt | string | да | Дата и время последнего обновления перевода |

Коды ответа:
| Код | Условие |
| :--- | :--- |
| 200 | Список переводов успешно получен |
| 500 | Непредвиденная внутренняя ошибка сервера (`ApiExceptionHandler#handleUnexpected`) |

Эндпоинт: GET /transfers/{id}
Описание: Получение данных перевода и истории изменения его статусов по идентификатору (`transfer-service/.../controller/TransferController.java`, метод `getById`).

Параметры запроса:
| Параметр | Источник (body/path/query) | Тип | Обязательный | Описание |
| :--- | :--- | :--- | :--- | :--- |
| id | path | number (integer) | да | Идентификатор перевода |

Параметры ответа:
| Поле | Тип | Nullable | Описание |
| :--- | :--- | :--- | :--- |
| transfer | object | нет | Данные перевода (`TransferEntity`) |
| transfer.id | number (integer) | нет | Идентификатор перевода |
| transfer.senderId | number (integer) | нет | Идентификатор отправителя |
| transfer.recipientId | number (integer) | нет | Идентификатор получателя |
| transfer.amount | number (decimal) | нет | Сумма перевода |
| transfer.currency | string | нет | Валюта перевода |
| transfer.status | string | нет | Текущий статус перевода |
| transfer.decidedBy | string | да | Сервис, принявший итоговое решение |
| transfer.messageInfo | string | да | Дополнительная информация о решении |
| transfer.createdAt | string | нет | Дата и время создания перевода |
| transfer.updatedAt | string | да | Дата и время последнего обновления перевода |
| history | array | нет | Список записей истории прохождения этапов (`TransferHistoryEntry`) |
| history[].id | number (integer) | нет | Идентификатор записи истории |
| history[].transferId | number (integer) | нет | Идентификатор перевода |
| history[].stage | string | нет | Этап обработки перевода (`NEW`, `LIMIT`, `SCREENING`, `FINAL`) |
| history[].status | string | нет | Статус на данном этапе |
| history[].decidedBy | string | да | Сервис, принявший решение на этапе |
| history[].messageInfo | string | да | Дополнительная информация / причина отказа на этапе |
| history[].changedAt | string | нет | Дата и время фиксации этапа |

Коды ответа:
| Код | Условие |
| :--- | :--- |
| 200 | Перевод найден |
| 400 | Некорректный формат параметра `id` в пути (`ResponseEntityExceptionHandler`) |
| 404 | Перевод с указанным `id` не найден |
| 500 | Непредвиденная внутренняя ошибка сервера (`ApiExceptionHandler#handleUnexpected`) |

### 2. СХЕМА БД

Таблица: transfers (база: transferdb)
Описание: Основная таблица переводов и их текущих статусов (`transfer-service/src/main/resources/db/changelog/001-create-tables.sql`, сущность `TransferEntity`).

| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| id | BIGINT | да | нет | да | да | нет | PK | Идентификатор перевода |
| sender_id | BIGINT | нет | нет | нет | да | нет | `idx_transfers_sender_id` | Идентификатор отправителя |
| recipient_id | BIGINT | нет | нет | нет | да | нет | нет | Идентификатор получателя |
| amount | DECIMAL(15,2) | нет | нет | нет | да | нет | нет | Сумма перевода |
| currency | VARCHAR(10) | нет | нет | нет | да | `'RUB'` | нет | Код валюты перевода |
| status | VARCHAR(20) | нет | нет | нет | да | нет | `idx_transfers_status` | Текущий статус перевода |
| decided_by | VARCHAR(20) | нет | нет | нет | нет | нет | нет | Сервис, принявший решение |
| message_info | TEXT | нет | нет | нет | нет | нет | нет | Причина отказа или пояснение к решению |
| created_at | TIMESTAMP | нет | нет | нет | да | нет | нет | Дата и время создания записи |
| updated_at | TIMESTAMP | нет | нет | нет | нет | нет | нет | Дата и время последнего обновления записи |

Допустимые значения (если поле содержит фиксированный набор значений):
| Колонка | Значения |
| :--- | :--- |
| status | `NEW`, `COMPLETED`, `CANCELLED`, `DENIED` |
| decided_by | `limit`, `screening`, `NULL` |

Таблица: transfer_history (база: transferdb)
Описание: История прохождения переводом этапов проверки (`transfer-service/src/main/resources/db/changelog/001-create-tables.sql`, сущность `TransferHistoryEntry`).

| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| id | BIGSERIAL | да | нет | да | да | auto-increment | PK | Идентификатор записи истории |
| transfer_id | BIGINT | нет | `transfers(id)` | нет | да | нет | `idx_transfer_history_transfer_id`, `uq_transfer_history_stage` | Идентификатор перевода |
| status | VARCHAR(20) | нет | нет | нет | да | нет | нет | Статус перевода на этапе |
| decided_by | VARCHAR(20) | нет | нет | нет | нет | нет | нет | Сервис, вынесший решение на этапе |
| message_info | TEXT | нет | нет | нет | нет | нет | нет | Пояснение / код причины отказа |
| changed_at | TIMESTAMP | нет | нет | нет | да | `NOW()` | нет | Дата и время записи этапа |
| stage | VARCHAR(20) | нет | нет | нет | да | нет | `uq_transfer_history_stage` | Наименование этапа обработки |

Ограничения уровня таблицы (составные ключи, составные UNIQUE), если есть:
| Ограничение | Колонки |
| :--- | :--- |
| UNIQUE `uq_transfer_history_stage` | `transfer_id, stage` |

Допустимые значения (если поле содержит фиксированный набор значений):
| Колонка | Значения |
| :--- | :--- |
| stage | `NEW`, `LIMIT`, `SCREENING`, `FINAL` |
| status | `NEW`, `APPROVED`, `DECLINED`, `COMPLETED`, `CANCELLED`, `DENIED` |
| decided_by | `limit`, `screening`, `NULL` |

Таблица: sender_usage (база: limitdb)
Описание: Учёт использованных дневных и месячных лимитов по каждому отправителю (`limit-service/src/main/resources/db/changelog/001-create-tables.sql`, сущность `SenderUsage`).

| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| sender_id | BIGINT | да | нет | да | да | нет | PK | Идентификатор отправителя |
| daily_used | DECIMAL(15,2) | нет | нет | нет | да | `0` | нет | Израсходованный дневной лимит |
| monthly_used | DECIMAL(15,2) | нет | нет | нет | да | `0` | нет | Израсходованный месячный лимит |
| last_daily_reset | DATE | нет | нет | нет | да | `CURRENT_DATE` | нет | Дата последнего сброса дневного лимита |
| last_monthly_reset | DATE | нет | нет | нет | да | `CURRENT_DATE` | нет | Дата последнего сброса месячного лимита |
| version | BIGINT | нет | нет | нет | да | `0` | нет | Версия записи для оптимистической блокировки (`@Version`) |

Таблица: reservations (база: limitdb)
Описание: Резервирования лимитов и решения сервиса лимитов по переводам (`limit-service/src/main/resources/db/changelog/001-create-tables.sql`, сущность `Reservation`).

| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| transfer_id | BIGINT | да | нет | да | да | нет | PK | Идентификатор перевода |
| sender_id | BIGINT | нет | нет | нет | да | нет | `idx_reservations_sender_id` | Идентификатор отправителя |
| amount | DECIMAL(15,2) | нет | нет | нет | да | нет | нет | Сумма резервирования |
| decision | VARCHAR(10) | нет | нет | нет | да | нет | нет | Решение проверки лимитов |
| reason | VARCHAR(50) | нет | нет | нет | нет | нет | нет | Код причины отклонения |
| state | VARCHAR(20) | нет | нет | нет | да | нет | нет | Состояние резерва |
| reserved_on | DATE | нет | нет | нет | да | нет | нет | Дата создания резерва |
| created_at | TIMESTAMP | нет | нет | нет | да | нет | нет | Дата и время создания записи |
| updated_at | TIMESTAMP | нет | нет | нет | да | нет | нет | Дата и время обновления записи |

Допустимые значения (если поле содержит фиксированный набор значений):
| Колонка | Значения |
| :--- | :--- |
| decision | `APPROVED`, `DECLINED` |
| reason | `INVALID_AMOUNT`, `DAILY_LIMIT_EXCEEDED`, `MONTHLY_LIMIT_EXCEEDED`, `NULL` |
| state | `RESERVED`, `COMMITTED`, `RELEASED`, `NONE` |

Таблица: blocked_senders (база: screeningdb)
Описание: Справочник заблокированных отправителей (`screening-service/src/main/resources/db/changelog/001-create-tables.sql`, сущность `BlockedSender`).

| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| id | BIGSERIAL | да | нет | да | да | auto-increment | PK | Внутренний идентификатор записи |
| sender_id | BIGINT | нет | нет | да | да | нет | `idx_blocked_senders_sender_id`, UNIQUE | Идентификатор заблокированного отправителя |
| reason | VARCHAR(255) | нет | нет | нет | да | нет | нет | Причина блокировки отправителя |
| blocked_at | TIMESTAMP | нет | нет | нет | да | `NOW()` | нет | Дата и время блокировки |

Таблица: screening_checks (база: screeningdb)
Описание: Журнал результатов проверки переводов сервисом скрининга (`screening-service/src/main/resources/db/changelog/001-create-tables.sql`, сущность `ScreeningCheck`).

| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| transfer_id | BIGINT | да | нет | да | да | нет | PK | Идентификатор перевода |
| sender_id | BIGINT | нет | нет | нет | да | нет | `idx_screening_checks_sender_id` | Идентификатор отправителя |
| result | VARCHAR(10) | нет | нет | нет | да | нет | нет | Результат скрининга |
| reason | VARCHAR(50) | нет | нет | нет | нет | нет | нет | Код причины отказа |
| checked_at | TIMESTAMP | нет | нет | нет | да | нет | нет | Дата и время проведения проверки |

Допустимые значения (если поле содержит фиксированный набор значений):
| Колонка | Значения |
| :--- | :--- |
| result | `APPROVED`, `DECLINED` |
| reason | `SENDER_BLOCKED`, `NULL` |

### 3. КОНТРАКТЫ СООБЩЕНИЙ

Канал: transfers
| Свойство | Значение |
| :--- | :--- |
| Тип канала | Топик Kafka |
| Число партиций | 3 (`TopicsConfig#transfersTopic`) |
| Политика хранения / очистки | `compact` |
| Ключ сообщения | `Long` (`id` перевода) |
| Сериализация ключа | `LongSerializer` / `ErrorHandlingDeserializer` (`LongDeserializer`), `Serdes.Long()` |
| Сериализация значения | `JsonSerializer` / `ErrorHandlingDeserializer` (`JsonDeserializer`), `JsonSerde<Transfer>` |
| Отправители | `transfer-service` (`TransferController#publishNew`, `DecisionTopologyConfig#decisionStream`) |
| Получатели (группа потребителей) | `limit-service` (`groupId = "limit"`), `screening-service` (`groupId = "screening"`), `transfer-service` (`groupId = "transfer-tracker"`) |

Формат сообщения (`com.example.common.Transfer` в `transfer-common/.../Transfer.java`):
| Поле | Тип | Nullable | Описание | Пример |
| :--- | :--- | :--- | :--- | :--- |
| id | Long | нет | Идентификатор перевода | `1001` |
| senderId | Long | нет | Идентификатор отправителя | `5` |
| recipientId | Long | нет | Идентификатор получателя | `10` |
| amount | BigDecimal | нет | Сумма перевода | `15000.00` |
| currency | String | нет | Валюта перевода | `"RUB"` |
| status | String | нет | Статус перевода / решения (`NEW`, `APPROVED`, `DECLINED`, `COMPLETED`, `CANCELLED`, `DENIED`) | `"NEW"` |
| decidedBy | String | да | Сервис, принявший решение (`"limit"`, `"screening"`, `null`) | `"limit"` |
| messageInfo | String | да | Причина отказа или пояснение к решению | `"DAILY_LIMIT_EXCEEDED"` |
| createdAt | LocalDateTime | нет | Дата и время создания перевода | `"2025-05-01T10:15:30"` |

Канал: limit-results
| Свойство | Значение |
| :--- | :--- |
| Тип канала | Топик Kafka |
| Число партиций | 3 (`TopicsConfig#limitResultsTopic`) |
| Политика хранения / очистки | `compact` |
| Ключ сообщения | `Long` (`id` перевода) |
| Сериализация ключа | `LongSerializer` / `ErrorHandlingDeserializer` (`LongDeserializer`), `Serdes.Long()` |
| Сериализация значения | `JsonSerializer` / `ErrorHandlingDeserializer` (`JsonDeserializer`), `JsonSerde<Transfer>` (`com.example.common.Transfer`, см. формат топика `transfers`) |
| Отправители | `limit-service` (`LimitService#sendAndWait`) |
| Получатели (группа потребителей) | `transfer-service` (`TransferEventListener#onEvent`, `groupId = "transfer-tracker"`), `transfer-service` (Kafka Streams `DecisionTopologyConfig#decisionStream`, `application.id = "transfer-service"`) |

Канал: screening-results
| Свойство | Значение |
| :--- | :--- |
| Тип канала | Топик Kafka |
| Число партиций | 3 (`TopicsConfig#screeningResultsTopic`) |
| Политика хранения / очистки | `compact` |
| Ключ сообщения | `Long` (`id` перевода) |
| Сериализация ключа | `LongSerializer` / `ErrorHandlingDeserializer` (`LongDeserializer`), `Serdes.Long()` |
| Сериализация значения | `JsonSerializer` / `ErrorHandlingDeserializer` (`JsonDeserializer`), `JsonSerde<Transfer>` (`com.example.common.Transfer`, см. формат топика `transfers`) |
| Отправители | `screening-service` (`ScreeningService#sendAndWait`) |
| Получатели (группа потребителей) | `transfer-service` (`TransferEventListener#onEvent`, `groupId = "transfer-tracker"`), `transfer-service` (Kafka Streams `DecisionTopologyConfig#decisionStream`, `application.id = "transfer-service"`) |

Канал: transfers.DLT
| Свойство | Значение |
| :--- | :--- |
| Тип канала | Топик Kafka (Dead Letter Topic) |
| Число партиций | 3 (`KafkaErrorHandlingConfig#transfersDlt`) |
| Политика хранения / очистки | По умолчанию (в коде явно не задана) |
| Ключ сообщения | `Long` или `byte[]` |
| Сериализация ключа | `DelegatingByTypeSerializer` (`LongSerializer` для `Long`, `ByteArraySerializer` для `byte[]`) |
| Сериализация значения | `JsonSerializer` (для десериализованного `com.example.common.Transfer`, см. формат топика `transfers`) или `ByteArraySerializer` (для `byte[]` при ошибке десериализации) |
| Отправители | `transfer-service`, `limit-service`, `screening-service` (`DeadLetterPublishingRecoverer` в `KafkaErrorHandlingConfig`) |
| Получатели (группа потребителей) | В коде не найдено |

Канал: limit-results.DLT
| Свойство | Значение |
| :--- | :--- |
| Тип канала | Топик Kafka (Dead Letter Topic) |
| Число партиций | 3 (`transfer-service/.../config/KafkaErrorHandlingConfig#limitResultsDlt`) |
| Политика хранения / очистки | По умолчанию (в коде явно не задана) |
| Ключ сообщения | `Long` или `byte[]` |
| Сериализация ключа | `DelegatingByTypeSerializer` (`LongSerializer` для `Long`, `ByteArraySerializer` для `byte[]`) |
| Сериализация значения | `JsonSerializer` (для `com.example.common.Transfer`, см. формат топика `transfers`) или `ByteArraySerializer` (для `byte[]`) |
| Отправители | `transfer-service` (`DeadLetterPublishingRecoverer` в `KafkaErrorHandlingConfig`) |
| Получатели (группа потребителей) | В коде не найдено |

Канал: screening-results.DLT
| Свойство | Значение |
| :--- | :--- |
| Тип канала | Топик Kafka (Dead Letter Topic) |
| Число партиций | 3 (`transfer-service/.../config/KafkaErrorHandlingConfig#screeningResultsDlt`) |
| Политика хранения / очистки | По умолчанию (в коде явно не задана) |
| Ключ сообщения | `Long` или `byte[]` |
| Сериализация ключа | `DelegatingByTypeSerializer` (`LongSerializer` для `Long`, `ByteArraySerializer` для `byte[]`) |
| Сериализация значения | `JsonSerializer` (для `com.example.common.Transfer`, см. формат топика `transfers`) или `ByteArraySerializer` (для `byte[]`) |
| Отправители | `transfer-service` (`DeadLetterPublishingRecoverer` в `KafkaErrorHandlingConfig`) |
| Получатели (группа потребителей) | В коде не найдено |

### 4. РАСХОЖДЕНИЯ КОНТРАКТА API С КОДОМ

| Элемент | В контракте (`transfer-api.yaml`) | В коде (`transfer-service`) |
| :--- | :--- | :--- |
| Код ответа `500` для всех эндпоинтов (`POST /transfers`, `GET /transfers`, `GET /transfers/{id}`) | Не описан | Обработчик `ApiExceptionHandler#handleUnexpected` перехватывает `Exception` и возвращает HTTP `500 Internal Server Error` с телом `ProblemDetail` |
| Код ответа `400` для `GET /transfers/{id}` | Не описан | При передаче нечислового `id` в пути `ResponseEntityExceptionHandler` возвращает HTTP `400 Bad Request` с телом `ProblemDetail` |
| Тип поля `status` в схеме `Transfer` и `HistoryEntry` | Строго ограниченный `enum` (`TransferStatus` и встроенный `enum` в `HistoryEntry`) | В `TransferEntity` и `TransferHistoryEntry` поле `status` имеет тип `String` без enum-ограничения (в `TransferDecisionService#decide` также возможна запись статуса `CANCELLED` с произвольным `UNEXPECTED_RESULT`) |
| Поле `updatedAt` в схеме `Transfer` | Не отмечено как `nullable: true` | В таблице `transfers` колонка `updated_at` допускает `NULL`, в `TransferEntity` поле может быть `null` |
| Обязательность полей (`required`) в схемах `Transfer`, `TransferWithHistory`, `HistoryEntry`, `Problem` | Секция `required` отсутствует во всех схемах ответов | Поля `id`, `senderId`, `recipientId`, `amount`, `currency`, `status`, `createdAt` в `TransferEntity`, поля `transfer` и `history` в `TransferController#getById`, а также `id`, `transferId`, `stage`, `status`, `changedAt` в `TransferHistoryEntry` всегда заполняются и не могут быть `null` |
| Формат полей дат (`createdAt`, `updatedAt`, `changedAt`) | Указан только `type: string` без `format: date-time` | В `TransferEntity` и `TransferHistoryEntry` используется тип `java.time.LocalDateTime`, сериализуемый в формат ISO-8601 date-time |