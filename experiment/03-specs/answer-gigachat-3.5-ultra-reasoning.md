## 1. HTTP API

### POST /transfers
| Параметр | Источник | Тип | Обязательный | Описание |
|----------|----------|-----|--------------|----------|
| id | body | integer (int64) | да | Идентификатор перевода |
| senderId | body | integer (int64) | да | ID отправителя |
| recipientId | body | integer (int64) | да | ID получателя |
| amount | body | number (decimal) | да | Сумма |
| currency | body | string | нет (default: RUB) | Код валюты |

| Поле | Тип | Nullable | Описание |
|------|-----|----------|----------|
| id | integer (int64) | нет | Идентификатор перевода |
| senderId | integer (int64) | нет | ID отправителя |
| recipientId | integer (int64) | нет | ID получателя |
| amount | number (decimal) | нет | Сумма |
| currency | string | нет | Код валюты |
| status | string | нет | Статус перевода |
| decidedBy | string | да | Кто принял решение |
| messageInfo | string | да | Дополнительная информация |
| createdAt | string (date-time) | нет | Дата создания |
| updatedAt | string (date-time) | да | Дата обновления |

| Код | Условие |
|-----|---------|
| 201 | Перевод создан |
| 200 | Перевод уже существует с теми же параметрами |
| 400 | Ошибка валидации |
| 409 | Конфликт параметров (существует с другими параметрами) |
| 503 | Kafka недоступна |

### GET /transfers
Нет параметров запроса.

| Поле | Тип | Nullable | Описание |
|------|-----|----------|----------|
| массив | Transfer | нет | Список переводов |

| Код | Условие |
|-----|---------|
| 200 | OK |

### GET /transfers/{id}
| Параметр | Источник | Тип | Обязательный | Описание |
|----------|----------|-----|--------------|----------|
| id | path | integer (int64) | да | Идентификатор перевода |

| Поле | Тип | Nullable | Описание |
|------|-----|----------|----------|
| transfer | Transfer | нет | Данные перевода |
| history | array (HistoryEntry) | нет | История изменений |

| Код | Условие |
|-----|---------|
| 200 | OK |
| 404 | Перевод не найден |

---

## 2. СХЕМА БД

### transfer-db: transfers
| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
|---------|-----|----|----|--------|----------|---------|-------|----------|
| id | BIGINT | да | нет | нет | да | нет | idx_transfers_sender_id, idx_transfers_status | Идентификатор перевода |
| sender_id | BIGINT | нет | нет | нет | да | нет | idx_transfers_sender_id | ID отправителя |
| recipient_id | BIGINT | нет | нет | нет | да | нет | нет | ID получателя |
| amount | DECIMAL(15,2) | нет | нет | нет | да | нет | нет | Сумма |
| currency | VARCHAR(10) | нет | нет | нет | да | 'RUB' | нет | Код валюты |
| status | VARCHAR(20) | нет | нет | нет | да | нет | idx_transfers_status | Статус |
| decided_by | VARCHAR(20) | нет | нет | нет | нет | нет | нет | Кто принял решение |
| message_info | TEXT | нет | нет | нет | нет | нет | нет | Сообщение |
| created_at | TIMESTAMP | нет | нет | нет | да | нет | нет | Дата создания |
| updated_at | TIMESTAMP | нет | нет | нет | нет | нет | нет | Дата обновления |

### transfer-db: transfer_history
| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
|---------|-----|----|----|--------|----------|---------|-------|----------|
| id | BIGSERIAL | да | нет | нет | да | нет | idx_transfer_history_transfer_id | ID записи |
| transfer_id | BIGINT | нет | да (transfers.id) | нет | да | нет | idx_transfer_history_transfer_id | ID перевода |
| status | VARCHAR(20) | нет | нет | нет | да | нет | нет | Статус |
| decided_by | VARCHAR(20) | нет | нет | нет | нет | нет | нет | Кто принял решение |
| message_info | TEXT | нет | нет | нет | нет | нет | нет | Сообщение |
| changed_at | TIMESTAMP | нет | нет | нет | да | NOW() | нет | Дата изменения |
| stage | VARCHAR(20) | нет | нет | нет | да | нет | нет | Этап |

**Ограничения уровня таблицы:**
| Ограничение | Колонки |
|-------------|---------|
| uq_transfer_history_stage | transfer_id, stage |

### limit-db: sender_usage
| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
|---------|-----|----|----|--------|----------|---------|-------|----------|
| sender_id | BIGINT | да | нет | нет | да | нет | нет | ID отправителя |
| daily_used | DECIMAL(15,2) | нет | нет | нет | да | 0 | нет | Дневной лимит |
| monthly_used | DECIMAL(15,2) | нет | нет | нет | да | 0 | нет | Месячный лимит |
| last_daily_reset | DATE | нет | нет | нет | да | CURRENT_DATE | нет | Последний сброс дневного |
| last_monthly_reset | DATE | нет | нет | нет | да | CURRENT_DATE | нет | Последний сброс месячного |
| version | BIGINT | нет | нет | нет | да | 0 | нет | Версия |

### limit-db: reservations
| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
|---------|-----|----|----|--------|----------|---------|-------|----------|
| transfer_id | BIGINT | да | нет | нет | да | нет | нет | ID перевода |
| sender_id | BIGINT | нет | нет | нет | да | нет | idx_reservations_sender_id | ID отправителя |
| amount | DECIMAL(15,2) | нет | нет | нет | да | нет | нет | Сумма |
| decision | VARCHAR(10) | нет | нет | нет | да | нет | нет | Решение |
| reason | VARCHAR(50) | нет | нет | нет | нет | нет | нет | Причина |
| state | VARCHAR(20) | нет | нет | нет | да | нет | нет | Состояние |
| reserved_on | DATE | нет | нет | нет | да | нет | нет | Дата резервирования |
| created_at | TIMESTAMP | нет | нет | нет | да | нет | нет | Дата создания |
| updated_at | TIMESTAMP | нет | нет | нет | да | нет | нет | Дата обновления |

### screening-db: blocked_senders
| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
|---------|-----|----|----|--------|----------|---------|-------|----------|
| id | BIGSERIAL | да | нет | нет | да | нет | idx_blocked_senders_sender_id | ID записи |
| sender_id | BIGINT | нет | нет | да | да | нет | idx_blocked_senders_sender_id | ID отправителя |
| reason | VARCHAR(255) | нет | нет | нет | да | нет | нет | Причина |
| blocked_at | TIMESTAMP | нет | нет | нет | да | NOW() | нет | Дата блокировки |

### screening-db: screening_checks
| Колонка | Тип | PK | FK | Unique | NOT NULL | Default | Index | Описание |
|---------|-----|----|----|--------|----------|---------|-------|----------|
| transfer_id | BIGINT | да | нет | нет | да | нет | нет | ID перевода |
| sender_id | BIGINT | нет | нет | нет | да | нет | idx_screening_checks_sender_id | ID отправителя |
| result | VARCHAR(10) | нет | нет | нет | да | нет | нет | Результат |
| reason | VARCHAR(50) | нет | нет | нет | нет | нет | нет | Причина |
| checked_at | TIMESTAMP | нет | нет | нет | да | нет | нет | Дата проверки |

---

## 3. КОНТРАКТЫ СООБЩЕНИЙ

### Формат сообщения: Transfer
| Поле | Тип | Nullable | Описание | Пример |
|------|-----|----------|----------|--------|
| id | Long | нет | Идентификатор перевода | 12345 |
| senderId | Long | нет | ID отправителя | 1001 |
| recipientId | Long | нет | ID получателя | 1002 |
| amount | BigDecimal | нет | Сумма | 5000.00 |
| currency | String | нет | Код валюты | RUB |
| status | String | нет | Статус | NEW |
| decidedBy | String | да | Кто принял решение | limit |
| messageInfo | String | да | Сообщение | APPROVED |
| createdAt | LocalDateTime | нет | Дата создания | 2024-01-15T10:30:00 |

### Канал: transfers
| Свойство | Значение |
|----------|----------|
| Тип | Kafka topic |
| Partitions | 3 |
| Cleanup policy | compact |
| Key | Long (transfer id) |
| Value | Transfer (JSON) |
| Сериализация ключа | LongSerializer |
| Сериализация значения | JsonSerializer |
| Отправители | transfer-service |
| Получатели | limit-service, screening-service, transfer-tracker |
| Группа потребителей | limit, screening, transfer-tracker |

### Канал: limit-results
| Свойство | Значение |
|----------|----------|
| Тип | Kafka topic |
| Partitions | 3 |
| Cleanup policy | compact |
| Key | Long (transfer id) |
| Value | Transfer (JSON) |
| Сериализация ключа | LongSerializer |
| Сериализация значения | JsonSerializer |
| Отправители | limit-service |
| Получатели | transfer-service (DecisionTopology) |

### Канал: screening-results
| Свойство | Значение |
|----------|----------|
| Тип | Kafka topic |
| Partitions | 3 |
| Cleanup policy | compact |
| Key | Long (transfer id) |
| Value | Transfer (JSON) |
| Сериализация ключа | LongSerializer |
| Сериализация значения | JsonSerializer |
| Отправители | screening-service |
| Получатели | transfer-service (DecisionTopology) |

### Канал: transfers.DLT
| Свойство | Значение |
|----------|----------|
| Тип | Kafka topic (dead letter) |
| Partitions | 3 |
| Key | Long |
| Value | JSON |
| Сериализация ключа | LongSerializer / ByteArraySerializer |
| Сериализация значения | JsonSerializer / ByteArraySerializer |
| Отправители | все сервисы |

### Канал: limit-results.DLT
| Свойство | Значение |
|----------|----------|
| Тип | Kafka topic (dead letter) |
| Partitions | 3 |
| Key | Long |
| Value | JSON |
| Сериализация ключа | LongSerializer / ByteArraySerializer |
| Сериализация значения | JsonSerializer / ByteArraySerializer |
| Отправители | limit-service |

### Канал: screening-results.DLT
| Свойство | Значение |
|----------|----------|
| Тип | Kafka topic (dead letter) |
| Partitions | 3 |
| Key | Long |
| Value | JSON |
| Сериализация ключа | LongSerializer / ByteArraySerializer |
| Сериализация значения | JsonSerializer / ByteArraySerializer |
| Отправители | screening-service |

---

## 4. РАСХОЖДЕНИЯ КОНТРАКТА API С КОДОМ

Расхождений нет. Файл контракта `openapi/transfer-api.yaml` полностью соответствует коду.

| Элемент | В контракте | В коде |
|---------|-------------|--------|
| TransferRequest | id, senderId, recipientId, amount, currency | id, senderId, recipientId, amount, currency |
| Transfer | id, senderId, recipientId, amount, currency, status, decidedBy, messageInfo, createdAt, updatedAt | id, senderId, recipientId, amount, currency, status, decidedBy, messageInfo, createdAt, updatedAt |
| TransferWithHistory | transfer, history | transfer, history |
| HistoryEntry | id, transferId, stage, status, decidedBy, messageInfo, changedAt | id, transferId, stage, status, decidedBy, messageInfo, changedAt |
| Problem | type, title, status, detail, instance | type, title, status, detail, instance |