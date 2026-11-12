## 1. ЦЕЛЬ ДОРАБОТКИ
Ввести единое для всех отправителей ограничение на максимальную сумму одного перевода и отклонять операции, превышающие этот порог, с отдельной понятной причиной отказа. Предоставить REST API для просмотра текущих значений всех лимитов и оперативного изменения дневного, месячного и разового лимитов без перезапуска сервисов.

---

## 2. ПРЕДПОСЫЛКИ
В текущей реализации контролируются только суммарные дневной (`50000.00`) и месячный (`200000.00`) лимиты отправителя (`limit-service/src/main/java/com/example/limit/service/LimitLedger.java`, `LimitLedger.decide`), из-за чего отправитель может израсходовать весь дневной лимит одной крупной операцией. Кроме того, пороговые значения жёстко заданы в конфигурационном файле (`limit-service/src/main/resources/application.yml`), не хранятся в БД и не могут быть просмотрены или изменены в runtime без переконфигурации и перезапуска `limit-service`.

---

## 3. SCOPE ИЗМЕНЕНИЙ
- Сервис `limit-service` (модифицируемый) — подключение веб-слоя и валидации, перенос хранения лимитов из конфигурации в БД, проверка максимальной суммы одного перевода и публикация REST API управления лимитами.
- Таблица БД `limit_settings` в `limit-db` (новый) — хранение актуальных глобальных значений разового, дневного и месячного лимитов.
- Эндпоинт `GET /limits` в `limit-service` (новый) — получение текущих значений всех лимитов.
- Эндпоинт `PUT /limits` в `limit-service` (новый) — изменение значений всех лимитов (дневной, месячный, максимальная сумма одного перевода).
- OpenAPI-спецификация `limit-service/src/main/resources/openapi/limit-api.yaml` (новый) — контракт REST API управления лимитами.
- Контракт сообщений Kafka в топиках `limit-results` и `transfers` (модифицируемый) — добавление нового значения причины отклонения `SINGLE_TRANSFER_LIMIT_EXCEEDED` в поле `messageInfo` без изменения структуры сообщения.
- Конфигурация `docker-compose.yml` (модифицируемый) — публикация HTTP-порта сервиса `limit-service` для внешнего доступа к API лимитов.

---

## 4. АРХИТЕКТУРНЫЕ РЕШЕНИЯ

### 4.1. Размещение REST API управления лимитами и хранилища настроек
В текущей системе веб-сервер и внешний HTTP-порт (`8080`) есть только у `transfer-service` (`docker-compose.yml`, `transfer-service/pom.xml`), тогда как доменом лимитов и БД `limit-db` владеет `limit-service` (`limit-service/pom.xml`).

- **Вариант А (рекомендуемый): Разместить REST API непосредственно в `limit-service`, хранить лимиты в `limit-db`.**
  - *Плюсы*: сохранение чётких границ домена (`limit-service` остаётся единственным владельцем данных и правил лимитирования); строгая консистентность (обновление в `limit-db` мгновенно применяется ко всем следующим проверяемым переводам); отсутствие синхронных межсервисных вызовов и дополнительных Kafka-топиков.
  - *Минусы*: требуется добавить веб-стек и валидацию в `limit-service`, выделить отдельный HTTP-порт (например, `8081`, чтобы не конфликтовать с `8080` у `transfer-service` при локальном запуске) и пробросить его в `docker-compose.yml`.

- **Вариант Б: Разместить публичные эндпоинты в `transfer-service` с синхронным HTTP-проксированием в `limit-service` (хранение в `limit-db`).**
  - *Плюсы*: единая точка входа (`:8080`) и единая OpenAPI-спецификация для внешних клиентов.
  - *Минусы*: всё равно требует поднятия веб-слоя в `limit-service`; вводит синхронную межсервисную связность (сейчас все взаимодействия строго асинхронные через Kafka), дублирование DTO/валидации и обработку ошибок недоступности `limit-service` (`502`/`503`/`504`).

- **Вариант В: Разместить REST API и таблицу лимитов в `transfer-service` (`transfer-db`), доставлять обновления в `limit-service` через compacted-топик Kafka.**
  - *Плюсы*: единая точка входа (`:8080`), `limit-service` остаётся без веб-слоя, сохраняется коммуникация исключительно через Kafka.
  - *Минусы*: нарушение границ ответственности (`transfer-service` хранит конфигурацию чужого домена); eventual consistency (после `200 OK` переводы ещё некоторое время проверяются по старым порогам); риск гонки при старте `limit-service`, если сообщения из `transfers` начнут обрабатываться раньше полной вычитки топика с настройками лимитов.

**Рекомендуемое решение**: Вариант А. Дальнейшая постановка описана для него.

### 4.2. Модель хранения глобальных лимитов в `limit-db`
- **Вариант 1 (рекомендуемый): Однострочная типизированная таблица `limit_settings` с фиксированным первичным ключом (`id = 1`) и полным обновлением через `PUT /limits`.**
  - *Плюсы*: СУБД гарантирует положительность значений и инвариант `single_transfer_max <= daily_max <= monthly_max` через `CHECK`-ограничения; оптимистическая блокировка одной строки исключает аномалию write skew при конкурентных обновлениях; чтение одной записи по PK внутри транзакции проверки перевода позволяет обойтись без распределённой инвалидации кэша при масштабировании `limit-service`.
  - *Минусы*: добавление новых видов глобальных лимитов в будущем потребует `ALTER TABLE`.

- **Вариант 2: Таблица формата «ключ–значение» (`limit_type`, `limit_value`).**
  - *Плюсы*: добавление нового вида лимита без изменения схемы таблицы.
  - *Минусы*: невозможно обеспечить соотношение между разными строками через SQL `CHECK`; при конкурентных запросах на уровне изоляции `READ COMMITTED` возможно нарушение иерархии лимитов без блокировки всей таблицы.

**Рекомендуемое решение**: Вариант 1.

---

## 5. ОПИСАНИЕ ИЗМЕНЕНИЙ

### 5.1. limit-service

#### Изменения в БД (`limit-db`)
- Добавить новую таблицу `limit_settings` через новую миграцию Liquibase с подключением в `limit-service/src/main/resources/db/changelog/changelog-master.yaml`:
  - `id` — `SMALLINT`, `PRIMARY KEY`, `NOT NULL`, `DEFAULT 1`
  - `single_transfer_max` — `DECIMAL(15,2)`, `NOT NULL`
  - `daily_max` — `DECIMAL(15,2)`, `NOT NULL`
  - `monthly_max` — `DECIMAL(15,2)`, `NOT NULL`
  - `updated_at` — `TIMESTAMP`, `NOT NULL`, `DEFAULT NOW()`
  - `version` — `BIGINT`, `NOT NULL`, `DEFAULT 0`
- Ограничения таблицы `limit_settings`:
  - `CHECK (id = 1)` — гарантия единственной записи глобальных настроек.
  - `CHECK (single_transfer_max > 0 AND daily_max > 0 AND monthly_max > 0)` — все лимиты строго положительные.
  - `CHECK (single_transfer_max <= daily_max AND daily_max <= monthly_max)` — непротиворечивость порогов между собой.
  - Тип `DECIMAL(15,2)` выбран в соответствии с размерностью денежных полей в существующих таблицах (`limit-service/src/main/resources/db/changelog/001-create-tables.sql`, `transfer-service/src/main/resources/db/changelog/001-create-tables.sql`).
- Существующие таблицы `sender_usage` и `reservations`: без изменений (существующая колонка `reservations.reason VARCHAR(50)` вмещает новый код отказа длиной 30 символов).
- Миграция начальных данных:
  - Заполнить строку `id = 1` значениями из текущей конфигурации (`limit-service/src/main/resources/application.yml`): `daily_max = 50000.00`, `monthly_max = 200000.00`.
  - Начальное значение `single_transfer_max` в коде отсутствует и **требует уточнения у бизнеса** (техническое значение по умолчанию до ответа бизнеса — `50000.00`, совпадающее с текущим `daily_max`, чтобы выкатка не меняла действующее поведение).
  - Удалить статические параметры `limits.daily-max` и `limits.monthly-max` из `limit-service/src/main/resources/application.yml`.
- Сетевые и веб-настройки сервиса:
  - Задать собственный порт веб-сервера `8081` в `limit-service/src/main/resources/application.yml` и пробросить `"8081:8081"` в `docker-compose.yml`.
  - Включить поддержку RFC 7807 (`application/problem+json`) и отключить `spring.jpa.open-in-view` единообразно с `transfer-service/src/main/resources/application.yml`.

#### Изменения в API
Оформить контракт также в спецификации `limit-service/src/main/resources/openapi/limit-api.yaml` (по аналогии с `transfer-service/src/main/resources/openapi/transfer-api.yaml`).

---
Эндпоинт: `GET /limits`
Описание: Возвращает актуальные значения всех глобальных лимитов и дату их последнего обновления.

Параметры запроса:
| Параметр | Источник (body/path/query) | Тип | Обязательный | Описание |
|---|---|---|---|---|
| — | — | — | — | Параметры отсутствуют |

Параметры ответа:
| Поле | Тип | Nullable | Описание |
|---|---|---|---|
| `singleTransferMax` | `number (decimal)` | `false` | Максимальная сумма одного перевода |
| `dailyMax` | `number (decimal)` | `false` | Дневной лимит на общую сумму переводов отправителя |
| `monthlyMax` | `number (decimal)` | `false` | Месячный лимит на общую сумму переводов отправителя |
| `updatedAt` | `string (date-time)` | `false` | Дата и время последнего сохранения лимитов |

Коды ответа:
| Код | Условие |
|---|---|
| `200` | Значения лимитов успешно получены |
| `500` | Внутренняя ошибка сервера (включая отсутствие записи конфигурации в БД), формат `application/problem+json` |

---
Эндпоинт: `PUT /limits`
Описание: Изменяет значения максимальной суммы одного перевода, дневного и месячного лимитов.

Параметры запроса:
| Параметр | Источник (body/path/query) | Тип | Обязательный | Описание |
|---|---|---|---|---|
| `singleTransferMax` | `body` | `number (decimal)` | Да | Максимальная сумма одного перевода (`> 0`, максимум 13 целых и 2 дробных знака — до `9999999999999.99`) |
| `dailyMax` | `body` | `number (decimal)` | Да | Дневной лимит отправителя (`> 0`, до `9999999999999.99`, не меньше `singleTransferMax`) |
| `monthlyMax` | `body` | `number (decimal)` | Да | Месячный лимит отправителя (`> 0`, до `9999999999999.99`, не меньше `dailyMax`) |

Параметры ответа:
| Поле | Тип | Nullable | Описание |
|---|---|---|---|
| `singleTransferMax` | `number (decimal)` | `false` | Обновлённая максимальная сумма одного перевода |
| `dailyMax` | `number (decimal)` | `false` | Обновлённый дневной лимит |
| `monthlyMax` | `number (decimal)` | `false` | Обновлённый месячный лимит |
| `updatedAt` | `string (date-time)` | `false` | Дата и время применения изменений |

Коды ответа:
| Код | Условие |
|---|---|
| `200` | Лимиты успешно обновлены |
| `400` | Невалидный JSON, отсутствуют обязательные поля, значения `<= 0`, превышена разрядность (13 целых / 2 дробных знака, ср. `transfer-service/src/main/java/com/example/transfer/controller/TransferRequest.java`, `TransferRequest`) или нарушено условие `singleTransferMax <= dailyMax <= monthlyMax` (`application/problem+json`) |
| `409` | Конфликт конкурентного обновления записи лимитов (`application/problem+json`) |
| `500` | Внутренняя ошибка сервера (`application/problem+json`) |

#### Изменения в бизнес-логике
*Новые правила:*
- При поступлении события `NEW` с положительной суммой (`amount > 0`), по которому ещё нет записи в `reservations`, система должна считывать актуальные значения `single_transfer_max`, `daily_max` и `monthly_max` из таблицы `limit_settings`.
- При условии `amount > single_transfer_max` система должна отклонить перевод с решением `DECLINED`, причиной `SINGLE_TRANSFER_LIMIT_EXCEEDED` и состоянием резерва `NONE`, сохранить запись в `reservations`, не увеличивать `daily_used` и `monthly_used` отправителя и опубликовать результат в топик `limit-results`.
- При условии `amount <= single_transfer_max` система должна продолжить проверку перевода по дневному (`daily_max`) и месячному (`monthly_max`) лимитам.
- При вызове `GET /limits` система должна прочитать запись из `limit_settings` и вернуть текущие значения всех трёх лимитов и `updatedAt`.
- При вызове `PUT /limits` с корректными параметрами (`0 < singleTransferMax <= dailyMax <= monthlyMax`, соблюдена разрядность `DECIMAL(15,2)`) система должна атомарно сохранить новые значения в `limit_settings`, обновить `updated_at` и вернуть обновлённое состояние.
- При вызове `PUT /limits` с нарушением обязательности полей, разрядности, положительности или иерархии `singleTransferMax <= dailyMax <= monthlyMax` система должна отклонить запрос со статусом `400 Bad Request` без модификации БД.
- При конкурентном вызове `PUT /limits` и обнаружении конфликта версий записи система должна предотвратить потерю обновлений и вернуть `409 Conflict`.

*Затрагиваемые существующие правила:*
- При проверке дневного и месячного лимитов система должна сравнивать накопленный расход с `daily_max` и `monthly_max` из БД вместо статических параметров конфигурации (`limit-service/src/main/java/com/example/limit/service/LimitLedger.java`, `LimitLedger.decide`).
- При одновременном нарушении нескольких условий система должна применять строгий порядок проверок и фиксировать первую причину отказа: `INVALID_AMOUNT` → `SINGLE_TRANSFER_LIMIT_EXCEEDED` → `DAILY_LIMIT_EXCEEDED` → `MONTHLY_LIMIT_EXCEEDED`.
- При получении дублирующего сообщения `NEW` по уже существующему в `reservations` переводу система должна возвращать ранее сохранённое решение без пересчёта по обновлённым лимитам (сохранение идемпотентности в `limit-service/src/main/java/com/example/limit/service/LimitLedger.java`, `LimitLedger.decide`).
- При уменьшении лимитов через `PUT /limits` ниже уже накопленных отправителем `daily_used` или `monthly_used` система должна оставить ранее зарезервированные и завершённые переводы без изменений, применяя новые пороги только к последующим событиям `NEW`; освобождение зарезервированных сумм при финальных статусах `CANCELLED` / `DENIED` работает без изменений (`limit-service/src/main/java/com/example/limit/service/LimitLedger.java`, `LimitLedger.settle`).

#### Изменения в контрактах сообщений
- Топик `limit-results`:
  - При отказе по разовому лимиту публикуется `Transfer` со значениями `status = "DECLINED"`, `decidedBy = "limit"`, `messageInfo = "SINGLE_TRANSFER_LIMIT_EXCEEDED"`.
- Топик `transfers` (результат штатной агрегации в `transfer-service/src/main/java/com/example/transfer/service/TransferDecisionService.java`, `TransferDecisionService.decide`):
  - Если `screening-service` вернул `APPROVED` → `status = "CANCELLED"`, `decidedBy = "limit"`, `messageInfo = "SINGLE_TRANSFER_LIMIT_EXCEEDED"`.
  - Если `screening-service` вернул `DECLINED` → `status = "DENIED"`, `decidedBy = null`, `messageInfo = "SINGLE_TRANSFER_LIMIT_EXCEEDED; SENDER_BLOCKED"`.

---

### 5.2. transfer-service
Изменений нет — сервис обрабатывает статус `DECLINED` от `limit-service` обобщённо и сохраняет произвольное строковое значение `messageInfo` в таблицы `transfers` и `transfer_history` (`transfer-service/src/main/java/com/example/transfer/service/TransferDecisionService.java`, `TransferDecisionService.decide`; `transfer-service/src/main/java/com/example/transfer/service/TransferTrackingService.java`, `TransferTrackingService.recordServiceResult`, `TransferTrackingService.applyFinalStatus`).

### 5.3. screening-service
Изменений нет — сервис проверяет только наличие `senderId` в таблице `blocked_senders` и не использует суммы переводов или лимиты (`screening-service/src/main/java/com/example/screening/service/ScreeningJournal.java`, `ScreeningJournal.check`).

### 5.4. transfer-common
Изменений нет — запись `Transfer` уже содержит строковые поля `status`, `decidedBy` и `messageInfo` (`transfer-common/src/main/java/com/example/common/Transfer.java`, `Transfer`).

---

## 6. ВЛИЯНИЕ СУЩЕСТВУЮЩИХ АНОМАЛИЙ

1. **Отсутствие учёта валюты при проверке лимитов** (`transfer-service/src/main/java/com/example/transfer/controller/TransferRequest.java`, `TransferRequest`; `limit-service/src/main/java/com/example/limit/service/LimitLedger.java`, `LimitLedger.decide`):
   - *Суть*: API переводов принимает любой код валюты формата `[A-Z]{3}` (по умолчанию `RUB`), но `limit-service` игнорирует `currency`, сравнивая `amount` напрямую с лимитами и складывая разнородные валюты в `daily_used` и `monthly_used`.
   - *Влияние на доработку*: новый порог `singleTransferMax` будет сравниваться с числовым `amount` без учёта валюты (например, перевод в `USD` или `EUR` будет проверен по рублёвому порогу как одноимённое число), а в контракте `/limits` валюта лимитов сейчас никак не отражена (см. раздел 8).

2. **Запись в `sender_usage` даже при отклонении перевода** (`limit-service/src/main/java/com/example/limit/service/LimitLedger.java`, `LimitLedger.decide`):
   - *Суть*: при отказе по `DAILY_LIMIT_EXCEEDED` или `MONTHLY_LIMIT_EXCEEDED` всё равно выполняется сохранение `sender_usage`, что создаёт нулевые записи для новых отправителей и увеличивает `version` при срабатывании `lazyReset` (`limit-service/src/main/java/com/example/limit/domain/SenderUsage.java`, `SenderUsage.lazyReset`).
   - *Влияние на доработку*: если разместить проверку `single_transfer_max` после загрузки и сохранения `sender_usage`, отклонённые сверхлимитные переводы будут создавать лишнюю нагрузку и вызывать конфликты оптимистической блокировки для параллельных валидных переводов того же отправителя. Проверку `single_transfer_max` следует выполнять до обращения к `sender_usage` (по аналогии с веткой `INVALID_AMOUNT`), либо не сохранять `sender_usage` при отказе.

3. **Партиционирование топика `transfers` по `transfer.id` вместо `senderId` и жёсткое окно join в 10 секунд** (`transfer-service/src/main/java/com/example/transfer/controller/TransferController.java`, `TransferController.publishNew`; `transfer-service/src/main/java/com/example/transfer/config/DecisionTopologyConfig.java`, `DecisionTopologyConfig.decisionStream`):
   - *Суть*: при масштабировании `limit-service` на несколько инстансов переводы одного отправителя попадают в разные партиции и конкурируют за строку `sender_usage` (для нового отправителя возможна ошибка уникальности PK, ретраи `DefaultErrorHandler` в `limit-service/src/main/java/com/example/limit/config/KafkaErrorHandlingConfig.java`, `KafkaErrorHandlingConfig.kafkaErrorHandler`), а при задержке обработки свыше 10 секунд событие пропускает `JoinWindows.ofTimeDifferenceWithNoGrace(Duration.ofSeconds(10))`, оставляя перевод в статусе `NEW` и резерв в состоянии `RESERVED` навсегда.
   - *Влияние на доработку*: операции обновления `PUT /limits` не должны брать длительных эксклюзивных блокировок на таблицу `limit_settings`, которые могли бы задержать транзакции чтения в `limit-service` дольше окна агрегации в Kafka Streams.

4. **Использование системной даты контейнера без явной бизнес-таймзоны** (`limit-service/src/main/java/com/example/limit/service/LimitLedger.java`, `LimitLedger.decide`, `LimitLedger.settle`):
   - *Суть*: сброс дневных и месячных лимитов использует `LocalDate.now()` дефолтной таймзоны JVM (в Docker-образе `eclipse-temurin:21-jre` — `UTC`).
   - *Влияние на доработку*: поле `updatedAt` в ответах `/limits` и момент суточного сброса накопленных сумм при проверке изменённых `dailyMax` / `monthlyMax` будут фиксироваться в системной таймзоне контейнера, если не задана единая таймзона.

---

## 7. КРИТЕРИИ ПРИЁМКИ

### Проверка максимальной суммы одного перевода
- Установлены `singleTransferMax = 15000.00`, `dailyMax = 50000.00`, `monthlyMax = 200000.00`, расход отправителя `0.00`; создаётся перевод на `14999.99` от незаблокированного отправителя → перевод одобряется (`COMPLETED`), `daily_used` и `monthly_used` равны `14999.99`.
- Граничное значение `=`: при тех же лимитах и нулевом расходе создаётся перевод ровно на `15000.00` → перевод одобряется (`COMPLETED`), `daily_used = 15000.00`.
- Граничное значение `+0.01`: при тех же лимитах создаётся перевод на `15000.01` → `limit-service` отклоняет операцию с `SINGLE_TRANSFER_LIMIT_EXCEEDED`, `daily_used` и `monthly_used` не увеличиваются (`0.00`), в `GET /transfers/{id}` этап `LIMIT` имеет `status = DECLINED` и `messageInfo = SINGLE_TRANSFER_LIMIT_EXCEEDED`, финальный статус перевода — `CANCELLED` (`decidedBy = limit`, `messageInfo = SINGLE_TRANSFER_LIMIT_EXCEEDED`).
- Приоритет причин отказа: `singleTransferMax = 15000.00`, `dailyMax = 50000.00`, `daily_used = 45000.00`; создаётся перевод на `20000.00` (превышены и разовый, и остаток дневного лимита) → причина отказа `SINGLE_TRANSFER_LIMIT_EXCEEDED`, `daily_used` остаётся `45000.00`.
- Перевод проходит разовый лимит, но превышает дневной (`singleTransferMax = 15000.00`, `dailyMax = 50000.00`, `daily_used = 45000.00`, сумма перевода `10000.00`) → причина отказа `DAILY_LIMIT_EXCEEDED`.
- Заблокированный отправитель (например, `senderId = 5` из `screening-service/src/main/resources/db/changelog/002-insert-test-data.sql`) отправляет перевод на сумму больше `singleTransferMax` → финальный статус перевода `DENIED`, `decidedBy = null`, `messageInfo = "SINGLE_TRANSFER_LIMIT_EXCEEDED; SENDER_BLOCKED"`.

### Управление лимитами через REST API
- Вызов `GET /limits` сразу после первого запуска на чистой БД → статус `200 OK`, возвращаются стартовые значения из миграции (`dailyMax = 50000.00`, `monthlyMax = 200000.00`, `singleTransferMax`, `updatedAt`).
- Вызов `PUT /limits` с телом `{"singleTransferMax": 10000.00, "dailyMax": 40000.00, "monthlyMax": 150000.00}` → статус `200 OK`, возвращаются новые значения и обновлённый `updatedAt`; последующий `GET /limits` (в том числе после перезапуска контейнера `limit-service`) возвращает те же значения.
- Динамическое применение лимитов: после уменьшения `singleTransferMax` до `10000.00` через `PUT /limits` создаётся перевод на `12000.00` → перевод отклоняется с `SINGLE_TRANSFER_LIMIT_EXCEEDED` без перезапуска сервисов.
- Граничные допустимые значения `PUT /limits`:
  - равенство всех трёх лимитов (`singleTransferMax = 50000.00`, `dailyMax = 50000.00`, `monthlyMax = 50000.00`) → `200 OK`;
  - минимальное значение (`0.01`, `0.01`, `0.01`) и максимальное значение (`9999999999999.99`, `9999999999999.99`, `9999999999999.99`) → `200 OK`.
- Валидация некорректных запросов `PUT /limits`:
  - `singleTransferMax > dailyMax` (например, `60000.00 > 50000.00`) → `400 Bad Request` (`application/problem+json`), значения в БД не изменены;
  - `dailyMax > monthlyMax` (например, `250000.00 > 200000.00`) → `400 Bad Request`, значения в БД не изменены;
  - любое поле `<= 0` (`0`, `-100`), отсутствует/`null`, содержит более 2 десятичных знаков (`100.005`) или более 13 целых разрядов → `400 Bad Request`, значения в БД не изменены.
- Снижение дневного лимита ниже текущего расхода: отправитель уже перевёл `40000.00` за день, затем через `PUT /limits` устанавливается `dailyMax = 30000.00` (`singleTransferMax = 10000.00`) → ранее выполненные переводы остаются `COMPLETED`, а новый перевод этого отправителя на `100.00` отклоняется с `DAILY_LIMIT_EXCEEDED`.
- Идемпотентность при смене лимитов: повторная доставка события `NEW` для перевода на `20000.00`, который уже был одобрен до снижения `singleTransferMax` до `10000.00` → возвращается прежнее решение `APPROVED`, повторного резервирования или отказа не происходит.

---

## 8. ОТКРЫТЫЕ ВОПРОСЫ К БИЗНЕСУ
1. **Стартовое значение максимальной суммы одного перевода**: какое начальное значение `singleTransferMax` должно быть установлено в миграции БД при первом развёртывании доработки (например, `50000.00` для сохранения текущего поведения или конкретный меньший порог)?
2. **Валюта лимитов и мультивалютные переводы**: в какой валюте задаются все три лимита (предположительно `RUB`), требуется ли передавать/возвращать код валюты в `/limits` и как система должна поступать с переводами, у которых `currency != RUB` (отклонять в `limit-service` с новой причиной отказа или ограничивать допустимые валюты на входе)?
3. **Нулевые значения лимитов (`0.00`)**: должны ли все лимиты быть строго больше нуля (`>= 0.01`), или бизнесу нужна возможность выставить `0.00` для экстренной приостановки всех переводов?
4. **Доступ и аудит изменений лимитов**: в текущем коде отсутствует аутентификация и авторизация. Кто будет вызывать `GET /limits` и `PUT /limits` (закрытый контур или требуется защита эндпоинтов), и нужно ли вести журнал аудита изменений лимитов (время, инициатор, старые и новые значения)?
5. **Индивидуальные лимиты в будущем**: останутся ли лимиты строго едиными для всех отправителей, или в перспективе планируются персональные лимиты для конкретных `senderId`?
6. **Точка входа API и частичное обновление**: допустимо ли обращение потребителей API лимитов к отдельному порту/сервису `limit-service`, или требуется единый внешний хост/порт с `transfer-service`; а также достаточно ли полной перезаписи всех трёх значений (`PUT /limits`), или необходим метод частичного изменения отдельных полей (`PATCH /limits`)?
7. **Часовой пояс расчёта лимитов**: в какой таймзоне (например, `Europe/Moscow` или `UTC`) должны определяться границы календарных суток и месяца для сброса лимитов и отображаться поле `updatedAt`?