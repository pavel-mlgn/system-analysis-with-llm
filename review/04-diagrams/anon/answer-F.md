### ДИАГРАММА 1: Component diagram (C4)

Диаграмма отображает контейнеры системы (`transfer-service`, `limit-service`, `screening-service`, брокер Kafka и три базы данных PostgreSQL из `docker-compose.yml`), их внутренние компоненты и каналы взаимодействия с указанием протоколов, портов, эндпоинтов и топиков. Внешние по отношению к проекту сторонние системы в коде не найдены, источником запросов выступает HTTP-клиент.

```mermaid
flowchart TB
    Client["HTTP-клиент (внешний инициатор)"]

    subgraph transfer_service ["Контейнер: transfer-service (Spring Boot, порт 8080)"]
        TC["TransferController"]
        TTS["TransferTrackingService"]
        TEL["TransferEventListener (groupId: transfer-tracker)"]
        DTC["DecisionTopologyConfig.decisionStream (Kafka Streams)"]
        TDS["TransferDecisionService"]
        TR["TransferRepository / TransferHistoryRepository"]
        TEH["KafkaErrorHandlingConfig (DefaultErrorHandler)"]
    end

    subgraph limit_service ["Контейнер: limit-service (Spring Boot)"]
        LEL["TransferEventsListener (groupId: limit)"]
        LS["LimitService"]
        LL["LimitLedger"]
        LR["SenderUsageRepository / ReservationRepository"]
        LEH["KafkaErrorHandlingConfig (DefaultErrorHandler)"]
    end

    subgraph screening_service ["Контейнер: screening-service (Spring Boot)"]
        SEL["TransferEventsListener (groupId: screening)"]
        SS["ScreeningService"]
        SJ["ScreeningJournal"]
        SR["BlockedSenderRepository / ScreeningCheckRepository"]
        SEH["KafkaErrorHandlingConfig (DefaultErrorHandler)"]
    end

    subgraph broker ["Контейнер: broker (Confluent Kafka 7.6.0, порты 9092 / 29092)"]
        T_TRANSFERS[("Топик: transfers (3 partitions, compact)")]
        T_LIMIT[("Топик: limit-results (3 partitions, compact)")]
        T_SCREENING[("Топик: screening-results (3 partitions, compact)")]
        T_DLT[("Топики DLT: transfers.DLT, limit-results.DLT, screening-results.DLT")]
    end

    subgraph transfer_db ["Контейнер: transfer-db (PostgreSQL 16, порт 5434:5432)"]
        DB_T[("БД: transferdb (таблицы: transfers, transfer_history)")]
    end

    subgraph limit_db ["Контейнер: limit-db (PostgreSQL 16, порт 5432:5432)"]
        DB_L[("БД: limitdb (таблицы: sender_usage, reservations)")]
    end

    subgraph screening_db ["Контейнер: screening-db (PostgreSQL 16, порт 5433:5432)"]
        DB_S[("БД: screeningdb (таблицы: blocked_senders, screening_checks)")]
    end

    Client -->|"HTTP REST (POST /transfers, GET /transfers, GET /transfers/{id})"| TC
    TC -->|"Вызов методов сервиса"| TTS
    TC -->|"Чтение переводов и истории"| TR
    TTS -->|"Сохранение состояния и истории"| TR
    TR -->|"JDBC PostgreSQL (transfer-db:5432/transferdb)"| DB_T

    TC -->|"Kafka Producer TCP (broker:29092), топик: transfers"| T_TRANSFERS

    T_TRANSFERS -->|"Kafka Consumer TCP (broker:29092), топик: transfers"| LEL
    LEL -->|"Обработка события"| LS
    LS -->|"Расчет и фиксация лимитов"| LL
    LL -->|"Чтение и запись резервов"| LR
    LR -->|"JDBC PostgreSQL (limit-db:5432/limitdb)"| DB_L
    LS -->|"Kafka Producer TCP (broker:29092), топик: limit-results"| T_LIMIT

    T_TRANSFERS -->|"Kafka Consumer TCP (broker:29092), топик: transfers"| SEL
    SEL -->|"Обработка события"| SS
    SS -->|"Проверка отправителя"| SJ
    SJ -->|"Чтение блокировок и запись проверок"| SR
    SR -->|"JDBC PostgreSQL (screening-db:5432/screeningdb)"| DB_S
    SS -->|"Kafka Producer TCP (broker:29092), топик: screening-results"| T_SCREENING

    T_LIMIT -->|"Kafka Streams TCP (broker:29092), топик: limit-results"| DTC
    T_SCREENING -->|"Kafka Streams TCP (broker:29092), топик: screening-results"| DTC
    DTC -->|"Агрегация решений (decide)"| TDS
    DTC -->|"Kafka Streams Producer TCP (broker:29092), топик: transfers"| T_TRANSFERS

    T_LIMIT -->|"Kafka Consumer TCP (broker:29092), топик: limit-results"| TEL
    T_SCREENING -->|"Kafka Consumer TCP (broker:29092), топик: screening-results"| TEL
    T_TRANSFERS -->|"Kafka Consumer TCP (broker:29092), топик: transfers"| TEL
    TEL -->|"Запись этапов и финального статуса"| TTS

    TEH -.->|"Kafka Producer TCP (broker:29092), при ошибке обработки"| T_DLT
    LEH -.->|"Kafka Producer TCP (broker:29092), при ошибке обработки"| T_DLT
    SEH -.->|"Kafka Producer TCP (broker:29092), при ошибке обработки"| T_DLT
```

### ДИАГРАММА 2: Sequence diagram — основной успешный сценарий (happy path)

Диаграмма показывает успешный сквозной сценарий создания перевода через `TransferController.create`, параллельное резервирование лимита в `limit-service` и проверку отправителя в `screening-service`, объединение результатов в Kafka Streams (`DecisionTopologyConfig.decisionStream`) со статусом `COMPLETED` и фиксацию резерва (`COMMITTED`).

```mermaid
sequenceDiagram
    autonumber
    actor Client as "HTTP-клиент"
    participant TC as "TransferController (transfer-service)"
    participant TTS as "TransferTrackingService (transfer-service)"
    participant TDB as "transferdb (PostgreSQL)"
    participant KT as "Топик: transfers (Kafka)"
    participant LEL as "TransferEventsListener / LimitService (limit-service)"
    participant LL as "LimitLedger (limit-service)"
    participant LDB as "limitdb (PostgreSQL)"
    participant KLR as "Топик: limit-results (Kafka)"
    participant SEL as "TransferEventsListener / ScreeningService (screening-service)"
    participant SJ as "ScreeningJournal (screening-service)"
    participant SDB as "screeningdb (PostgreSQL)"
    participant KSR as "Топик: screening-results (Kafka)"
    participant DS as "DecisionTopologyConfig / TransferDecisionService"
    participant TEL as "TransferEventListener (transfer-service)"

    Client->>TC: POST /transfers (TransferRequest)
    TC->>TDB: TransferRepository.findById(request.id())
    TDB-->>TC: Optional.empty()
    TC->>TTS: createTransfer(request)
    TTS->>TDB: TransferRepository.saveAndFlush(TransferEntity status=NEW)
    TTS->>TDB: TransferHistoryRepository.save(stage=NEW, status=NEW)
    TTS-->>TC: TransferEntity (status=NEW)
    TC->>KT: publishNew() -> KafkaTemplate.send("transfers", id, Transfer status=NEW)
    KT-->>TC: SendResult (ack)
    TC-->>Client: 201 Created (TransferEntity status=NEW)

    par Проверка и резервирование лимита в limit-service
        KT->>LEL: onEvent(Transfer status=NEW) -> onNewTransfer(transfer)
        LEL->>LL: decide(transfer)
        LL->>LDB: ReservationRepository.findById(id)
        LDB-->>LL: Optional.empty()
        LL->>LDB: SenderUsageRepository.findById(senderId)
        LDB-->>LL: SenderUsage (или новый объект)
        Note over LL: lazyReset(today), проверка dailyMax и monthlyMax,<br/>usage.reserve(amount), reservation.approve() (state=RESERVED)
        LL->>LDB: SenderUsageRepository.save(usage)
        LL->>LDB: ReservationRepository.save(reservation decision=APPROVED, state=RESERVED)
        LL-->>LEL: Reservation (APPROVED, RESERVED)
        LEL->>KLR: sendAndWait() -> KafkaTemplate.send("limit-results", id, Transfer status=APPROVED, decidedBy=limit)
    and Проверка отправителя в screening-service
        KT->>SEL: onEvent(Transfer status=NEW) -> onNewTransfer(transfer)
        SEL->>SJ: check(transfer)
        SJ->>SDB: ScreeningCheckRepository.findById(id)
        SDB-->>SJ: Optional.empty()
        SJ->>SDB: BlockedSenderRepository.findBySenderId(senderId)
        SDB-->>SJ: Optional.empty()
        SJ->>SDB: ScreeningCheckRepository.save(result=APPROVED)
        SJ-->>SEL: ScreeningCheck (APPROVED)
        SEL->>KSR: sendAndWait() -> KafkaTemplate.send("screening-results", id, Transfer status=APPROVED, decidedBy=screening)
    end

    par Сохранение промежуточных результатов в историю
        KLR->>TEL: onEvent(Transfer status=APPROVED, topic="limit-results")
        TEL->>TTS: recordServiceResult(transfer, HistoryStage.LIMIT)
        TTS->>TDB: TransferHistoryRepository.save(stage=LIMIT, status=APPROVED) и update transfers.updated_at
    and
        KSR->>TEL: onEvent(Transfer status=APPROVED, topic="screening-results")
        TEL->>TTS: recordServiceResult(transfer, HistoryStage.SCREENING)
        TTS->>TDB: TransferHistoryRepository.save(stage=SCREENING, status=APPROVED) и update transfers.updated_at
    and Объединение решений в Kafka Streams (JoinWindows 10s)
        KLR->>DS: stream("limit-results")
        KSR->>DS: stream("screening-results")
        Note over DS: TransferDecisionService.decide(limitResult, screeningResult)<br/>Оба APPROVED -> status=COMPLETED
        DS->>KT: to("transfers") (Transfer status=COMPLETED)
    end

    par Обработка финального статуса COMPLETED
        KT->>TEL: onEvent(Transfer status=COMPLETED, topic="transfers")
        TEL->>TTS: applyFinalStatus(transfer)
        TTS->>TDB: TransferRepository.save(status=COMPLETED)
        TTS->>TDB: TransferHistoryRepository.save(stage=FINAL, status=COMPLETED)
    and Подтверждение резерва в limit-service
        KT->>LEL: onEvent(Transfer status=COMPLETED) -> onFinalStatus(transfer)
        LEL->>LL: settle(transfer)
        LL->>LDB: ReservationRepository.findById(id)
        LDB-->>LL: Reservation (state=RESERVED)
        Note over LL: reservation.markCommitted() -> state=COMMITTED
        LL->>LDB: ReservationRepository.save(reservation state=COMMITTED)
    and Игнорирование финального статуса в screening-service
        KT->>SEL: onEvent(Transfer status=COMPLETED)
        Note over SEL: Логирование: screening holds no reservations, nothing to do
    end
```

### ДИАГРАММА 3: Sequence diagram — основной сценарий отказа

Диаграмма иллюстрирует сценарий отказа с компенсацией: `limit-service` одобряет перевод и резервирует средства отправителя (`state=RESERVED`), однако `screening-service` отклоняет перевод из-за нахождения отправителя в списке заблокированных (`SENDER_BLOCKED`), после чего `TransferDecisionService.decide` формирует статус `CANCELLED`, а `limit-service` в методе `LimitLedger.settle` освобождает ранее зарезервированный лимит (`SenderUsage.release`, `state=RELEASED`).

```mermaid
sequenceDiagram
    autonumber
    actor Client as "HTTP-клиент"
    participant TC as "TransferController (transfer-service)"
    participant TTS as "TransferTrackingService (transfer-service)"
    participant TDB as "transferdb (PostgreSQL)"
    participant KT as "Топик: transfers (Kafka)"
    participant LEL as "TransferEventsListener / LimitService (limit-service)"
    participant LL as "LimitLedger (limit-service)"
    participant LDB as "limitdb (PostgreSQL)"
    participant KLR as "Топик: limit-results (Kafka)"
    participant SEL as "TransferEventsListener / ScreeningService (screening-service)"
    participant SJ as "ScreeningJournal (screening-service)"
    participant SDB as "screeningdb (PostgreSQL)"
    participant KSR as "Топик: screening-results (Kafka)"
    participant DS as "DecisionTopologyConfig / TransferDecisionService"
    participant TEL as "TransferEventListener (transfer-service)"

    Client->>TC: POST /transfers (TransferRequest с заблокированным senderId)
    TC->>TTS: createTransfer(request)
    TTS->>TDB: Сохранение TransferEntity (status=NEW) и TransferHistoryEntry (stage=NEW)
    TTS-->>TC: TransferEntity (status=NEW)
    TC->>KT: publishNew() -> KafkaTemplate.send("transfers", id, Transfer status=NEW)
    TC-->>Client: 201 Created (TransferEntity status=NEW)

    par Успешное резервирование лимита в limit-service
        KT->>LEL: onEvent(Transfer status=NEW) -> onNewTransfer(transfer)
        LEL->>LL: decide(transfer)
        Note over LL: Лимит не превышен:<br/>usage.reserve(amount) увеличивает dailyUsed и monthlyUsed,<br/>reservation.approve() устанавливает state=RESERVED
        LL->>LDB: SenderUsageRepository.save(usage)
        LL->>LDB: ReservationRepository.save(decision=APPROVED, state=RESERVED)
        LEL->>KLR: sendAndWait("limit-results", Transfer status=APPROVED, decidedBy=limit)
    and Отказ при проверке отправителя в screening-service
        KT->>SEL: onEvent(Transfer status=NEW) -> onNewTransfer(transfer)
        SEL->>SJ: check(transfer)
        SJ->>SDB: BlockedSenderRepository.findBySenderId(senderId)
        SDB-->>SJ: Optional.of(BlockedSender)
        SJ->>SDB: ScreeningCheckRepository.save(result=DECLINED, reason="SENDER_BLOCKED")
        SJ-->>SEL: ScreeningCheck (DECLINED, SENDER_BLOCKED)
        SEL->>KSR: sendAndWait("screening-results", Transfer status=DECLINED, decidedBy=screening, messageInfo="SENDER_BLOCKED")
    end

    par Запись промежуточных результатов в transferdb
        KLR->>TEL: onEvent(status=APPROVED, topic="limit-results")
        TEL->>TTS: recordServiceResult(transfer, HistoryStage.LIMIT)
        TTS->>TDB: TransferHistoryRepository.save(stage=LIMIT, status=APPROVED)
    and
        KSR->>TEL: onEvent(status=DECLINED, topic="screening-results")
        TEL->>TTS: recordServiceResult(transfer, HistoryStage.SCREENING)
        TTS->>TDB: TransferHistoryRepository.save(stage=SCREENING, status=DECLINED, messageInfo="SENDER_BLOCKED")
    and Принятие решения об отмене в Kafka Streams
        KLR->>DS: stream("limit-results") (status=APPROVED)
        KSR->>DS: stream("screening-results") (status=DECLINED)
        Note over DS: TransferDecisionService.decide:<br/>limitApproved && screeningDeclined -> status=CANCELLED, decidedBy=screening
        DS->>KT: to("transfers") (Transfer status=CANCELLED, decidedBy=screening, messageInfo="SENDER_BLOCKED")
    end

    par Фиксация статуса CANCELLED в transfer-service
        KT->>TEL: onEvent(Transfer status=CANCELLED, topic="transfers")
        TEL->>TTS: applyFinalStatus(transfer)
        TTS->>TDB: TransferRepository.save(status=CANCELLED, decidedBy=screening, messageInfo="SENDER_BLOCKED")
        TTS->>TDB: TransferHistoryRepository.save(stage=FINAL, status=CANCELLED)
    and Компенсация (возврат) зарезервированного лимита в limit-service
        KT->>LEL: onEvent(Transfer status=CANCELLED) -> onFinalStatus(transfer)
        LEL->>LL: settle(transfer)
        LL->>LDB: ReservationRepository.findById(id)
        LDB-->>LL: Reservation (state=RESERVED)
        LL->>LDB: SenderUsageRepository.findById(senderId)
        LDB-->>LL: SenderUsage
        Note over LL: Компенсирующее действие:<br/>usage.release(amount, reservedOn, today) вычитает сумму из dailyUsed и monthlyUsed,<br/>reservation.markReleased() переводит резерв в state=RELEASED
        LL->>LDB: SenderUsageRepository.save(usage)
        LL->>LDB: ReservationRepository.save(reservation state=RELEASED)
    end
```

### ДИАГРАММА 4а: ER-диаграмма базы данных `transferdb` (`transfer-service`)

Диаграмма показывает структуру таблиц `transfers` (класс `TransferEntity`) и `transfer_history` (класс `TransferHistoryEntry`) в базе `transferdb`, связанных внешним ключом `transfer_id`.

```mermaid
erDiagram
    transfers {
        BIGINT id PK
        BIGINT sender_id "NOT NULL"
        BIGINT recipient_id "NOT NULL"
        DECIMAL_15_2 amount "NOT NULL"
        VARCHAR_10 currency "NOT NULL DEFAULT RUB"
        VARCHAR_20 status "NOT NULL"
        VARCHAR_20 decided_by "NULL"
        TEXT message_info "NULL"
        TIMESTAMP created_at "NOT NULL"
        TIMESTAMP updated_at "NULL"
    }

    transfer_history {
        BIGSERIAL id PK
        BIGINT transfer_id FK "NOT NULL, UK(transfer_id, stage)"
        VARCHAR_20 stage "NOT NULL, UK(transfer_id, stage)"
        VARCHAR_20 status "NOT NULL"
        VARCHAR_20 decided_by "NULL"
        TEXT message_info "NULL"
        TIMESTAMP changed_at "NOT NULL DEFAULT NOW()"
    }

    transfers ||--o{ transfer_history : "FK transfer_id"
```

### ДИАГРАММА 4б: ER-диаграмма базы данных `limitdb` (`limit-service`)

Диаграмма показывает таблицы `sender_usage` (класс `SenderUsage`) и `reservations` (класс `Reservation`) в базе `limitdb`, между которыми в SQL-схеме (`limit-service/src/main/resources/db/changelog/001-create-tables.sql`) нет внешнего ключа, но имеется логическая связь по полю `sender_id`.

```mermaid
erDiagram
    sender_usage {
        BIGINT sender_id PK
        DECIMAL_15_2 daily_used "NOT NULL DEFAULT 0"
        DECIMAL_15_2 monthly_used "NOT NULL DEFAULT 0"
        DATE last_daily_reset "NOT NULL DEFAULT CURRENT_DATE"
        DATE last_monthly_reset "NOT NULL DEFAULT CURRENT_DATE"
        BIGINT version "NOT NULL DEFAULT 0 (@Version)"
    }

    reservations {
        BIGINT transfer_id PK
        BIGINT sender_id "NOT NULL"
        DECIMAL_15_2 amount "NOT NULL"
        VARCHAR_10 decision "NOT NULL"
        VARCHAR_50 reason "NULL"
        VARCHAR_20 state "NOT NULL"
        DATE reserved_on "NOT NULL"
        TIMESTAMP created_at "NOT NULL"
        TIMESTAMP updated_at "NOT NULL"
    }

    sender_usage |o..o{ reservations : "logical sender_id"
```

### ДИАГРАММА 4в: ER-диаграмма базы данных `screeningdb` (`screening-service`)

Диаграмма показывает таблицы `blocked_senders` (класс `BlockedSender`) и `screening_checks` (класс `ScreeningCheck`) в базе `screeningdb`, связанные логически через идентификатор отправителя `sender_id` без ограничения внешнего ключа.

```mermaid
erDiagram
    blocked_senders {
        BIGSERIAL id PK
        BIGINT sender_id UK "NOT NULL UNIQUE"
        VARCHAR_255 reason "NOT NULL"
        TIMESTAMP blocked_at "NOT NULL DEFAULT NOW()"
    }

    screening_checks {
        BIGINT transfer_id PK
        BIGINT sender_id "NOT NULL"
        VARCHAR_10 result "NOT NULL"
        VARCHAR_50 reason "NULL"
        TIMESTAMP checked_at "NOT NULL"
    }

    blocked_senders |o..o{ screening_checks : "logical sender_id"
```

### ДИАГРАММА 4г: ER-диаграмма объектов, не хранящихся в БД (DTO и сообщения)

Диаграмма показывает структуры данных, передаваемые по HTTP и через топики Kafka (`TransferRequest`, `Transfer`, `HistoryStage`, `ProblemDetail`), и логические связи между ними.

```mermaid
erDiagram
    TransferRequest {
        Long id "NOT NULL"
        Long senderId "NOT NULL"
        Long recipientId "NOT NULL"
        BigDecimal amount "NOT NULL, Positive, Digits(13,2)"
        String currency "Pattern [A-Z]{3}, default RUB"
    }

    Transfer {
        Long id "id перевода (ключ сообщения Kafka)"
        Long senderId "id отправителя"
        Long recipientId "id получателя"
        BigDecimal amount "сумма перевода"
        String currency "валюта перевода"
        String status "NEW, APPROVED, DECLINED, COMPLETED, CANCELLED, DENIED"
        String decidedBy "limit, screening или null"
        String messageInfo "причина отказа или null"
        LocalDateTime createdAt "время создания"
    }

    HistoryStage {
        ENUM NEW "создание перевода"
        ENUM LIMIT "результат limit-service"
        ENUM SCREENING "результат screening-service"
        ENUM FINAL "финальное решение"
    }

    ProblemDetail {
        URI type "тип ошибки RFC 7807"
        String title "заголовок HTTP-статуса"
        int status "400, 409, 500, 503"
        String detail "описание ошибки"
        URI instance "URI эндпоинта"
    }

    TransferRequest ||..o{ Transfer : "logical id"
    HistoryStage ||..o{ Transfer : "logical status/stage"
    TransferRequest |o..o{ ProblemDetail : "logical error response"
```

**Логические связи между сущностями разных диаграмм:**
- `TransferRequest.id` (4г) ↔ `transfers.id` (4а, `1:1`): HTTP-запрос создаёт запись перевода в `transferdb` с тем же `id`.
- `transfers.id` (4а) ↔ `Transfer.id` (4г, `1:N`): по одному переводу в Kafka публикуется цепочка сообщений `Transfer` с одинаковым `id` (сначала `NEW`, затем ответы в `limit-results` и `screening-results`, затем финальный статус в `transfers`).
- `Transfer.id` (4г) / `transfers.id` (4а) ↔ `reservations.transfer_id` (4б, `1:0..1`): идентификатор перевода используется как первичный ключ резерва в `limitdb`.
- `Transfer.id` (4г) / `transfers.id` (4а) ↔ `screening_checks.transfer_id` (4в, `1:0..1`): идентификатор перевода используется как первичный ключ проверки в `screeningdb`.
- `transfers.sender_id` (4а) / `Transfer.senderId` (4г) ↔ `sender_usage.sender_id` (4б, `N:0..1`), `reservations.sender_id` (4б, `1:N`), `blocked_senders.sender_id` (4в, `N:0..1`), `screening_checks.sender_id` (4в, `1:N`): сквозной идентификатор отправителя во всех сервисах.
- `HistoryStage` (4г) ↔ `transfer_history.stage` (4а, `1:N`): значения перечисления `HistoryStage` сохраняются в виде строки в колонку `stage`.

### ДИАГРАММА 5: Диаграмма состояний

Диаграмма отображает жизненный цикл ключевой бизнес-сущности системы — перевода (`TransferEntity` в `transfer-service` и сообщение `Transfer` в `transfer-common`), включая промежуточные состояния проверки в сервисах и условия перехода к терминальным статусам в `TransferDecisionService.decide`.

```mermaid
stateDiagram-v2
    [*] --> NEW : "TransferTrackingService.createTransfer()\nи Transfer.newTransfer()"

    state "Параллельная проверка в микросервисах (сообщения Transfer)" as Checks {
        state "Решение limit-service (LimitLedger.decide)" as LimitCheck {
            [*] --> LIMIT_APPROVED : "amount > 0 AND dailyUsed + amount <= dailyMax\nAND monthlyUsed + amount <= monthlyMax"
            [*] --> LIMIT_DECLINED : "amount == null OR amount <= 0 (INVALID_AMOUNT)\nOR превышен dailyMax (DAILY_LIMIT_EXCEEDED)\nOR превышен monthlyMax (MONTHLY_LIMIT_EXCEEDED)"
        }
        --
        state "Решение screening-service (ScreeningJournal.check)" as ScreeningCheck {
            [*] --> SCREENING_APPROVED : "senderId отсутствует в blocked_senders"
            [*] --> SCREENING_DECLINED : "senderId найден в blocked_senders (SENDER_BLOCKED)"
        }
    }

    NEW --> Checks : "Публикация в топик transfers"

    Checks --> COMPLETED : "TransferDecisionService.decide():\nlimit == APPROVED AND screening == APPROVED"
    Checks --> DENIED : "TransferDecisionService.decide():\nlimit == DECLINED AND screening == DECLINED"
    Checks --> CANCELLED : "TransferDecisionService.decide():\n(limit == DECLINED AND screening == APPROVED, decidedBy='limit')\nOR (limit == APPROVED AND screening == DECLINED, decidedBy='screening')\nOR иные статусы (UNEXPECTED_RESULT)"

    COMPLETED --> [*] : "TransferTrackingService.applyFinalStatus()"
    DENIED --> [*] : "TransferTrackingService.applyFinalStatus()"
    CANCELLED --> [*] : "TransferTrackingService.applyFinalStatus()"
```

**Остальные сущности с жизненным циклом (статусами), присутствующие в коде:**
1. **`Reservation` (`limit-service`, класс `com.example.limit.domain.Reservation`, таблица `reservations`)**:
   - Поле `state`: при создании через `LimitLedger.decide` переходит либо в `NONE` (метод `decline()`, если сумма некорректна или превышен лимит — терминальное состояние), либо в `RESERVED` (метод `approve()`, сумма зарезервирована). Из состояния `RESERVED` в методе `LimitLedger.settle` при получении финального статуса перевода переходит в `COMMITTED` (метод `markCommitted()`, если статус перевода `COMPLETED`) или в `RELEASED` (метод `markReleased()`, если статус перевода `CANCELLED` или `DENIED`, с возвратом лимита в `SenderUsage.release`).
   - Поле `decision`: принимает значения `APPROVED` или `DECLINED`.
2. **`TransferHistoryEntry` (`transfer-service`, класс `com.example.transfer.domain.TransferHistoryEntry`, таблица `transfer_history`)**:
   - Фиксирует прохождение перевода по этапам `HistoryStage` (`NEW`, `LIMIT`, `SCREENING`, `FINAL`) со значениями поля `status` (`NEW`, `APPROVED`, `DECLINED`, `COMPLETED`, `CANCELLED`, `DENIED`).
3. **`ScreeningCheck` (`screening-service`, класс `com.example.screening.domain.ScreeningCheck`, таблица `screening_checks`)**:
   - Поле `result`: одношаговый статус результата проверки отправителя в `ScreeningJournal.check` — принимает значение `APPROVED` или `DECLINED`.