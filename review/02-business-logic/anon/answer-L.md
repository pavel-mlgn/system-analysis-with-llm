# Анализ бизнес-логики системы обработки переводов

## 1. ТОЧКИ ВХОДА

### 1.1. HTTP-эндпоинт: создание перевода

**ТОЧКА ВХОДА:** `TransferController.create(TransferRequest)`

**Триггер:** HTTP POST запрос на `/transfers` от внешнего клиента (UI/партнёрская система).

**Алгоритм (бизнес-язык):**
1. Получены данные нового перевода: отправитель, получатель, сумма, валюта.
2. Проверить — нет ли уже перевода с таким же ID.
3. Если существует:
   - Сравнить параметры (сумма, отправитель, получатель, валюта).
   - Если параметры совпадают — вернуть существующий перевод (идемпотентность).
   - Если отличаются — ошибка 409 Conflict.
4. Если не существует:
   - Сохранить перевод в статусе `NEW`.
   - Записать историю: стадия `NEW`, статус `NEW`.
   - Опубликовать событие `Transfer` со статусом `NEW` в топик `transfers`.
   - Вернуть созданный перевод со статусом 201.

**Бизнес-правила:**
- **БП-1:** Если перевод с таким ID уже существует с теми же параметрами — возвращать существующий (идемпотентность).
- **БП-2:** Если перевод с таким ID существует, но параметры отличаются — возвращать ошибку 409 Conflict.
- **БП-3:** Сумма перевода должна быть положительной (валидируется аннотацией `@Positive`).
- **БП-4:** Валюта — трёхбуквенный код (валидируется `@Pattern("[A-Z]{3}")`).

**Данные:**
- Читается: `transfers` (по ID), `transfer_history` (по ID + стадия).
- Изменяется: `transfers` (INSERT), `transfer_history` (INSERT).

**Исходы:**
- `201 Created` — перевод создан.
- `200 OK` — перевод уже существовал, возвращён.
- `409 Conflict` — конфликт параметров.
- `503 Service Unavailable` — Kafka недоступна, перевод сохранён (сохраняется в БД, но не опубликован).

---

### 1.2. Kafka Consumer (limit-service): обработка новых переводов

**ТОЧКА ВХОДА:** `TransferEventsListener.onEvent(Transfer)`

**Триггер:** Сообщение из топика `transfers` со статусом `NEW`.

**Алгоритм:**
1. Получить перевод из топика `transfers`.
2. Если статус — `NEW`:
   - Вызвать `LimitLedger.decide(transfer)`.
   - В `LimitLedger`:
     - Проверить дубликат в `reservations`.
     - Если дубликат — вернуть сохранённое решение.
     - Получить/создать `SenderUsage` для отправителя.
     - Сбросить дневной/месячный счётчики, если наступил новый день/месяц.
     - Проверить: `dailyUsed + amount <= dailyMax` (50 000).
     - Проверить: `monthlyUsed + amount <= monthlyMax` (200 000).
     - Если лимит превышен — `DECLINED` с причиной (`DAILY_LIMIT_EXCEEDED` или `MONTHLY_LIMIT_EXCEEDED`).
     - Если лимит не превышен — зарезервировать сумму, `APPROVED`.
   - Отправить результат в `limit-results`.
3. Иначе (финальный статус) — вызвать `ledger.settle(transfer)`.

**Бизнес-правила:**
- **БП-5:** Дневной лимит — 50 000 на отправителя.
- **БП-6:** Месячный лимит — 200 000 на отправителя.
- **БП-7:** При новом дне/месяце счётчики обнуляются (lazy reset).
- **БП-8:** Повторная обработка того же перевода — возвращать сохранённое решение (резервирование идемпотентно).
- **БП-9:** Сумма <= 0 — автоматический `DECLINED` с причиной `INVALID_AMOUNT`.

**Данные:**
- Читается: `reservations` (по transferId), `sender_usage` (по senderId).
- Изменяется: `reservations` (INSERT), `sender_usage` (UPDATE — reserve).

**Исходы:**
- `APPROVED` — лимиты соблюдены.
- `DECLINED` — превышение лимита или некорректная сумма.
- `DUPLICATE` — решение уже принято.

---

### 1.3. Kafka Consumer (screening-service): проверка отправителя

**ТОЧКА ВХОДА:** `TransferEventsListener.onEvent(Transfer)`

**Триггер:** Сообщение из топика `transfers` со статусом `NEW`.

**Алгоритм:**
1. Получить перевод из топика `transfers`.
2. Если статус — `NEW`:
   - Вызвать `ScreeningJournal.check(transfer)`.
   - В `ScreeningJournal`:
     - Проверить дубликат в `screening_checks`.
     - Если дубликат — вернуть сохранённый результат.
     - Проверить `blocked_senders` по senderId.
     - Если отправитель заблокирован — `DECLINED` с причиной `SENDER_BLOCKED`.
     - Иначе — `APPROVED`.
   - Отправить результат в `screening-results`.
3. Иначе — игнорировать (screening не хранит резервы).

**Бизнес-правила:**
- **БП-10:** Если отправитель присутствует в списке заблокированных — перевод отклоняется.
- **БП-11:** Причина блокировки сохраняется в аудите (`SENDER_BLOCKED`).
- **БП-12:** Повторная проверка — возвращать сохранённый результат.

**Данные:**
- Читается: `screening_checks` (по transferId), `blocked_senders` (по senderId).
- Изменяется: `screening_checks` (INSERT).

**Исходы:**
- `APPROVED` — отправитель не заблокирован.
- `DECLINED` — отправитель заблокирован.

---

### 1.4. Kafka Consumer (transfer-service): сборка финального решения

**ТОЧКА ВХОДА:** `TransferEventListener.onEvent(Transfer, topic)`

**Триггер:** Сообщение из `limit-results` или `screening-results` или `transfers`.

**Алгоритм (по топикам):**
- **limit-results / screening-results:**
  1. Записать результат сервиса в историю (`recordServiceResult`).
  2. Проверить — есть ли уже запись для этой стадии.
  3. Сохранить историю, обновить `updatedAt` перевода.
- **transfers (финальный статус):**
  1. Если стадия `FINAL` уже записана — пропустить (дубликат).
  2. Обновить статус перевода (`COMPLETED`, `CANCELLED`, `DENIED`).
  3. Записать финальную запись в историю.

**Бизнес-правила:**
- **БП-13:** Результаты сервисов (limit/screening) — записываются однократно (по стадии).
- **БП-14:** Финальный статус применяется однократно (контроль по `FINAL` стадии).

**Данные:**
- Читается: `transfer_history` (exists check).
- Изменяется: `transfer_history` (INSERT), `transfers` (UPDATE статуса).

---

### 1.5. Kafka Streams: объединение результатов

**ТОЧКА ВХОДА:** `DecisionTopologyConfig.decisionStream`

**Триггер:** Наличие результатов в `limit-results` и `screening-results` в пределах 10 секунд.

**Алгоритм:**
1. Поток из `limit-results` соединяется с потоком из `screening-results` по `transferId`.
2. Окно — 10 секунд.
3. Вызвать `TransferDecisionService.decide(limitResult, screeningResult)`.
4. Логика решения (см. ниже).
5. Результат публикуется в `transfers` (финальный статус).

**Логика `decide()` — ТАБЛИЦА РЕШЕНИЙ:**

| limitResult | screeningResult | Итоговый статус | decidedBy | Примечание |
|-------------|-----------------|-----------------|-----------|------------|
| APPROVED | APPROVED | COMPLETED | — | Перевод выполнен |
| DECLINED | DECLINED | DENIED | — | Оба отказали |
| DECLINED | APPROVED | CANCELLED | limit | Лимиты — отмена |
| APPROVED | DECLINED | CANCELLED | screening | Скрининг — отмена |
| *любой другой | *любой другой | CANCELLED | null | UNEXPECTED_RESULT |

**Бизнес-правила:**
- **БП-15:** Перевод выполняется только при `APPROVED` от обоих сервисов.
- **БП-16:** При отказе обоих — финальный статус `DENIED` (с указанием причин).
- **БП-17:** При отказе одного — `CANCELLED` с указанием сервиса-инициатора.
- **БП-18:** Неизвестная комбинация — `CANCELLED` с флагом `UNEXPECTED_RESULT`.

---

### 1.6. Задача по расписанию (неявная)

**ТОЧКА ВХОДА:** `SenderUsage.lazyReset(LocalDate)`

**Триггер:** Обработка каждого запроса (внутри `decide` и `release`).

**Алгоритм:**
- Сброс дневного счётчика, если `today != lastDailyReset`.
- Сброс месячного счётчика, если изменился месяц.

**Бизнес-правила:**
- **БП-19:** Сброс происходит лениво — при обращении, а не по крону.
- **БП-20:** Месячный сброс — по календарному месяцу (YearMonth).

---

## 2. СВОДНЫЙ РЕЕСТР БИЗНЕС-ПРАВИЛ

| ID | Правило | Сервис |
|----|---------|--------|
| БП-1 | Идемпотентность создания перевода: одинаковые параметры → существующий | transfer-service |
| БП-2 | Конфликт параметров → 409 | transfer-service |
| БП-3 | Сумма > 0 | transfer-service (валидация) |
| БП-4 | Валюта — 3 заглавные буквы | transfer-service (валидация) |
| БП-5 | Дневной лимит: 50 000 | limit-service |
| БП-6 | Месячный лимит: 200 000 | limit-service |
| БП-7 | Lazy reset счётчиков при смене дня/месяца | limit-service |
| БП-8 | Дубликат решения → возвращать сохранённое | limit-service |
| БП-9 | INVALID_AMOUNT при сумме <= 0 | limit-service |
| БП-10 | Блокировка отправителя → DECLINED | screening-service |
| БП-11 | Причина SENDER_BLOCKED в аудите | screening-service |
| БП-12 | Дубликат проверки → возвращать сохранённое | screening-service |
| БП-13 | Однократная запись результата (limit/screening) | transfer-service |
| БП-14 | Однократная запись FINAL | transfer-service |
| БП-15 | COMPLETED = APPROVED + APPROVED | transfer-service |
| БП-16 | DENIED = DECLINED + DECLINED | transfer-service |
| БП-17 | CANCELLED = один отказал | transfer-service |
| БП-18 | UNEXPECTED_RESULT для неизвестных комбинаций | transfer-service |
| БП-19 | Lazy reset (не крон) | limit-service |
| БП-20 | Месячный сброс по YearMonth | limit-service |

---

## 3. ТАБЛИЦЫ РЕШЕНИЙ

### 3.1. Комбинация результатов limit/screening

**Место принятия решения:** `TransferDecisionService.decide()`

| limitResult | screeningResult | Итог | decidedBy | messageInfo |
|-------------|-----------------|------|-----------|-------------|
| APPROVED | APPROVED | COMPLETED | null | — |
| APPROVED | DECLINED | CANCELLED | screening | screeningResult.messageInfo() |
| DECLINED | APPROVED | CANCELLED | limit | limitResult.messageInfo() |
| DECLINED | DECLINED | DENIED | null | limit + "; " + screening |
| APPROVED | APPROVED (дубль) | COMPLETED | null | — |
| *любой | *любой (не описан выше) | CANCELLED | null | UNEXPECTED_RESULT |

### 3.2. Решение limit-service

**Место принятия решения:** `LimitLedger.decide()`

| Условие | Результат | Причина |
|---------|-----------|---------|
| exists(reservation) | APPROVED/DECLINED (из БД) | — |
| amount <= 0 | DECLINED | INVALID_AMOUNT |
| dailyUsed + amount > dailyMax | DECLINED | DAILY_LIMIT_EXCEEDED |
| monthlyUsed + amount > monthlyMax | DECLINED | MONTHLY_LIMIT_EXCEEDED |
| Иначе | APPROVED | — |

### 3.3. Решение screening-service

**Место принятия решения:** `ScreeningJournal.check()`

| Условие | Результат | Причина |
|---------|-----------|---------|
| exists(check) | APPROVED/DECLINED (из БД) | — |
| blocked_senders.contains(senderId) | DECLINED | SENDER_BLOCKED |
| Иначе | APPROVED | — |

---

## 4. РЕЕСТР АНОМАЛИЙ

### Критические

| ID | Описание | Сценарий | Последствие | Сервис | Код |
|----|----------|----------|-------------|--------|-----|
| A-1 | Нет блокировки при `amount == null` в `decide()` | Передача null суммы (невозможно при валидации, но если обойти) | NullPointerException, перевод не обработан | limit-service | `LimitLedger.decide()` |
| A-2 | `SenderUsage.reserve()` вызывается до проверки лимитов | Нарушение порядка — счётчик увеличивается даже при отказе | Данные usage расходятся с реальностью | limit-service | `LimitLedger.decide()` — вызов `usage.reserve()` внутри блока else |

### Средние

| ID | Описание | Сценарий | Последствие | Сервис | Код |
|----|----------|----------|-------------|--------|-----|
| A-3 | `lazyReset` не вызывается при `release()` если `reservedOn` != today | Отпуск резерва в другой день — дневной лимит занижен | Пользователь не может использовать дневной лимит полностью | limit-service | `SenderUsage.release()` |
| A-4 | Нет проверки на отрицательный `amount` при `release()` | Баг или ручная операция — отрицательное освобождение | dailyUsed/monthlyUsed могут стать отрицательными | limit-service | `SenderUsage.release()` |
| A-5 | `transferRepository.saveAndFlush()` + `historyRepository.save()` не атомарны | Crash после save, но до save history | Перевод без истории, расходится состояние | transfer-service | `TransferTrackingService.createTransfer()` |
| A-6 | `DecisionTopologyConfig` — нет обработки ошибок в `decide()` | Исключение в `decide()` — поток останавливается | Все последующие переводы зависают | transfer-service | `TransferDecisionService.decide()` |

### Низкие

| ID | Описание | Сценарий | Последствие | Сервис | Код |
|----|----------|----------|-------------|--------|-----|
| A-7 | `screening` не обрабатывает финальные статусы | Отправка COMPLETED в screening — логирование без действия | Шум в логах, но без бизнес-эффекта | screening-service | `TransferEventsListener.onEvent()` |
| A-8 | `existsByTransferIdAndStage` — запрос к БД при каждом статусе | Дубликат результата → лишний запрос | Незначительное замедление | transfer-service | `TransferTrackingService.recordServiceResult()` |
| A-9 | Нет индекса на `reservations.state` | Поиск активных резервов (isReserved) | Full scan при большом объёме | limit-service | `Reservation.isReserved()` |
| A-10 | `createdAt` в `TransferEntity` не уникален | Два перевода с одинаковым ID — разные даты | Нарушение контракта (ID — PK) | transfer-service | БД-ограничение |

---

## 5. ЗАМЕЧАНИЯ К АРХИТЕКТУРЕ (вне рамок, но важно)

- **Разделение БД:** 3 отдельных БД (transfer, limit, screening) — усложняет распределённые транзакции. При падении после записи в limit-db, но до transfer-db — рассинхрон.
- **Event-driven + REST:** REST создаёт перевод, но финальный статус зависит от Kafka Streams. Нет SAGA-компенсации при сбоях.
- **Нет мониторинга:** Нет health-check'ов для бизнес-метрик (количество отказов, среднее время решения).
- **`transfer-service` — владелец ID:** ID генерируется клиентом, что перекладывает ответственность за уникальность на клиента.

---

**Анализ выполнен на основе приложенного кода. Все ссылки на код — в скобках как справка.**