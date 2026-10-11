### 1. HTTP API

Эндпоинт: POST /transfers
Описание: Создает новый перевод или возвращает информацию, если дубликат с такими же параметрами уже существует.

Параметры запроса:
| Параметр | Источник | Тип | Обязательный | Описание |
|---|---|---|---|---|
| id | body | Long | Да | ID перевода |
| senderId | body | Long | Да | ID отправителя |
| recipientId | body | Long | Да | ID получателя |
| amount | body | BigDecimal | Да | Сумма (больше нуля, макс. 13 цифр, 2 знака после запятой) |
| currency | body | String | Нет | Валюта (паттерн [A-Z]{3}, по умолчанию "RUB") |

Параметры ответа:
| Поле | Тип | Nullable | Описание |
|---|---|---|---|
| id | Long | Нет | ID перевода |
| senderId | Long | Нет | ID отправителя |
| recipientId | Long | Нет | ID получателя |
| amount | BigDecimal | Нет | Сумма перевода |
| currency | String | Нет | Валюта |
| status | String | Нет | Статус перевода |
| decidedBy | String | Да | Кто принял решение |
| messageInfo | String | Да | Дополнительная информация |
| createdAt | String (DateTime) | Нет | Дата создания перевода |
| updatedAt | String (DateTime) | Да | Дата обновления перевода |

Коды ответа:
| Код | Условие |
|---|---|
| 201 | Успешное создание нового перевода. |
| 200 | Возврат существующего перевода (дубликат). |
| 400 | Ошибка валидации тела запроса (неявная обработка Spring). |
| 409 | Конфликт, перевод с таким id существует, но с другими параметрами. |
| 503 | Ошибка при публикации сообщения в Kafka (Kafka недоступна). |
| 500 | Непредвиденная внутренняя ошибка сервера. |

Эндпоинт: GET /transfers
Описание: Возвращает список всех переводов, отсортированный по дате создания по убыванию.

Параметры запроса:
| Параметр | Источник | Тип | Обязательный | Описание |
|---|---|---|---|---|
| - | - | - | - | Параметры отсутствуют |

Параметры ответа:
| Поле | Тип | Nullable | Описание |
|---|---|---|---|
| [root] | Array of Objects | Нет | Массив объектов переводов (состав полей идентичен ответу POST /transfers) |

Коды ответа:
| Код | Условие |
|---|---|
| 200 | Успешный возврат списка переводов. |
| 500 | Непредвиденная внутренняя ошибка сервера. |

Эндпоинт: GET /transfers/{id}
Описание: Возвращает данные перевода и полную историю его статусов.

Параметры запроса:
| Параметр | Источник | Тип | Обязательный | Описание |
|---|---|---|---|---|
| id | path | Long | Да | ID перевода |

Параметры ответа:
| Поле | Тип | Nullable | Описание |
|---|---|---|---|
| transfer | Object | Нет | Объект перевода (поля идентичны POST /transfers) |
| history | Array of Objects | Нет | Массив записей истории статусов |
| history[].id | Long | Нет | Внутренний ID записи истории |
| history[].transferId | Long | Нет | ID перевода |
| history[].stage | String | Нет | Этап обработки (NEW, LIMIT, SCREENING, FINAL) |
| history[].status | String | Нет | Статус на данном этапе |
| history[].decidedBy | String | Да | Система, принявшая решение |
| history[].messageInfo | String | Да | Текст причины/сообщения |
| history[].changedAt | String (DateTime) | Нет | Дата записи этапа |

Коды ответа:
| Код | Условие |
|---|---|
| 200 | Перевод успешно найден. |
| 404 | Перевод с указанным id не найден. |
| 500 | Непредвиденная внутренняя ошибка сервера. |

### 2. СХЕМА БД

Таблица: sender_usage (база: limitdb)
Описание: Хранит данные об использовании лимитов отправителем.

| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
|---|---|---|---|---|---|---|---|---|
| sender_id | BIGINT | Да | Нет | Нет | Да | Нет | Нет | ID отправителя |
| daily_used | DECIMAL(15,2) | Нет | Нет | Нет | Да | 0 | Нет | Потрачено за день |
| monthly_used | DECIMAL(15,2) | Нет | Нет | Нет | Да | 0 | Нет | Потрачено за месяц |
| last_daily_reset | DATE | Нет | Нет | Нет | Да | CURRENT_DATE | Нет | Дата последнего суточного сброса |
| last_monthly_reset | DATE | Нет | Нет | Нет | Да | CURRENT_DATE | Нет | Дата последнего месячного сброса |
| version | BIGINT | Нет | Нет | Нет | Да | 0 | Нет | Поле для оптимистичной блокировки |

Таблица: reservations (база: limitdb)
Описание: Хранит решения по лимитам для конкретных переводов (резервирования).

| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
|---|---|---|---|---|---|---|---|---|
| transfer_id | BIGINT | Да | Нет | Нет | Да | Нет | Нет | ID перевода |
| sender_id | BIGINT | Нет | Нет | Нет | Да | Нет | idx_reservations_sender_id | ID отправителя |
| amount | DECIMAL(15,2) | Нет | Нет | Нет | Да | Нет | Нет | Сумма перевода |
| decision | VARCHAR(10) | Нет | Нет | Нет | Да | Нет | Нет | Принятое решение |
| reason | VARCHAR(50) | Нет | Нет | Нет | Нет | Нет | Нет | Причина отклонения |
| state | VARCHAR(20) | Нет | Нет | Нет | Да | Нет | Нет | Состояние резервирования |
| reserved_on | DATE | Нет | Нет | Нет | Да | Нет | Нет | Дата фиксации |
| created_at | TIMESTAMP | Нет | Нет | Нет | Да | Нет | Нет | Время создания записи |
| updated_at | TIMESTAMP | Нет | Нет | Нет | Да | Нет | Нет | Время изменения записи |

Допустимые значения:
| Колонка | Значения |
|---|---|
| decision | APPROVED, DECLINED |
| state | RESERVED, COMMITTED, RELEASED, NONE |

Таблица: blocked_senders (база: screeningdb)
Описание: Справочник заблокированных отправителей (санкционные списки и т.д.).

| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
|---|---|---|---|---|---|---|---|---|
| id | BIGSERIAL | Да | Нет | Нет | Да | Нет | Нет | Суррогатный PK |
| sender_id | BIGINT | Нет | Нет | Да | Да | Нет | idx_blocked_senders_sender_id | ID отправителя |
| reason | VARCHAR(255) | Нет | Нет | Нет | Да | Нет | Нет | Причина блокировки |
| blocked_at | TIMESTAMP | Нет | Нет | Нет | Да | NOW() | Нет | Дата попадания в список |

Таблица: screening_checks (база: screeningdb)
Описание: Журнал результатов проверки переводов антифрод/комплаенс службой.

| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
|---|---|---|---|---|---|---|---|---|
| transfer_id | BIGINT | Да | Нет | Нет | Да | Нет | Нет | ID перевода |
| sender_id | BIGINT | Нет | Нет | Нет | Да | Нет | idx_screening_checks_sender_id | ID отправителя |
| result | VARCHAR(10) | Нет | Нет | Нет | Да | Нет | Нет | Исход проверки |
| reason | VARCHAR(50) | Нет | Нет | Нет | Нет | Нет | Нет | Причина при отказе |
| checked_at | TIMESTAMP | Нет | Нет | Нет | Да | Нет | Нет | Дата проверки |

Допустимые значения:
| Колонка | Значения |
|---|---|
| result | APPROVED, DECLINED |

Таблица: transfers (база: transferdb)
Описание: Основная таблица реестра инициированных переводов.

| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
|---|---|---|---|---|---|---|---|---|
| id | BIGINT | Да | Нет | Нет | Да | Нет | Нет | ID перевода |
| sender_id | BIGINT | Нет | Нет | Нет | Да | Нет | idx_transfers_sender_id | ID отправителя |
| recipient_id | BIGINT | Нет | Нет | Нет | Да | Нет | Нет | ID получателя |
| amount | DECIMAL(15,2) | Нет | Нет | Нет | Да | Нет | Нет | Сумма перевода |
| currency | VARCHAR(10) | Нет | Нет | Нет | Да | 'RUB' | Нет | Валюта перевода |
| status | VARCHAR(20) | Нет | Нет | Нет | Да | Нет | idx_transfers_status | Текущий статус перевода |
| decided_by | VARCHAR(20) | Нет | Нет | Нет | Нет | Нет | Нет | Кто инициировал смену статуса |
| message_info | TEXT | Нет | Нет | Нет | Нет | Нет | Нет | Детальное текстовое сообщение |
| created_at | TIMESTAMP | Нет | Нет | Нет | Да | Нет | Нет | Дата создания перевода |
| updated_at | TIMESTAMP | Нет | Нет | Нет | Нет | Нет | Нет | Дата последнего обновления |

Таблица: transfer_history (база: transferdb)
Описание: Таблица с историей прохождения проверок (limit, screening) и финальными статусами.

| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
|---|---|---|---|---|---|---|---|---|
| id | BIGSERIAL | Да | Нет | Нет | Да | Нет | Нет | Внутренний ID |
| transfer_id | BIGINT | Нет | transfers(id) | Составной | Да | Нет | idx_transfer_history_transfer_id | ID перевода |
| status | VARCHAR(20) | Нет | Нет | Нет | Да | Нет | Нет | Статус решения |
| decided_by | VARCHAR(20) | Нет | Нет | Нет | Нет | Нет | Нет | Кем принято решение |
| message_info | TEXT | Нет | Нет | Нет | Нет | Нет | Нет | Комментарий/причина |
| changed_at | TIMESTAMP | Нет | Нет | Нет | Да | NOW() | Нет | Дата события |
| stage | VARCHAR(20) | Нет | Нет | Составной | Да | Нет | Нет | Этап обработки |

Ограничения уровня таблицы:
| Ограничение | Колонки |
|---|---|
| uq_transfer_history_stage | transfer_id, stage |

Допустимые значения:
| Колонка | Значения |
|---|---|
| stage | NEW, LIMIT, SCREENING, FINAL (enum HistoryStage в коде) |

### 3. КОНТРАКТЫ СООБЩЕНИЙ

Формат сообщения (Используется во всех описанных ниже целевых каналах):
| Поле | Тип | Nullable | Описание | Пример |
|---|---|---|---|---|
| id | Long | Нет | ID перевода | 1001 |
| senderId | Long | Нет | ID отправителя | 555 |
| recipientId | Long | Нет | ID получателя | 777 |
| amount | BigDecimal | Нет | Сумма перевода | 1500.00 |
| currency | String | Нет | Валюта | "RUB" |
| status | String | Нет | Статус / Результат обработки | "NEW" |
| decidedBy | String | Да | Подсистема, принявшая решение | "limit" |
| messageInfo | String | Да | Сообщение с причиной отказа | "DAILY_LIMIT_EXCEEDED" |
| createdAt | String(ISO) / LocalDateTime | Нет | Дата-время создания | "2024-01-01T12:00:00" |

Канал: transfers
| Свойство | Значение |
|---|---|
| Тип канала | Kafka Topic |
| Партиции | 3 |
| Политика хранения | compact |
| Ключ сообщения | Long (ID перевода) |
| Сериализация ключа | LongSerializer / LongDeserializer |
| Сериализация значения | JsonSerializer / JsonDeserializer |
| Отправители | transfer-service (REST Controller), transfer-service (Kafka Streams) |
| Получатели | limit-service (group: limit), screening-service (group: screening), transfer-service (group: transfer-tracker) |

Канал: limit-results
| Свойство | Значение |
|---|---|
| Тип канала | Kafka Topic |
| Партиции | 3 |
| Политика хранения | compact |
| Ключ сообщения | Long (ID перевода) |
| Сериализация ключа | LongSerializer / LongDeserializer |
| Сериализация значения | JsonSerializer / JsonDeserializer |
| Отправители | limit-service |
| Получатели | transfer-service (group: transfer-tracker), transfer-service (Kafka Streams) |

Канал: screening-results
| Свойство | Значение |
|---|---|
| Тип канала | Kafka Topic |
| Партиции | 3 |
| Политика хранения | compact |
| Ключ сообщения | Long (ID перевода) |
| Сериализация ключа | LongSerializer / LongDeserializer |
| Сериализация значения | JsonSerializer / JsonDeserializer |
| Отправители | screening-service |
| Получатели | transfer-service (group: transfer-tracker), transfer-service (Kafka Streams) |

Канал: transfers.DLT, limit-results.DLT, screening-results.DLT
| Свойство | Значение |
|---|---|
| Тип канала | Kafka Topic (Dead Letter Topic) |
| Партиции | 3 |
| Политика хранения | default (удаление по времени) |
| Ключ сообщения | В зависимости от исходного сообщения (ByteArray/Long) |
| Сериализация ключа | ByteArraySerializer / LongSerializer |
| Сериализация значения | ByteArraySerializer / JsonSerializer |
| Отправители | Все сервисы (через DeadLetterPublishingRecoverer) |
| Получатели | В коде не настроены |

### 4. РАСХОЖДЕНИЯ КОНТРАКТА API С КОДОМ

| Элемент | В контракте (openapi/transfer-api.yaml) | В коде |
|---|---|---|
| Глобальные коды ответов (GET /transfers, GET /transfers/{id}, POST /transfers) | Отсутствует код ответа 500 (Internal Server Error) | Присутствует глобальный обработчик `ApiExceptionHandler`, возвращающий код `500` и структуру `ProblemDetail`. |
| Схема TransferRequest (POST /transfers) | Для поля `amount` не указан параметр формата `minimum: 0`, ограничивающий точное поведение, используется `exclusiveMinimum: true` | Валидация аннотацией `@Positive` (число строго > 0). Соответствует логике, но технически в коде используется `integer=13, fraction=2`, что в OpenAPI не отражено в `multipleOf` и `maximum` напрямую через аннотации кода. |
| Схема Transfer (поле `status`) и TransferStatus | Тип `string` со строгим `enum` (NEW, COMPLETED, CANCELLED, DENIED) | Поле `TransferEntity.status` имеет тип `String` и не привязано к перечислению в Java (enum не валидируется при выгрузке из БД). |
| Схема HistoryEntry (поле `status`) | Тип `string` со строгим `enum` (NEW, APPROVED, DECLINED, COMPLETED, CANCELLED, DENIED) | Поле `TransferHistoryEntry.status` имеет тип `String` и не привязано к перечислению в Java. |