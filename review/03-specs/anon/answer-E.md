# 1. HTTP API

## Эндпоинт: POST /transfers
Описание: Создание перевода.

Параметры запроса:
| Параметр | Источник (body/path/query) | Тип | Обязательный | Описание |
|---|---|---|---|---|
| id | body | integer(int64) | да | Идентификатор перевода |
| senderId | body | integer(int64) | да | Идентификатор отправителя |
| recipientId | body | integer(int64) | да | Идентификатор получателя |
| amount | body | number | да | Сумма перевода |
| currency | body | string | нет | Код валюты; при null используется RUB |

Параметры ответа:
| Поле | Тип | Nullable | Описание |
|---|---|---|---|
| id | integer(int64) | нет | Идентификатор перевода |
| senderId | integer(int64) | нет | Идентификатор отправителя |
| recipientId | integer(int64) | нет | Идентификатор получателя |
| amount | number | нет | Сумма перевода |
| currency | string | нет | Код валюты |
| status | string | нет | Статус перевода |
| decidedBy | string | да | Кем принято решение |
| messageInfo | string | да | Информация о решении |
| createdAt | string | нет | Дата и время создания |
| updatedAt | string | да | Дата и время обновления |

Коды ответа:
| Код | Условие |
|---|---|
| 201 | Перевод создан и сообщение NEW опубликовано |
| 200 | Дубликат с теми же параметрами; для status NEW выполнена повторная публикация |
| 400 | Ошибка валидации `@Valid` |
| 409 | Перевод с таким id уже существует с другими параметрами |
| 503 | Kafka недоступна при публикации в `TransferController.publishNew` |
| 500 | Необработанное исключение в `ApiExceptionHandler.handleUnexpected` |

## Эндпоинт: GET /transfers
Описание: Получение всех переводов.

Параметры запроса:
| Параметр | Источник (body/path/query) | Тип | Обязательный | Описание |
|---|---|---|---|---|
| — | — | — | — | В коде параметры запроса не найдены |

Параметры ответа:
| Поле | Тип | Nullable | Описание |
|---|---|---|---|
| [] | array | нет | Массив объектов TransferEntity |
| [].id | integer(int64) | нет | Идентификатор перевода |
| [].senderId | integer(int64) | нет | Идентификатор отправителя |
| [].recipientId | integer(int64) | нет | Идентификатор получателя |
| [].amount | number | нет | Сумма перевода |
| [].currency | string | нет | Код валюты |
| [].status | string | нет | Статус перевода |
| [].decidedBy | string | да | Кем принято решение |
| [].messageInfo | string | да | Информация о решении |
| [].createdAt | string | нет | Дата и время создания |
| [].updatedAt | string | да | Дата и время обновления |

Коды ответа:
| Код | Условие |
|---|---|
| 200 | Успешно |
| 500 | Необработанное исключение в `ApiExceptionHandler.handleUnexpected` |

## Эндпоинт: GET /transfers/{id}
Описание: Получение перевода по ID с историей.

Параметры запроса:
| Параметр | Источник (body/path/query) | Тип | Обязательный | Описание |
|---|---|---|---|---|
| id | path | integer(int64) | да | Идентификатор перевода |

Параметры ответа:
| Поле | Тип | Nullable | Описание |
|---|---|---|---|
| transfer | object | нет | Объект TransferEntity |
| transfer.id | integer(int64) | нет | Идентификатор перевода |
| transfer.senderId | integer(int64) | нет | Идентификатор отправителя |
| transfer.recipientId | integer(int64) | нет | Идентификатор получателя |
| transfer.amount | number | нет | Сумма перевода |
| transfer.currency | string | нет | Код валюты |
| transfer.status | string | нет | Статус перевода |
| transfer.decidedBy | string | да | Кем принято решение |
| transfer.messageInfo | string | да | Информация о решении |
| transfer.createdAt | string | нет | Дата и время создания |
| transfer.updatedAt | string | да | Дата и время обновления |
| history | array | нет | История изменений |
| history[].id | integer(int64) | нет | Идентификатор записи истории |
| history[].transferId | integer(int64) | нет | Идентификатор перевода |
| history[].stage | string | нет | Этап: NEW, LIMIT, SCREENING, FINAL |
| history[].status | string | нет | Статус: NEW, APPROVED, DECLINED, COMPLETED, CANCELLED, DENIED |
| history[].decidedBy | string | да | Кем принято решение |
| history[].messageInfo | string | да | Информация о решении |
| history[].changedAt | string | нет | Дата и время изменения |

Коды ответа:
| Код | Условие |
|---|---|
| 200 | Перевод найден |
| 404 | Перевод не найден |
| 400 | id не соответствует Long |
| 500 | Необработанное исключение в `ApiExceptionHandler.handleUnexpected` |

# 2. СХЕМА БД

## Таблица: transfers (база: transferdb)
Описание: Хранит переводы.

| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
|---|---|---|---|---|---|---|---|---|
| id | BIGINT | да | нет | нет | да | нет | PK | Идентификатор перевода |
| sender_id | BIGINT | нет | нет | нет | да | нет | idx_transfers_sender_id | Идентификатор отправителя |
| recipient_id | BIGINT | нет | нет | нет | да | нет | нет | Идентификатор получателя |
| amount | DECIMAL(15,2) | нет | нет | нет | да | нет | нет | Сумма перевода |
| currency | VARCHAR(10) | нет | нет | нет | да | 'RUB' | нет | Код валюты |
| status | VARCHAR(20) | нет | нет | нет | да | нет | idx_transfers_status | Статус перевода |
| decided_by | VARCHAR(20) | нет | нет | нет | нет | нет | нет | Кем принято решение |
| message_info | TEXT | нет | нет | нет | нет | нет | нет | Информация о решении |
| created_at | TIMESTAMP | нет | нет | нет | да | нет | нет | Дата и время создания |
| updated_at | TIMESTAMP | нет | нет | нет | нет | нет | нет | Дата и время обновления |

Допустимые значения:
| Колонка | Значения |
|---|---|
| status | NEW, COMPLETED, CANCELLED, DENIED |
| decided_by | null, limit, screening |

## Таблица: transfer_history (база: transferdb)
Описание: Хранит историю этапов обработки перевода.

| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
|---|---|---|---|---|---|---|---|---|
| id | BIGSERIAL | да | нет | нет | да | нет | PK | Идентификатор записи истории |
| transfer_id | BIGINT | нет | transfers(id) | нет | да | нет | idx_transfer_history_transfer_id | Идентификатор перевода |
| status | VARCHAR(20) | нет | нет | нет | да | нет | нет | Статус на этапе |
| decided_by | VARCHAR(20) | нет | нет | нет | нет | нет | нет | Кем принято решение |
| message_info | TEXT | нет | нет | нет | нет | нет | нет | Информация о решении |
| changed_at | TIMESTAMP | нет | нет | нет | да | NOW() | нет | Дата и время изменения |
| stage | VARCHAR(20) | нет | нет | нет | да | нет | нет | Этап обработки |

Ограничения уровня таблицы:
| Ограничение | Колонки |
|---|---|
| uq_transfer_history_stage | transfer_id, stage |

Допустимые значения:
| Колонка | Значения |
|---|---|
| stage | NEW, LIMIT, SCREENING, FINAL |
| status | NEW, APPROVED, DECLINED, COMPLETED, CANCELLED, DENIED |
| decided_by | null, limit, screening |

## Таблица: sender_usage (база: limitdb)
Описание: Хранит накопленное использование лимитов отправителем.

| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
|---|---|---|---|---|---|---|---|---|
| sender_id | BIGINT | да | нет | нет | да | нет | PK | Идентификатор отправителя |
| daily_used | DECIMAL(15,2) | нет | нет | нет | да | 0 | нет | Использовано за день |
| monthly_used | DECIMAL(15,2) | нет | нет | нет | да | 0 | нет | Использовано за месяц |
| last_daily_reset | DATE | нет | нет | нет | да | CURRENT_DATE | нет | Дата последнего дневного сброса |
| last_monthly_reset | DATE | нет | нет | нет | да | CURRENT_DATE | нет | Дата последнего месячного сброса |
| version | BIGINT | нет | нет | нет | да | 0 | нет | Версия для оптимистичной блокировки |

## Таблица: reservations (база: limitdb)
Описание: Хранит резервирования по переводам.

| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
|---|---|---|---|---|---|---|---|---|
| transfer_id | BIGINT | да | нет | нет | да | нет | PK | Идентификатор перевода |
| sender_id | BIGINT | нет | нет | нет | да | нет | idx_reservations_sender_id | Идентификатор отправителя |
| amount | DECIMAL(15,2) | нет | нет | нет | да | нет | нет | Сумма резервирования |
| decision | VARCHAR(10) | нет | нет | нет | да | нет | нет | Решение по лимиту |
| reason | VARCHAR(50) | нет | нет | нет | нет | нет | нет | Причина решения |
| state | VARCHAR(20) | нет | нет | нет | да | нет | нет | Состояние резервирования |
| reserved_on | DATE | нет | нет | нет | да | нет | нет | Дата резервирования |
| created_at | TIMESTAMP | нет | нет | нет | да | нет | нет | Дата и время создания |
| updated_at | TIMESTAMP | нет | нет | нет | да | нет | нет | Дата и время обновления |

Допустимые значения:
| Колонка | Значения |
|---|---|
| decision | APPROVED, DECLINED |
| reason | null, INVALID_AMOUNT, DAILY_LIMIT_EXCEEDED, MONTHLY_LIMIT_EXCEEDED |
| state | RESERVED, COMMITTED, RELEASED, NONE |

## Таблица: blocked_senders (база: screeningdb)
Описание: Хранит заблокированных отправителей.

| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
|---|---|---|---|---|---|---|---|---|
| id | BIGSERIAL | да | нет | нет | да | нет | PK | Идентификатор записи |
| sender_id | BIGINT | нет | нет | да | да | нет | idx_blocked_senders_sender_id | Идентификатор отправителя |
| reason | VARCHAR(255) | нет | нет | нет | да | нет | нет | Причина блокировки |
| blocked_at | TIMESTAMP | нет | нет | нет | да | NOW() | нет | Дата и время блокировки |

## Таблица: screening_checks (база: screeningdb)
Описание: Хранит результаты проверки отправителей.

| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
|---|---|---|---|---|---|---|---|---|
| transfer_id | BIGINT | да | нет | нет | да | нет | PK | Идентификатор перевода |
| sender_id | BIGINT | нет | нет | нет | да | нет | idx_screening_checks_sender_id | Идентификатор отправителя |
| result | VARCHAR(10) | нет | нет | нет | да | нет | нет | Результат проверки |
| reason | VARCHAR(50) | нет | нет | нет | нет | нет | нет | Причина результата |
| checked_at | TIMESTAMP | нет | нет | нет | да | нет | нет | Дата и время проверки |

Допустимые значения:
| Колонка | Значения |
|---|---|
| result | APPROVED, DECLINED |
| reason | null, SENDER_BLOCKED |

# 3. КОНТРАКТЫ СООБЩЕНИЙ

## Формат Transfer (используется в каналах transfers, limit-results, screening-results)
| Поле | Тип | Nullable | Описание | Пример |
|---|---|---|---|---|
| id | Long | нет | Идентификатор перевода | 1001 |
| senderId | Long | нет | Идентификатор отправителя | 5 |
| recipientId | Long | нет | Идентификатор получателя | 13 |
| amount | BigDecimal | нет | Сумма перевода | 15000.00 |
| currency | String | нет | Код валюты | "RUB" |
| status | String | нет | Статус | "NEW" |
| decidedBy | String | да | Кем принято решение | null |
| messageInfo | String | да | Информация о решении | null |
| createdAt | LocalDateTime | нет | Дата и время создания | "2025-01-01T12:00:00" |

## Канал: transfers
| Свойство | Значение |
|---|---|
| Тип канала | Kafka topic |
| Число партиций | 3 |
| Политика хранения / очистки | compact |
| Ключ сообщения | Long, `transfer.id()` |
| Сериализация ключа | LongSerializer / LongDeserializer |
| Сериализация значения | JsonSerializer / JsonDeserializer (Transfer) |
| Отправители | `TransferController.publishNew`; `DecisionTopologyConfig.decisionStream` |
| Получатели | `limit-service.TransferEventsListener`, groupId="limit"; `screening-service.TransferEventsListener`, groupId="screening"; `transfer-service.TransferEventListener`, groupId="transfer-tracker" |

Формат сообщения: см. Формат Transfer.

## Канал: limit-results
| Свойство | Значение |
|---|---|
| Тип канала | Kafka topic |
| Число партиций | 3 |
| Политика хранения / очистки | compact |
| Ключ сообщения | Long, `transfer.id()` |
| Сериализация ключа | LongSerializer / LongDeserializer |
| Сериализация значения | JsonSerializer / JsonDeserializer (Transfer) |
| Отправители | `limit-service.LimitService.sendAndWait` |
| Получатели | `transfer-service.DecisionTopologyConfig.decisionStream` (application.id в коде явно не задан); `transfer-service.TransferEventListener`, groupId="transfer-tracker" |

Формат сообщения: см. Формат Transfer.

## Канал: screening-results
| Свойство | Значение |
|---|---|
| Тип канала | Kafka topic |
| Число партиций | 3 |
| Политика хранения / очистки | compact |
| Ключ сообщения | Long, `transfer.id()` |
| Сериализация ключа | LongSerializer / LongDeserializer |
| Сериализация значения | JsonSerializer / JsonDeserializer (Transfer) |
| Отправители | `screening-service.ScreeningService.sendAndWait` |
| Получатели | `transfer-service.DecisionTopologyConfig.decisionStream` (application.id в коде явно не задан); `transfer-service.TransferEventListener`, groupId="transfer-tracker" |

Формат сообщения: см. Формат Transfer.

## Канал: transfers.DLT
| Свойство | Значение |
|---|---|
| Тип канала | Kafka topic |
| Число партиций | 3 |
| Политика хранения / очистки | в коде не задана |
| Ключ сообщения | исходный ключ записи |
| Сериализация ключа | DelegatingByTypeSerializer (byte[] / Long) |
| Сериализация значения | ByteArraySerializer или JsonSerializer в `DeadLetterPublishingRecoverer` |
| Отправители | `transfer-service.KafkaErrorHandlingConfig`; `limit-service.KafkaErrorHandlingConfig`; `screening-service.KafkaErrorHandlingConfig` |
| Получатели | в коде не найдено |

Формат сообщения:
| Поле | Тип | Nullable | Описание | Пример |
|---|---|---|---|---|
| payload | byte[] / JSON | требует уточнения | Исходное сообщение, публикуемое `DeadLetterPublishingRecoverer` | в коде не найдено |
| headers | Spring Kafka headers | требует уточнения | Заголовки DLT | в коде не найдено |

## Канал: limit-results.DLT
| Свойство | Значение |
|---|---|
| Тип канала | Kafka topic |
| Число партиций | 3 |
| Политика хранения / очистки | в коде не задана |
| Ключ сообщения | исходный ключ записи |
| Сериализация ключа | DelegatingByTypeSerializer (byte[] / Long) |
| Сериализация значения | ByteArraySerializer или JsonSerializer в `DeadLetterPublishingRecoverer` |
| Отправители | `transfer-service.KafkaErrorHandlingConfig` |
| Получатели | в коде не найдено |

Формат сообщения: аналогичен `transfers.DLT`; специфичный формат в коде не найден.

## Канал: screening-results.DLT
| Свойство | Значение |
|---|---|
| Тип канала | Kafka topic |
| Число партиций | 3 |
| Политика хранения / очистки | в коде не задана |
| Ключ сообщения | исходный ключ записи |
| Сериализация ключа | DelegatingByTypeSerializer (byte[] / Long) |
| Сериализация значения | ByteArraySerializer или JsonSerializer в `DeadLetterPublishingRecoverer` |
| Отправители | `transfer-service.KafkaErrorHandlingConfig` |
| Получатели | в коде не найдено |

Формат сообщения: аналогичен `transfers.DLT`; специфичный формат в коде не найден.

# 4. РАСХОЖДЕНИЯ КОНТРАКТА API С КОДОМ

| Элемент | В контракте | В коде |
|---|---|---|
| POST /transfers, ответ 503 | 503 Problem | `TransferController.publishNew` выбрасывает `ResponseStatusException` со статусом 503, но `ApiExceptionHandler.handleUnexpected` перехватывает `Exception` и возвращает `ProblemDetail` со статусом 500 |
| POST /transfers, ответ 500 | не описан | `ApiExceptionHandler.handleUnexpected` возвращает 500 ProblemDetail |
| GET /transfers, ответ 500 | не описан | `ApiExceptionHandler.handleUnexpected` возвращает 500 ProblemDetail |
| GET /transfers/{id}, ответ 400 | не описан | `MethodArgumentTypeMismatchException` для `id` обрабатывается `ResponseEntityExceptionHandler` как 400 ProblemDetail |
| GET /transfers/{id}, ответ 500 | не описан | `ApiExceptionHandler.handleUnexpected` возвращает 500 ProblemDetail |
| TransferRequest.amount, multipleOf 0.01 | multipleOf: 0.01 | `@Digits(integer = 13, fraction = 2)` может отклонить значение с более чем 2 знаками после запятой, даже если оно кратно 0.01, например 0.010 |