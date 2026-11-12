### 1. HTTP API

Эндпоинт: POST /transfers
Описание: Создание денежного перевода.

Параметры запроса:
| Параметр | Источник (body/path/query) | Тип | Обязательный | Описание |
| :--- | :--- | :--- | :--- | :--- |
| `id` | body | `integer` | да | Идентификатор перевода (`@NotNull`, `transfer-service/.../TransferRequest.java`, record `TransferRequest`) |
| `senderId` | body | `integer` | да | Идентификатор отправителя (`@NotNull`, `TransferRequest`) |
| `recipientId` | body | `integer` | да | Идентификатор получателя (`@NotNull`, `TransferRequest`) |
| `amount` | body | `number` | да | Сумма перевода (`@NotNull`, `@Positive`, `@Digits(integer = 13, fraction = 2)`, `TransferRequest`) |
| `currency` | body | `string` | нет | Трехбуквенный код валюты (`@Pattern(regexp = "[A-Z]{3}")`, при `null` используется `"RUB"`, `TransferRequest.currencyOrDefault`) |

Параметры ответа:
| Поле | Тип | Nullable | Описание |
| :--- | :--- | :--- | :--- |
| `id` | `integer` | нет | Идентификатор перевода (`transfer-service/.../TransferEntity.java`, класс `TransferEntity`) |
| `senderId` | `integer` | нет | Идентификатор отправителя |
| `recipientId` | `integer` | нет | Идентификатор получателя |
| `amount` | `number` | нет | Сумма перевода |
| `currency` | `string` | нет | Код валюты перевода |
| `status` | `string` | нет | Статус перевода |
| `decidedBy` | `string` | да | Имя сервиса, принявшего решение |
| `messageInfo` | `string` | да | Дополнительная информация / причина решения |
| `createdAt` | `string` | нет | Дата и время создания перевода (ISO-8601) |
| `updatedAt` | `string` | да | Дата и время последнего обновления перевода (ISO-8601; в БД допускается `NULL`, в коде при сохранении заполняется) |

Коды ответа:
| Код | Условие |
| :--- | :--- |
| `201` | Новый перевод сохранен и опубликован в Kafka (`transfer-service/.../TransferController.java`, метод `create`) |
| `200` | Перевод с указанным `id` и идентичными параметрами уже существует (`TransferController.duplicate`) |
| `400` | Некорректный JSON или ошибка валидации входных параметров запроса (`ResponseEntityExceptionHandler`, возвращается `ProblemDetail`) |
| `409` | Перевод с указанным `id` уже существует с другими параметрами (`TransferController.duplicate`, возвращается `ProblemDetail`) |
| `503` | Ошибка или таймаут при публикации сообщения в Kafka (`TransferController.publishNew`, возвращается `ProblemDetail`) |
| `500` | Необработанное исключение на сервере (`transfer-service/.../ApiExceptionHandler.java`, метод `handleUnexpected`, возвращается `ProblemDetail`) |

Эндпоинт: GET /transfers
Описание: Получение списка всех переводов.

Параметры запроса:
| Параметр | Источник (body/path/query) | Тип | Обязательный | Описание |
| :--- | :--- | :--- | :--- | :--- |
| — | — | — | — | Параметры отсутствуют (`transfer-service/.../TransferController.java`, метод `all`) |

Параметры ответа:
| Поле | Тип | Nullable | Описание |
| :--- | :--- | :--- | :--- |
| `[]` | `array` | нет | Массив объектов переводов (`TransferEntity`) |
| `[].id` | `integer` | нет | Идентификатор перевода |
| `[].senderId` | `integer` | нет | Идентификатор отправителя |
| `[].recipientId` | `integer` | нет | Идентификатор получателя |
| `[].amount` | `number` | нет | Сумма перевода |
| `[].currency` | `string` | нет | Код валюты перевода |
| `[].status` | `string` | нет | Статус перевода |
| `[].decidedBy` | `string` | да | Имя сервиса, принявшего решение |
| `[].messageInfo` | `string` | да | Дополнительная информация / причина решения |
| `[].createdAt` | `string` | нет | Дата и время создания перевода (ISO-8601) |
| `[].updatedAt` | `string` | да | Дата и время последнего обновления перевода (ISO-8601) |

Коды ответа:
| Код | Условие |
| :--- | :--- |
| `200` | Список переводов успешно получен (`TransferController.all`) |
| `500` | Необработанное исключение на сервере (`ApiExceptionHandler.handleUnexpected`, возвращается `ProblemDetail`) |

Эндпоинт: GET /transfers/{id}
Описание: Получение перевода и истории его этапов по идентификатору.

Параметры запроса:
| Параметр | Источник (body/path/query) | Тип | Обязательный | Описание |
| :--- | :--- | :--- | :--- | :--- |
| `id` | path | `integer` | да | Идентификатор перевода (`@PathVariable Long id`, `transfer-service/.../TransferController.java`, метод `getById`) |

Параметры ответа:
| Поле | Тип | Nullable | Описание |
| :--- | :--- | :--- | :--- |
| `transfer` | `object` | нет | Данные перевода (`TransferEntity`) |
| `transfer.id` | `integer` | нет | Идентификатор перевода |
| `transfer.senderId` | `integer` | нет | Идентификатор отправителя |
| `transfer.recipientId` | `integer` | нет | Идентификатор получателя |
| `transfer.amount` | `number` | нет | Сумма перевода |
| `transfer.currency` | `string` | нет | Код валюты перевода |
| `transfer.status` | `string` | нет | Текущий статус перевода |
| `transfer.decidedBy` | `string` | да | Имя сервиса, принявшего решение |
| `transfer.messageInfo` | `string` | да | Дополнительная информация / причина решения |
| `transfer.createdAt` | `string` | нет | Дата и время создания перевода (ISO-8601) |
| `transfer.updatedAt` | `string` | да | Дата и время последнего обновления перевода (ISO-8601) |
| `history` | `array` | нет | Список записей истории по переводу (`transfer-service/.../TransferHistoryEntry.java`, класс `TransferHistoryEntry`) |
| `history[].id` | `integer` | нет | Идентификатор записи истории |
| `history[].transferId` | `integer` | нет | Идентификатор перевода |
| `history[].stage` | `string` | нет | Этап обработки (`NEW`, `LIMIT`, `SCREENING`, `FINAL`) |
| `history[].status` | `string` | нет | Статус на данном этапе |
| `history[].decidedBy` | `string` | да | Имя сервиса, вынесшего решение на этапе |
| `history[].messageInfo` | `string` | да | Дополнительная информация / причина решения на этапе |
| `history[].changedAt` | `string` | нет | Дата и время фиксации этапа (ISO-8601) |

Коды ответа:
| Код | Условие |
| :--- | :--- |
| `200` | Перевод с указанным `id` найден (`TransferController.getById`) |
| `400` | Значение параметра `id` не приводится к целому числу `Long` (`ResponseEntityExceptionHandler`, возвращается `ProblemDetail`) |
| `404` | Перевод с указанным `id` не найден (`TransferController.getById`, тело ответа отсутствует) |
| `500` | Необработанное исключение на сервере (`ApiExceptionHandler.handleUnexpected`, возвращается `ProblemDetail`) |

### 2. СХЕМА БД

Таблица: transfers (база: transferdb)
Описание: Хранит основные реквизиты и актуальный статус денежных переводов (`transfer-service/src/main/resources/db/changelog/001-create-tables.sql`).

| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | да | нет | да | да | — | PK | Идентификатор перевода |
| `sender_id` | `BIGINT` | нет | нет | нет | да | — | `idx_transfers_sender_id` | Идентификатор отправителя |
| `recipient_id` | `BIGINT` | нет | нет | нет | да | — | нет | Идентификатор получателя |
| `amount` | `DECIMAL(15,2)` | нет | нет | нет | да | — | нет | Сумма перевода |
| `currency` | `VARCHAR(10)` | нет | нет | нет | да | `'RUB'` | нет | Код валюты перевода |
| `status` | `VARCHAR(20)` | нет | нет | нет | да | — | `idx_transfers_status` | Текущий статус перевода |
| `decided_by` | `VARCHAR(20)` | нет | нет | нет | нет | — | нет | Сервис, принявший решение по переводу |
| `message_info` | `TEXT` | нет | нет | нет | нет | — | нет | Дополнительные сведения или причина решения |
| `created_at` | `TIMESTAMP` | нет | нет | нет | да | — | нет | Дата и время создания записи |
| `updated_at` | `TIMESTAMP` | нет | нет | нет | нет | — | нет | Дата и время последнего обновления записи |

Допустимые значения:
| Колонка | Значения |
| :--- | :--- |
| `status` | `NEW`, `COMPLETED`, `CANCELLED`, `DENIED` (`TransferTrackingService`, `TransferDecisionService`) |
| `decided_by` | `limit`, `screening` (или `NULL`; `TransferDecisionService.decide`) |

Таблица: transfer_history (база: transferdb)
Описание: Хранит историю прохождения этапов обработки по каждому переводу (`transfer-service/src/main/resources/db/changelog/001-create-tables.sql`).

| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `id` | `BIGSERIAL` | да | нет | да | да | автоинкремент | PK | Идентификатор записи истории |
| `transfer_id` | `BIGINT` | нет | `transfers(id)` | нет | да | — | `idx_transfer_history_transfer_id`, `uq_transfer_history_stage` | Идентификатор перевода |
| `status` | `VARCHAR(20)` | нет | нет | нет | да | — | нет | Статус перевода или проверки на этапе |
| `decided_by` | `VARCHAR(20)` | нет | нет | нет | нет | — | нет | Сервис, принявший решение на этапе |
| `message_info` | `TEXT` | нет | нет | нет | нет | — | нет | Дополнительная информация / причина решения |
| `changed_at` | `TIMESTAMP` | нет | нет | нет | да | `NOW()` | нет | Дата и время записи этапа |
| `stage` | `VARCHAR(20)` | нет | нет | нет | да | — | `uq_transfer_history_stage` | Наименование этапа обработки |

Ограничения уровня таблицы:
| Ограничение | Колонки |
| :--- | :--- |
| `uq_transfer_history_stage` (`UNIQUE`) | `transfer_id, stage` |

Допустимые значения:
| Колонка | Значения |
| :--- | :--- |
| `stage` | `NEW`, `LIMIT`, `SCREENING`, `FINAL` (`transfer-service/.../HistoryStage.java`, enum `HistoryStage`) |
| `status` | `NEW`, `APPROVED`, `DECLINED`, `COMPLETED`, `CANCELLED`, `DENIED` (`TransferTrackingService`, `Reservation`, `ScreeningCheck`, `TransferDecisionService`) |
| `decided_by` | `limit`, `screening` (или `NULL`; `LimitService`, `ScreeningService`, `TransferDecisionService`) |

Таблица: sender_usage (база: limitdb)
Описание: Хранит текущие накопленные суммы переводов отправителя за день и за месяц (`limit-service/src/main/resources/db/changelog/001-create-tables.sql`).

| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `sender_id` | `BIGINT` | да | нет | да | да | — | PK | Идентификатор отправителя |
| `daily_used` | `DECIMAL(15,2)` | нет | нет | нет | да | `0` | нет | Использованная сумма дневного лимита |
| `monthly_used` | `DECIMAL(15,2)` | нет | нет | нет | да | `0` | нет | Использованная сумма месячного лимита |
| `last_daily_reset` | `DATE` | нет | нет | нет | да | `CURRENT_DATE` | нет | Дата последнего сброса дневного счетчика |
| `last_monthly_reset` | `DATE` | нет | нет | нет | да | `CURRENT_DATE` | нет | Дата последнего сброса месячного счетчика |
| `version` | `BIGINT` | нет | нет | нет | да | `0` | нет | Версия записи для оптимистической блокировки (`@Version`, `SenderUsage`) |

Таблица: reservations (база: limitdb)
Описание: Хранит решения по проверке лимитов и состояния резервов по переводам (`limit-service/src/main/resources/db/changelog/001-create-tables.sql`).

| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `transfer_id` | `BIGINT` | да | нет | да | да | — | PK | Идентификатор перевода |
| `sender_id` | `BIGINT` | нет | нет | нет | да | — | `idx_reservations_sender_id` | Идентификатор отправителя |
| `amount` | `DECIMAL(15,2)` | нет | нет | нет | да | — | нет | Сумма резервирования |
| `decision` | `VARCHAR(10)` | нет | нет | нет | да | — | нет | Результат проверки лимитов |
| `reason` | `VARCHAR(50)` | нет | нет | нет | нет | — | нет | Код причины отклонения |
| `state` | `VARCHAR(20)` | нет | нет | нет | да | — | нет | Состояние резерва |
| `reserved_on` | `DATE` | нет | нет | нет | да | — | нет | Дата создания резерва |
| `created_at` | `TIMESTAMP` | нет | нет | нет | да | — | нет | Дата и время создания записи |
| `updated_at` | `TIMESTAMP` | нет | нет | нет | да | — | нет | Дата и время последнего обновления записи |

Допустимые значения:
| Колонка | Значения |
| :--- | :--- |
| `decision` | `APPROVED`, `DECLINED` (`limit-service/.../Reservation.java`, класс `Reservation`) |
| `state` | `RESERVED`, `COMMITTED`, `RELEASED`, `NONE` (`Reservation`) |
| `reason` | `INVALID_AMOUNT`, `DAILY_LIMIT_EXCEEDED`, `MONTHLY_LIMIT_EXCEEDED` (или `NULL`; `limit-service/.../LimitLedger.java`, метод `decide`) |

Таблица: blocked_senders (база: screeningdb)
Описание: Хранит перечень заблокированных отправителей (`screening-service/src/main/resources/db/changelog/001-create-tables.sql`).

| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `id` | `BIGSERIAL` | да | нет | да | да | автоинкремент | PK | Внутренний идентификатор записи |
| `sender_id` | `BIGINT` | нет | нет | да | да | — | `UNIQUE`, `idx_blocked_senders_sender_id` | Идентификатор заблокированного отправителя |
| `reason` | `VARCHAR(255)` | нет | нет | нет | да | — | нет | Причина блокировки |
| `blocked_at` | `TIMESTAMP` | нет | нет | нет | да | `NOW()` | нет | Дата и время блокировки |

Таблица: screening_checks (база: screeningdb)
Описание: Хранит результаты скрининг-проверок по переводам (`screening-service/src/main/resources/db/changelog/001-create-tables.sql`).

| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `transfer_id` | `BIGINT` | да | нет | да | да | — | PK | Идентификатор перевода |
| `sender_id` | `BIGINT` | нет | нет | нет | да | — | `idx_screening_checks_sender_id` | Идентификатор отправителя |
| `result` | `VARCHAR(10)` | нет | нет | нет | да | — | нет | Результат скрининг-проверки |
| `reason` | `VARCHAR(50)` | нет | нет | нет | нет | — | нет | Код причины отклонения |
| `checked_at` | `TIMESTAMP` | нет | нет | нет | да | — | нет | Дата и время выполнения проверки |

Допустимые значения:
| Колонка | Значения |
| :--- | :--- |
| `result` | `APPROVED`, `DECLINED` (`screening-service/.../ScreeningCheck.java`, класс `ScreeningCheck`) |
| `reason` | `SENDER_BLOCKED` (или `NULL`; `screening-service/.../ScreeningJournal.java`, метод `check`) |

### 3. КОНТРАКТЫ СООБЩЕНИЙ

Канал: transfers
| Свойство | Значение |
| :--- | :--- |
| Тип канала | Топик Kafka |
| Число партиций | `3` (`TopicsConfig.transfersTopic` в `transfer-service`, `limit-service`, `screening-service`) |
| Политика хранения / очистки | `compact` (`TopicBuilder.compact()`) |
| Ключ сообщения | `Long` — идентификатор перевода (`transfer.id()`) |
| Сериализация ключа | Producer: `LongSerializer`; Kafka Streams: `Serdes$LongSerde`; Consumer: `ErrorHandlingDeserializer` -> `LongDeserializer` |
| Сериализация значения | Producer: `JsonSerializer`; Kafka Streams: `JsonSerde<Transfer>`; Consumer: `ErrorHandlingDeserializer` -> `JsonDeserializer` |
| Отправители | 1) `transfer-service` (`transfer-service/.../TransferController.java`, метод `publishNew`); 2) `transfer-service` Kafka Streams (`transfer-service/.../DecisionTopologyConfig.java`, метод `decisionStream`) |
| Получатели (группа потребителей) | 1) `limit-service` (`limit-service/.../TransferEventsListener.java`, метод `onEvent`, `groupId = "limit"`); 2) `screening-service` (`screening-service/.../TransferEventsListener.java`, метод `onEvent`, `groupId = "screening"`); 3) `transfer-service` (`transfer-service/.../TransferEventListener.java`, метод `onEvent`, `groupId = "transfer-tracker"`) |

Формат сообщения (`com.example.common.Transfer`, файл `transfer-common/src/main/java/com/example/common/Transfer.java`):
| Поле | Тип | Nullable | Описание | Пример |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `Long` | нет | Идентификатор перевода | `1001` |
| `senderId` | `Long` | нет | Идентификатор отправителя | `5` |
| `recipientId` | `Long` | нет | Идентификатор получателя | `10` |
| `amount` | `BigDecimal` | да (в record валидации нет; штатно не `null`) | Сумма перевода | `1500.00` |
| `currency` | `String` | нет | Трехбуквенный код валюты | `"RUB"` |
| `status` | `String` | нет | Статус перевода или этапа (`NEW`, `APPROVED`, `DECLINED`, `COMPLETED`, `CANCELLED`, `DENIED`) | `"NEW"` |
| `decidedBy` | `String` | да | Имя сервиса, принявшего решение (`"limit"`, `"screening"` или `null`) | `"limit"` |
| `messageInfo` | `String` | да | Причина решения или дополнительная информация | `"DAILY_LIMIT_EXCEEDED"` |
| `createdAt` | `LocalDateTime` | нет | Дата и время создания перевода | `"2025-01-15T12:30:00"` |

Канал: limit-results
| Свойство | Значение |
| :--- | :--- |
| Тип канала | Топик Kafka |
| Число партиций | `3` (`TopicsConfig.limitResultsTopic` в `transfer-service`, `limit-service`, `screening-service`) |
| Политика хранения / очистки | `compact` (`TopicBuilder.compact()`) |
| Ключ сообщения | `Long` — идентификатор перевода (`transfer.id()`) |
| Сериализация ключа | Producer: `LongSerializer`; Consumer: `ErrorHandlingDeserializer` -> `LongDeserializer`; Kafka Streams: `Serdes.Long()` |
| Сериализация значения | Producer: `JsonSerializer`; Consumer: `ErrorHandlingDeserializer` -> `JsonDeserializer`; Kafka Streams: `JsonSerde<Transfer>` |
| Отправители | `limit-service` (`limit-service/.../LimitService.java`, метод `sendAndWait`) |
| Получатели (группа потребителей) | 1) `transfer-service` (`transfer-service/.../TransferEventListener.java`, метод `onEvent`, `groupId = "transfer-tracker"`); 2) `transfer-service` Kafka Streams (`transfer-service/.../DecisionTopologyConfig.java`, метод `decisionStream`, `application.id` по умолчанию из `spring.application.name` = `"transfer-service"`) |
| Формат сообщения | `com.example.common.Transfer` (см. таблицу «Формат сообщения» канала `transfers`) |

Канал: screening-results
| Свойство | Значение |
| :--- | :--- |
| Тип канала | Топик Kafka |
| Число партиций | `3` (`TopicsConfig.screeningResultsTopic` в `transfer-service`, `limit-service`, `screening-service`) |
| Политика хранения / очистки | `compact` (`TopicBuilder.compact()`) |
| Ключ сообщения | `Long` — идентификатор перевода (`transfer.id()`) |
| Сериализация ключа | Producer: `LongSerializer`; Consumer: `ErrorHandlingDeserializer` -> `LongDeserializer`; Kafka Streams: `Serdes.Long()` |
| Сериализация значения | Producer: `JsonSerializer`; Consumer: `ErrorHandlingDeserializer` -> `JsonDeserializer`; Kafka Streams: `JsonSerde<Transfer>` |
| Отправители | `screening-service` (`screening-service/.../ScreeningService.java`, метод `sendAndWait`) |
| Получатели (группа потребителей) | 1) `transfer-service` (`transfer-service/.../TransferEventListener.java`, метод `onEvent`, `groupId = "transfer-tracker"`); 2) `transfer-service` Kafka Streams (`transfer-service/.../DecisionTopologyConfig.java`, метод `decisionStream`, `application.id` по умолчанию из `spring.application.name` = `"transfer-service"`) |
| Формат сообщения | `com.example.common.Transfer` (см. таблицу «Формат сообщения» канала `transfers`) |

Канал: transfers.DLT
| Свойство | Значение |
| :--- | :--- |
| Тип канала | Топик Kafka (Dead Letter Topic) |
| Число партиций | `3` (`KafkaErrorHandlingConfig.transfersDlt` в `transfer-service`, `limit-service`, `screening-service`) |
| Политика хранения / очистки | В коде не задана (используется политика брокера по умолчанию) |
| Ключ сообщения | `Long` (при ошибке обработки) или `byte[]` (при ошибке десериализации ключа) |
| Сериализация ключа | `DelegatingByTypeSerializer` (`Long` -> `LongSerializer`, `byte[]` -> `ByteArraySerializer`, `KafkaErrorHandlingConfig.keySerializer`) |
| Сериализация значения | `JsonSerializer` (для десериализованных объектов `Object`) или `ByteArraySerializer` (для `byte[]` при ошибке десериализации) |
| Отправители | `DeadLetterPublishingRecoverer` в `transfer-service`, `limit-service`, `screening-service` (`KafkaErrorHandlingConfig.kafkaErrorHandler`) |
| Получатели (группа потребителей) | В коде не найдено |
| Формат сообщения | `com.example.common.Transfer` (см. канал `transfers`) при ошибке обработки; `byte[]` (исходный массив байтов) при ошибке десериализации |

Канал: limit-results.DLT
| Свойство | Значение |
| :--- | :--- |
| Тип канала | Топик Kafka (Dead Letter Topic) |
| Число партиций | `3` (`transfer-service/.../KafkaErrorHandlingConfig.java`, метод `limitResultsDlt`) |
| Политика хранения / очистки | В коде не задана (используется политика брокера по умолчанию) |
| Ключ сообщения | `Long` (при ошибке обработки) или `byte[]` (при ошибке десериализации ключа) |
| Сериализация ключа | `DelegatingByTypeSerializer` (`Long` -> `LongSerializer`, `byte[]` -> `ByteArraySerializer`) |
| Сериализация значения | `JsonSerializer` (для `Object`) или `ByteArraySerializer` (для `byte[]`) |
| Отправители | `DeadLetterPublishingRecoverer` в `transfer-service` (`transfer-service/.../KafkaErrorHandlingConfig.java`, метод `kafkaErrorHandler`) |
| Получатели (группа потребителей) | В коде не найдено |
| Формат сообщения | `com.example.common.Transfer` (см. канал `transfers`) при ошибке обработки; `byte[]` (исходный массив байтов) при ошибке десериализации |

Канал: screening-results.DLT
| Свойство | Значение |
| :--- | :--- |
| Тип канала | Топик Kafka (Dead Letter Topic) |
| Число партиций | `3` (`transfer-service/.../KafkaErrorHandlingConfig.java`, метод `screeningResultsDlt`) |
| Политика хранения / очистки | В коде не задана (используется политика брокера по умолчанию) |
| Ключ сообщения | `Long` (при ошибке обработки) или `byte[]` (при ошибке десериализации ключа) |
| Сериализация ключа | `DelegatingByTypeSerializer` (`Long` -> `LongSerializer`, `byte[]` -> `ByteArraySerializer`) |
| Сериализация значения | `JsonSerializer` (для `Object`) или `ByteArraySerializer` (для `byte[]`) |
| Отправители | `DeadLetterPublishingRecoverer` в `transfer-service` (`transfer-service/.../KafkaErrorHandlingConfig.java`, метод `kafkaErrorHandler`) |
| Получатели (группа потребителей) | В коде не найдено |
| Формат сообщения | `com.example.common.Transfer` (см. канал `transfers`) при ошибке обработки; `byte[]` (исходный массив байтов) при ошибке десериализации |

### 4. РАСХОЖДЕНИЯ КОНТРАКТА API С КОДОМ

| Элемент | В контракте (`transfer-service/src/main/resources/openapi/transfer-api.yaml`) | В коде |
| :--- | :--- | :--- |
| Код ответа `500` для `POST /transfers`, `GET /transfers`, `GET /transfers/{id}` | Ответ `500` не описан ни для одного эндпоинта | Реализован глобальный обработчик `ApiExceptionHandler.handleUnexpected` (`transfer-service/.../ApiExceptionHandler.java`), возвращающий `500 Internal Server Error` с `ProblemDetail` (`application/problem+json`) |
| Код ответа `400` для `GET /transfers/{id}` | Описаны только коды `200` и `404` | При передаче нечислового `id` в пути `ResponseEntityExceptionHandler` (унаследован `ApiExceptionHandler`) возвращает `400 Bad Request` с `ProblemDetail` (`application/problem+json`) |
| `TransferRequest.currency` (`nullable`) | В OpenAPI 3.0.3 свойство объявлено без `nullable: true`, поэтому явная передача `"currency": null` невалидна по схеме | В `TransferRequest` (`transfer-service/.../TransferRequest.java`) отсутствует `@NotNull`; метод `currencyOrDefault()` явно принимает `null` и заменяет его на `"RUB"` |
| `Transfer.updatedAt` (`nullable`) | Объявлено без `nullable: true` | В таблице `transfers` (`transfer-service/src/main/resources/db/changelog/001-create-tables.sql`) и в классе `TransferEntity` поле допускает `null` (ограничение `NOT NULL` отсутствует) |
| Обязательные поля (`required`) в схемах ответов `Transfer`, `TransferWithHistory`, `HistoryEntry`, `Problem` | Секция `required` отсутствует во всех схемах ответов (все поля помечены как необязательные) | `TransferController.getById` всегда возвращает оба поля `transfer` и `history`; в `TransferEntity` поля `id`, `senderId`, `recipientId`, `amount`, `currency`, `status`, `createdAt`, а в `TransferHistoryEntry` поля `id`, `transferId`, `stage`, `status`, `changedAt` всегда не `null` (`001-create-tables.sql`, `TransferTrackingService`) |
| Типизация `Transfer.status` и `HistoryEntry.status` | Заданы строгие перечисления `enum` (`[NEW, COMPLETED, CANCELLED, DENIED]` и `[NEW, APPROVED, DECLINED, COMPLETED, CANCELLED, DENIED]`) | В `TransferEntity` и `TransferHistoryEntry` поле `status` имеет тип `String` без проверки по enum; `TransferTrackingService.recordServiceResult` и `applyFinalStatus` сохраняют любую строку `status`, пришедшую из Kafka |
| Формат полей даты-времени и URI (`Transfer.createdAt`, `Transfer.updatedAt`, `HistoryEntry.changedAt`, `Problem.type`, `Problem.instance`) | Указан только базовый тип `type: string` без `format` | В `TransferEntity` и `TransferHistoryEntry` используется `java.time.LocalDateTime` (ISO-8601 `date-time`), а в `org.springframework.http.ProblemDetail` для `type` и `instance` — `java.net.URI` (`uri`) |