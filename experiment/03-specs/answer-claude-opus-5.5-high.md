## 1. HTTP API (transfer-service, порт 8080, TransferController)

### Эндпоинт: POST /transfers
Описание: Создаёт перевод с идентификатором, заданным клиентом, и публикует событие NEW в топик transfers. Повторный запрос с тем же id обрабатывается идемпотентно.

Параметры запроса:
| Параметр | Источник (body/path/query) | Тип | Обязательный | Описание |
|---|---|---|---|---|
| id | body | integer (int64) | да (@NotNull) | Идентификатор перевода. Задаётся клиентом и служит ключом идемпотентности (TransferRequest) |
| senderId | body | integer (int64) | да (@NotNull) | Идентификатор отправителя |
| recipientId | body | integer (int64) | да (@NotNull) | Идентификатор получателя |
| amount | body | number | да (@NotNull, @Positive, @Digits(integer=13, fraction=2)) | Сумма перевода: больше 0, не более 13 знаков в целой части и 2 в дробной |
| currency | body | string | нет (@Pattern "[A-Z]{3}"; если не передано, подставляется "RUB" в TransferRequest.currencyOrDefault) | Код валюты из трёх заглавных латинских букв |

Параметры ответа (201, 200), объект TransferEntity:
| Поле | Тип | Nullable | Описание |
|---|---|---|---|
| id | integer (int64) | нет | Идентификатор перевода |
| senderId | integer (int64) | нет | Идентификатор отправителя |
| recipientId | integer (int64) | нет | Идентификатор получателя |
| amount | number | нет | Сумма перевода |
| currency | string | нет | Код валюты |
| status | string | нет | Статус перевода: NEW / COMPLETED / CANCELLED / DENIED |
| decidedBy | string | да | Сервис, принявший решение: "limit", "screening" или null |
| messageInfo | string | да | Причина решения |
| createdAt | string (LocalDateTime, ISO-8601 без часового пояса) | нет | Время создания |
| updatedAt | string (LocalDateTime, ISO-8601 без часового пояса) | да (в БД допускается NULL; при создании через API всегда заполняется) | Время последнего изменения |

Параметры ответа при ошибке (400, 409, 500, 503), объект ProblemDetail:
| Поле | Тип | Nullable | Описание |
|---|---|---|---|
| type | string | нет | В коде не переопределяется, значение по умолчанию "about:blank" |
| title | string | нет | Название HTTP-статуса, например "Conflict" |
| status | integer | нет | HTTP-код |
| detail | string | нет | Текст ошибки. 409: «Перевод с id=… уже существует с другими параметрами». 503: «Kafka недоступна, перевод сохранён — повторите запрос с тем же id». 500: «Внутренняя ошибка сервера, повторите запрос позже». 400: стандартный текст Spring |
| instance | string | нет | Путь запроса, Spring заполняет его автоматически |

Коды ответа:
| Код | Условие |
|---|---|
| 201 | Перевод создан, событие NEW опубликовано (TransferController.create) |
| 200 | Перевод с таким id уже существует с теми же параметрами (TransferController.duplicate) |
| 400 | Тело запроса не прошло валидацию TransferRequest или содержит некорректный JSON (ResponseEntityExceptionHandler, базовый класс ApiExceptionHandler) |
| 409 | Перевод с таким id уже существует с другими senderId / recipientId / amount / currency (TransferController.duplicate) |
| 503 | Не удалось отправить сообщение в Kafka (TransferController.publishNew) |
| 500 | Любое другое необработанное исключение (ApiExceptionHandler.handleUnexpected) |

### Эндпоинт: GET /transfers
Описание: Возвращает список всех переводов.

Параметры запроса:
| Параметр | Источник (body/path/query) | Тип | Обязательный | Описание |
|---|---|---|---|---|
| — | — | — | — | Параметров нет |

Параметры ответа (200):
| Поле | Тип | Nullable | Описание |
|---|---|---|---|
| (корень) | array of object | нет | Список переводов, отсортированный по createdAt по убыванию (TransferRepository.findAllByOrderByCreatedAtDesc). Поля элемента такие же, как в ответе POST /transfers |

Коды ответа:
| Код | Условие |
|---|---|
| 200 | Успешно (TransferController.all) |
| 500 | Необработанное исключение. Тело в формате ProblemDetail, как в POST /transfers (ApiExceptionHandler.handleUnexpected) |

### Эндпоинт: GET /transfers/{id}
Описание: Возвращает перевод по идентификатору вместе с историей его этапов.

Параметры запроса:
| Параметр | Источник (body/path/query) | Тип | Обязательный | Описание |
|---|---|---|---|---|
| id | path | integer (int64) | да (@PathVariable) | Идентификатор перевода |

Параметры ответа (200):
| Поле | Тип | Nullable | Описание |
|---|---|---|---|
| transfer | object | нет | Перевод. Поля такие же, как в ответе POST /transfers |
| history | array of object | нет | История этапов, отсортированная по stage (NEW, LIMIT, SCREENING, FINAL), затем по changedAt (TransferController.getById) |
| history[].id | integer (int64) | нет | Идентификатор записи истории |
| history[].transferId | integer (int64) | нет | Идентификатор перевода |
| history[].stage | string | нет | Этап: NEW / LIMIT / SCREENING / FINAL |
| history[].status | string | нет | Статус на этапе: NEW / APPROVED / DECLINED / COMPLETED / CANCELLED / DENIED |
| history[].decidedBy | string | да | "limit", "screening" или null |
| history[].messageInfo | string | да | Причина решения |
| history[].changedAt | string (LocalDateTime, ISO-8601 без часового пояса) | нет | Время записи этапа |

Коды ответа:
| Код | Условие |
|---|---|
| 200 | Перевод найден |
| 404 | Перевод не найден, тело пустое (ResponseEntity.notFound()) |
| 400 | id нельзя привести к Long. Тело в формате ProblemDetail (ResponseEntityExceptionHandler) |
| 500 | Необработанное исключение. Тело в формате ProblemDetail (ApiExceptionHandler.handleUnexpected) |

## 2. СХЕМА БД

### Таблица: transfers (база: transferdb)
Описание: Переводы и их итоговый статус (transfer-service, TransferEntity).

| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
|---|---|---|---|---|---|---|---|---|
| id | BIGINT | да | — | да (PK) | да | — | PK | Идентификатор перевода. Задаётся клиентом, не генерируется |
| sender_id | BIGINT | — | — | — | да | — | idx_transfers_sender_id | Идентификатор отправителя |
| recipient_id | BIGINT | — | — | — | да | — | — | Идентификатор получателя |
| amount | DECIMAL(15,2) | — | — | — | да | — | — | Сумма перевода |
| currency | VARCHAR(10) | — | — | — | да | 'RUB' | — | Код валюты |
| status | VARCHAR(20) | — | — | — | да | — | idx_transfers_status | Текущий статус перевода |
| decided_by | VARCHAR(20) | — | — | — | нет | — | — | Сервис, принявший итоговое решение |
| message_info | TEXT | — | — | — | нет | — | — | Причина итогового решения |
| created_at | TIMESTAMP | — | — | — | да | — | — | Время создания |
| updated_at | TIMESTAMP | — | — | — | нет | — | — | Время последнего изменения |

Допустимые значения:
| Колонка | Значения |
|---|---|
| status | NEW, COMPLETED, CANCELLED, DENIED |
| decided_by | limit, screening, NULL |

### Таблица: transfer_history (база: transferdb)
Описание: История прохождения переводом этапов обработки (transfer-service, TransferHistoryEntry).

| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
|---|---|---|---|---|---|---|---|---|
| id | BIGSERIAL | да | — | да (PK) | да | последовательность | PK | Идентификатор записи (GenerationType.IDENTITY) |
| transfer_id | BIGINT | — | transfers(id) | в составе uq_transfer_history_stage | да | — | idx_transfer_history_transfer_id; входит в uq_transfer_history_stage | Идентификатор перевода |
| status | VARCHAR(20) | — | — | — | да | — | — | Статус на этапе |
| decided_by | VARCHAR(20) | — | — | — | нет | — | — | Сервис, принявший решение |
| message_info | TEXT | — | — | — | нет | — | — | Причина решения |
| changed_at | TIMESTAMP | — | — | — | да | NOW() | — | Время записи этапа |
| stage | VARCHAR(20) | — | — | в составе uq_transfer_history_stage | да | — | входит в uq_transfer_history_stage | Этап обработки (HistoryStage, EnumType.STRING) |

Ограничения уровня таблицы:
| Ограничение | Колонки |
|---|---|
| uq_transfer_history_stage (UNIQUE) | transfer_id, stage |

Допустимые значения:
| Колонка | Значения |
|---|---|
| stage | NEW, LIMIT, SCREENING, FINAL |
| status | NEW (этап NEW); APPROVED, DECLINED (этапы LIMIT, SCREENING); COMPLETED, CANCELLED, DENIED (этап FINAL) |
| decided_by | limit, screening, NULL |

### Таблица: sender_usage (база: limitdb)
Описание: Накопленное использование лимитов по отправителю (limit-service, SenderUsage).

| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
|---|---|---|---|---|---|---|---|---|
| sender_id | BIGINT | да | — | да (PK) | да | — | PK | Идентификатор отправителя |
| daily_used | DECIMAL(15,2) | — | — | — | да | 0 | — | Использовано за текущий день |
| monthly_used | DECIMAL(15,2) | — | — | — | да | 0 | — | Использовано за текущий месяц |
| last_daily_reset | DATE | — | — | — | да | CURRENT_DATE | — | Дата последнего сброса дневного счётчика |
| last_monthly_reset | DATE | — | — | — | да | CURRENT_DATE | — | Дата последнего сброса месячного счётчика |
| version | BIGINT | — | — | — | да | 0 | — | Версия для оптимистической блокировки (@Version) |

### Таблица: reservations (база: limitdb)
Описание: Решение limit-service и состояние резерва лимита по каждому переводу (Reservation).

| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
|---|---|---|---|---|---|---|---|---|
| transfer_id | BIGINT | да | — | да (PK) | да | — | PK | Идентификатор перевода |
| sender_id | BIGINT | — | — | — | да | — | idx_reservations_sender_id | Идентификатор отправителя |
| amount | DECIMAL(15,2) | — | — | — | да | — | — | Зарезервированная сумма |
| decision | VARCHAR(10) | — | — | — | да | — | — | Решение по лимиту |
| reason | VARCHAR(50) | — | — | — | нет | — | — | Причина отказа |
| state | VARCHAR(20) | — | — | — | да | — | — | Состояние резерва |
| reserved_on | DATE | — | — | — | да | — | — | Дата резервирования |
| created_at | TIMESTAMP | — | — | — | да | — | — | Время создания |
| updated_at | TIMESTAMP | — | — | — | да | — | — | Время последнего изменения |

Допустимые значения:
| Колонка | Значения |
|---|---|
| decision | APPROVED, DECLINED |
| reason | INVALID_AMOUNT, DAILY_LIMIT_EXCEEDED, MONTHLY_LIMIT_EXCEEDED, NULL |
| state | RESERVED, COMMITTED, RELEASED, NONE |

### Таблица: blocked_senders (база: screeningdb)
Описание: Справочник заблокированных отправителей (screening-service, BlockedSender). Начальные данные загружаются в changeset 002-insert-test-data.

| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
|---|---|---|---|---|---|---|---|---|
| id | BIGSERIAL | да | — | да (PK) | да | последовательность | PK | Идентификатор записи (GenerationType.IDENTITY) |
| sender_id | BIGINT | — | — | да | да | — | idx_blocked_senders_sender_id + неявный уникальный индекс | Идентификатор заблокированного отправителя |
| reason | VARCHAR(255) | — | — | — | да | — | — | Причина блокировки, свободный текст |
| blocked_at | TIMESTAMP | — | — | — | да | NOW() | — | Время блокировки |

### Таблица: screening_checks (база: screeningdb)
Описание: Результаты проверки переводов в screening-service (ScreeningCheck).

| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
|---|---|---|---|---|---|---|---|---|
| transfer_id | BIGINT | да | — | да (PK) | да | — | PK | Идентификатор перевода |
| sender_id | BIGINT | — | — | — | да | — | idx_screening_checks_sender_id | Идентификатор отправителя |
| result | VARCHAR(10) | — | — | — | да | — | — | Результат проверки |
| reason | VARCHAR(50) | — | — | — | нет | — | — | Причина отказа |
| checked_at | TIMESTAMP | — | — | — | да | — | — | Время проверки |

Допустимые значения:
| Колонка | Значения |
|---|---|
| result | APPROVED, DECLINED |
| reason | SENDER_BLOCKED, NULL |

## 3. КОНТРАКТЫ СООБЩЕНИЙ

### Канал: transfers
| Свойство | Значение |
|---|---|
| Тип канала | Kafka topic (брокер cp-kafka 7.6.0, автосоздание топиков выключено) |
| Объявление | TopicsConfig.transfersTopic в transfer-service, limit-service и screening-service |
| Число партиций | 3 |
| Фактор репликации | В коде не задан (используется значение брокера по умолчанию) |
| Политика очистки / хранения | cleanup.policy = compact; retention в коде не задан |
| Ключ сообщения | Transfer.id (Long) |
| Сериализация ключа | LongSerializer / LongDeserializer (через ErrorHandlingDeserializer); в Kafka Streams — Serdes.Long (default.key.serde) |
| Сериализация значения | JSON: JsonSerializer / JsonDeserializer (через ErrorHandlingDeserializer, spring.json.trusted.packages="*"); в Kafka Streams — JsonSerde (default.value.serde) |
| Заголовки | __TypeId__ — добавляется JsonSerializer/JsonSerde по умолчанию, в коде явно не настраивается |
| Отправители | transfer-service: TransferController.publishNew (статус NEW); transfer-service: Kafka Streams DecisionTopologyConfig.decisionStream (итоговые статусы) |
| Получатели | limit-service: TransferEventsListener.onEvent, группа "limit"; screening-service: TransferEventsListener.onEvent, группа "screening"; transfer-service: TransferEventListener.onEvent, группа "transfer-tracker" |
| auto-offset-reset | earliest |

Формат сообщения (com.example.common.Transfer, общий для transfers, limit-results и screening-results):
| Поле | Тип | Nullable | Описание | Пример |
|---|---|---|---|---|
| id | Long | нет | Идентификатор перевода, совпадает с ключом | 1001 |
| senderId | Long | нет | Идентификатор отправителя | 7 |
| recipientId | Long | нет | Идентификатор получателя | 8 |
| amount | BigDecimal | нет | Сумма перевода | 1500.00 |
| currency | String | нет | Код валюты | "RUB" |
| status | String | нет | Статус, набор значений зависит от канала (см. ниже) | "NEW" |
| decidedBy | String | да | Сервис, принявший решение | "limit" |
| messageInfo | String | да | Причина решения | "DAILY_LIMIT_EXCEEDED" |
| createdAt | LocalDateTime | нет | Время создания перевода, строка ISO-8601 | "2026-10-02T14:30:15.123456" |

Значения полей по каналам:
| Канал / отправитель | status | decidedBy | messageInfo |
|---|---|---|---|
| transfers / TransferController | NEW | null | null |
| transfers / Kafka Streams | COMPLETED | null | null |
| transfers / Kafka Streams | DENIED | null | "<причина limit>; <причина screening>" |
| transfers / Kafka Streams | CANCELLED | "limit", "screening" или null | Причина от отклонившего сервиса либо "UNEXPECTED_RESULT: limit=…, screening=…" |
| limit-results / limit-service | APPROVED, DECLINED | "limit" | null, INVALID_AMOUNT, DAILY_LIMIT_EXCEEDED, MONTHLY_LIMIT_EXCEEDED |
| screening-results / screening-service | APPROVED, DECLINED | "screening" | null, SENDER_BLOCKED |

### Канал: limit-results
| Свойство | Значение |
|---|---|
| Тип канала | Kafka topic |
| Объявление | TopicsConfig.limitResultsTopic во всех трёх сервисах |
| Число партиций | 3 |
| Фактор репликации | В коде не задан |
| Политика очистки / хранения | cleanup.policy = compact; retention в коде не задан |
| Ключ сообщения | Transfer.id (Long) |
| Сериализация ключа | LongSerializer; у получателей — LongDeserializer / Serdes.Long |
| Сериализация значения | JsonSerializer; у получателей — JsonDeserializer / JsonSerde<Transfer> |
| Отправители | limit-service: LimitService.sendAndWait |
| Получатели | transfer-service: Kafka Streams DecisionTopologyConfig.decisionStream, join со screening-results в окне 10 с. application.id явно не задан, требует уточнения: по умолчанию Spring Boot берёт spring.application.name = transfer-service. transfer-service: TransferEventListener.onEvent, группа "transfer-tracker" |
| Формат сообщения | Transfer (см. канал transfers) |

### Канал: screening-results
| Свойство | Значение |
|---|---|
| Тип канала | Kafka topic |
| Объявление | TopicsConfig.screeningResultsTopic во всех трёх сервисах |
| Число партиций | 3 |
| Фактор репликации | В коде не задан |
| Политика очистки / хранения | cleanup.policy = compact; retention в коде не задан |
| Ключ сообщения | Transfer.id (Long) |
| Сериализация ключа | LongSerializer; у получателей — LongDeserializer / Serdes.Long |
| Сериализация значения | JsonSerializer; у получателей — JsonDeserializer / JsonSerde<Transfer> |
| Отправители | screening-service: ScreeningService.sendAndWait |
| Получатели | transfer-service: Kafka Streams DecisionTopologyConfig.decisionStream (join с limit-results); transfer-service: TransferEventListener.onEvent, группа "transfer-tracker" |
| Формат сообщения | Transfer (см. канал transfers) |

### Канал: transfers.DLT
| Свойство | Значение |
|---|---|
| Тип канала | Kafka topic (dead letter) |
| Объявление | KafkaErrorHandlingConfig.transfersDlt в transfer-service, limit-service и screening-service |
| Число партиций | 3 |
| Фактор репликации | В коде не задан |
| Политика очистки / хранения | В коде не задана (используется значение брокера по умолчанию) |
| Партиция назначения | Совпадает с партицией исходной записи |
| Ключ сообщения | Ключ исходной записи |
| Сериализация ключа | DelegatingByTypeSerializer: Long → LongSerializer, byte[] → ByteArraySerializer |
| Сериализация значения | Объект → JsonSerializer; byte[] (если десериализация не удалась) → ByteArraySerializer |
| Отправители | DeadLetterPublishingRecoverer из KafkaErrorHandlingConfig.kafkaErrorHandler в трёх сервисах, после FixedBackOff(1000 мс, 3 повтора) |
| Получатели | В коде не найдено |

### Канал: limit-results.DLT, screening-results.DLT
| Свойство | Значение |
|---|---|
| Тип канала | Kafka topic (dead letter) |
| Объявление | KafkaErrorHandlingConfig.limitResultsDlt / screeningResultsDlt, только в transfer-service |
| Число партиций | 3 |
| Фактор репликации | В коде не задан |
| Политика очистки / хранения | В коде не задана (используется значение брокера по умолчанию) |
| Партиция назначения | Совпадает с партицией исходной записи |
| Ключ сообщения | Ключ исходной записи |
| Сериализация ключа и значения | Как у transfers.DLT |
| Отправители | transfer-service: DeadLetterPublishingRecoverer для слушателя TransferEventListener (группа "transfer-tracker"). Kafka Streams в DLT не пишет: ошибки десериализации обрабатываются LogAndContinueExceptionHandler |
| Получатели | В коде не найдено |

Формат сообщения DLT (общий для transfers.DLT, limit-results.DLT, screening-results.DLT):
| Поле | Тип | Nullable | Описание | Пример |
|---|---|---|---|---|
| (тело) | Transfer (JSON) либо byte[] | нет | Копия исходного сообщения (формат Transfer, см. канал transfers) или исходные байты, если десериализация не удалась | — |
| Заголовки kafka_dlt-* | byte[] | нет | Стандартные заголовки DeadLetterPublishingRecoverer: исходный топик, партиция, оффсет, класс и текст исключения | kafka_dlt-original-topic = "transfers" |

## 4. РАСХОЖДЕНИЯ КОНТРАКТА API С КОДОМ

Контракт: transfer-service/src/main/resources/openapi/transfer-api.yaml.

| Элемент | В контракте | В коде |
|---|---|---|
| POST /transfers — код 500 | Не описан | ProblemDetail при любом необработанном исключении (ApiExceptionHandler.handleUnexpected) |
| GET /transfers — код 500 | Не описан, есть только 200 | ProblemDetail (ApiExceptionHandler.handleUnexpected) |
| GET /transfers/{id} — код 400 | Не описан | ProblemDetail при нечисловом id (TypeMismatchException обрабатывается ResponseEntityExceptionHandler) |
| GET /transfers/{id} — код 500 | Не описан | ProblemDetail (ApiExceptionHandler.handleUnexpected) |
| Все эндпоинты — коды 405, 406, 415 | Не описаны | Возвращаются базовым ResponseEntityExceptionHandler (родитель ApiExceptionHandler) в формате ProblemDetail |
| Transfer.createdAt, Transfer.updatedAt, HistoryEntry.changedAt — формат | type: string, format не указан | LocalDateTime сериализуется в строку ISO-8601 без смещения часового пояса, например "2026-10-02T14:30:15.123456" |
| Transfer.updatedAt — nullable | Не помечено как nullable | Колонка transfers.updated_at допускает NULL (001-create-tables.sql), поле TransferEntity.updatedAt может быть null. При создании через API заполняется |