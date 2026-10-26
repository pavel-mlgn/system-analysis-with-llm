# Диаграмма 1: Component diagram (C4)

Все контейнеры системы: три Spring Boot-сервиса, брокер Kafka, три базы PostgreSQL, внешний клиент REST API и связи между ними с указанием протоколов и каналов.

```mermaid
flowchart TB
    Client["Внешний клиент<br/>(REST API)"]

    subgraph transfer-service["transfer-service (Spring Boot 3.4.1, Java 21)"]
        TC["TransferController<br/>POST /transfers, GET /transfers, GET /transfers/{id}"]
        DTS["TransferDecisionService"]
        TTS["TransferTrackingService"]
        TDL["DecisionTopologyConfig<br/>(Kafka Streams join)"]
        TEL["TransferEventListener"]
    end

    subgraph limit-service["limit-service (Spring Boot 3.4.1, Java 21)"]
        LEL["TransferEventsListener"]
        LS["LimitService"]
        LL["LimitLedger"]
    end

    subgraph screening-service["screening-service (Spring Boot 3.4.1, Java 21)"]
        SEL["TransferEventsListener"]
        SS["ScreeningService"]
        SJ["ScreeningJournal"]
    end

    subgraph broker["Kafka (cp-kafka 7.6.0)"]
        T["topic: transfers<br/>(3 партиции, compact)"]
        LR["topic: limit-results<br/>(3 партиции, compact)"]
        SR["topic: screening-results<br/>(3 партиции, compact)"]
        DLT["topics: *.DLT<br/>(dead letter)"]
    end

    subgraph dbs["PostgreSQL 16"]
        TDB[("transfer-db<br/>transferdb<br/>порт 5434")]
        LDB[("limit-db<br/>limitdb<br/>порт 5432")]
        SDB[("screening-db<br/>screeningdb<br/>порт 5433")]
    end

    Client -->|"HTTP/REST (порт 8080)"| TC
    TC -->|"JDBC"| TDB
    TTS -->|"JDBC"| TDB

    TC -->|"Kafka producer: topic transfers"| T
    T -->|"Kafka consumer, groupId=limit"| LEL
    T -->|"Kafka consumer, groupId=screening"| SEL
    T -->|"Kafka Streams source"| TDL
    LR -->|"Kafka Streams source"| TDL
    SR -->|"Kafka Streams source"| TDL

    LEL --> LS --> LL
    LL -->|"JDBC"| LDB
    LS -->|"Kafka producer: topic limit-results"| LR

    SEL --> SS --> SJ
    SJ -->|"JDBC"| SDB
    SS -->|"Kafka producer: topic screening-results"| SR

    TDL --> DTS
    TDL -->|"Kafka producer: topic transfers"| T

    TEL -->|"Kafka consumer, groupId=transfer-tracker:<br/>transfers, limit-results, screening-results"| T
    TEL --> TTS

    LR -.->|"при ошибках обработки"| DLT
    SR -.->|"при ошибках обработки"| DLT
    T -.->|"при ошибках обработки"| DLT
```

# Диаграмма 2: Sequence diagram — happy path

Успешный сценарий: оба сервиса (лимиты и скрининг) одобряют перевод, Kafka Streams-join выносит решение `COMPLETED`, лимитная резервация коммитится.

```mermaid
sequenceDiagram
    autonumber
    actor C as Клиент
    participant TC as TransferController
    participant TTS as TransferTrackingService
    participant DB as transfer-db (transferdb)
    participant K as Kafka
    participant L as limit-service<br/>(TransferEventsListener,<br/>LimitService, LimitLedger)
    participant LDB as limit-db (limitdb)
    participant S as screening-service<br/>(TransferEventsListener,<br/>ScreeningService, ScreeningJournal)
    participant SDB as screening-db (screeningdb)
    participant KS as transfer-service<br/>(Kafka Streams:<br/>DecisionTopologyConfig,<br/>TransferDecisionService)
    participant TE as transfer-service<br/>(TransferEventListener)

    C->>TC: POST /transfers<br/>(TransferRequest)
    TC->>TTS: createTransfer(request)
    TTS->>DB: INSERT transfers (status=NEW)<br/>INSERT transfer_history (stage=NEW)
    TC->>K: send("transfers", id, Transfer[status=NEW])
    TC-->>C: 201 Created (TransferEntity)

    K->>L: onEvent(Transfer[NEW])<br/>groupId=limit
    L->>LDB: LIMIT_LEDGER.decide():<br/>SELECT sender_usage, lazyReset,<br/>reserve(amount),<br/>INSERT reservations (APPROVED/RESERVED)
    L->>K: send("limit-results", id, Transfer[APPROVED])

    K->>S: onEvent(Transfer[NEW])<br/>groupId=screening
    S->>SDB: SCREENING_JOURNAL.check():<br/>SELECT blocked_senders,<br/>INSERT screening_checks (APPROVED)
    S->>K: send("screening-results", id, Transfer[APPROVED])

    K->>KS: stream join limit-results + screening-results<br/>(JoinWindows 10 сек)
    KS->>KS: TRANSFER_DECISION_SERVICE.decide():<br/>APPROVED + APPROVED → COMPLETED
    KS->>K: send("transfers", id, Transfer[COMPLETED])

    K->>TE: onEvent(Transfer[COMPLETED])<br/>groupId=transfer-tracker
    TE->>TTS: applyFinalStatus(Transfer[COMPLETED])
    TTS->>DB: UPDATE transfers (status=COMPLETED)<br/>INSERT transfer_history (stage=FINAL)

    K->>L: onEvent(Transfer[COMPLETED])
    L->>LDB: LIMIT_LEDGER.settle():<br/>reservations.markCommitted() (RESERVED → COMMITTED)
```

# Диаграмма 3: Sequence diagram — сценарий отказа

Отказ по лимиту: резервация уже создана не будет — declined на этапе решения; затем поток join выносит `CANCELLED`, и уже выполненная лимитная резервация компенсируется (освобождение `SenderUsage`, `RESERVED → RELEASED`). Показан полный путь компенсации.

```mermaid
sequenceDiagram
    autonumber
    actor C as Клиент
    participant TC as TransferController
    participant TTS as TransferTrackingService
    participant DB as transfer-db
    participant K as Kafka
    participant L as limit-service<br/>(LimitLedger)
    participant LDB as limit-db
    participant S as screening-service<br/>(ScreeningJournal)
    participant KS as Kafka Streams:<br/>TransferDecisionService
    participant TE as TransferEventListener<br/>(transfer-service)

    C->>TC: POST /transfers<br/>(sender превысил daily-max)
    TC->>TTS: createTransfer(request)
    TTS->>DB: INSERT transfers (status=NEW),<br/>INSERT transfer_history (stage=NEW)
    TC->>K: send("transfers", id, Transfer[NEW])
    TC-->>C: 201 Created

    K->>L: onEvent(Transfer[NEW])
    Note over L,LDB: LIMIT_LEDGER.decide():<br/>usage.dailyUsed + amount > dailyMax<br/>→ reservations.decline("DAILY_LIMIT_EXCEEDED")<br/>usage НЕ резервируется
    L->>LDB: INSERT reservations (DECLINED/NONE)
    L->>K: send("limit-results", id, Transfer[DECLINED])

    K->>S: onEvent(Transfer[NEW])
    S->>K: send("screening-results", id, Transfer[APPROVED])

    K->>KS: join limit-results + screening-results
    Note over KS: decide(): DECLINED + APPROVED<br/>→ CANCELLED, decidedBy="limit"
    KS->>K: send("transfers", id, Transfer[CANCELLED])

    K->>TE: onEvent(Transfer[CANCELLED])
    TE->>TTS: applyFinalStatus(Transfer[CANCELLED])
    TTS->>DB: UPDATE transfers (status=CANCELLED,<br/>decidedBy=limit, messageInfo=DAILY_LIMIT_EXCEEDED)<br/>INSERT transfer_history (stage=FINAL)

    K->>L: onEvent(Transfer[CANCELLED])
    Note over L,LDB: LIMIT_LEDGER.settle():<br/>CASE "CANCELLED" →<br/>usage.release(amount, ...)<br/>reservation.markReleased()<br/>(компенсация резерва)
    L->>LDB: UPDATE sender_usage,<br/>UPDATE reservations (RELEASED)
```

Дополнительный сценарий частичного отказа, обнаруженный в коде: при недоступности Kafka в `TransferController.publishNew` сущность уже сохранена в БД, клиент получает `503` с сообщением «Kafka недоступна, перевод сохранён — повторите запрос с тем же id», а повторный запрос с тем же `id` (через `duplicate()`) просто переиздаёт событие в `transfers` — двойной записи не возникает.

# Диаграмма 4: ER-диаграммы

Три физические базы данных и один неперсистентный объект сообщения (`Transfer` — Java record, хранится только в Kafka).

```mermaid
erDiagram
    TRANSFERS ||--|| TRANSFER_HISTORY : "fk: transfer_history.transfer_id → transfers.id"

    TRANSFERS {
        bigint id PK
        bigint sender_id
        bigint recipient_id
        decimal_15_2 amount
        varchar_10 currency "DEFAULT 'RUB'"
        varchar_20 status
        varchar_20 decided_by
        text message_info
        timestamp created_at
        timestamp updated_at
    }
    TRANSFER_HISTORY {
        bigserial id PK
        bigint transfer_id FK
        varchar_20 status
        varchar_20 decided_by
        text message_info
        timestamp changed_at
        varchar_20 stage "UNIQUE (transfer_id, stage)"
    }
```

```mermaid
erDiagram
    SENDER_USAGE ||--o{ RESERVATIONS : "логически: reservations.sender_id (без FK)"

    SENDER_USAGE {
        bigint sender_id PK
        decimal_15_2 daily_used
        decimal_15_2 monthly_used
        date last_daily_reset
        date last_monthly_reset
        bigint version "@Version"
    }
    RESERVATIONS {
        bigint transfer_id PK
        bigint sender_id
        decimal_15_2 amount
        varchar_10 decision "APPROVED | DECLINED"
        varchar_50 reason
        varchar_20 state "RESERVED | COMMITTED | RELEASED | NONE"
        date reserved_on
        timestamp created_at
        timestamp updated_at
    }
```

```mermaid
erDiagram
    BLOCKED_SENDERS ||--o{ SCREENING_CHECKS : "логически: screening_checks.sender_id (без FK)"

    BLOCKED_SENDERS {
        bigserial id PK
        bigint sender_id "UNIQUE"
        varchar_255 reason
        timestamp blocked_at
    }
    SCREENING_CHECKS {
        bigint transfer_id PK
        bigint sender_id
        varchar_10 result "APPROVED | DECLINED"
        varchar_50 reason
        timestamp checked_at
    }
```

```mermaid
erDiagram
    TRANSFER_MESSAGE {
        long id
        long senderId
        long recipientId
        bigdecimal amount
        string currency
        string status "NEW | APPROVED | DECLINED | COMPLETED | CANCELLED | DENIED"
        string decidedBy
        string messageInfo
        localdatetime createdAt
    }
```

Логические связи между сущностями разных диаграмм (без внешних ключей, передача через Kafka по идентификаторам):

- `Transfer.id` ↔ `transfers.id` ↔ `reservations.transfer_id` ↔ `screening_checks.transfer_id` — сквозной идентификатор перевода.
- `Transfer.senderId` ↔ `sender_usage.sender_id`, `blocked_senders.sender_id` — идентификатор отправителя.
- `TransferHistoryEntry.transferId` ↔ `transfers.id` — единственный настоящий FK (в transferdb).

# Диаграмма 5: Диаграмма состояний

Жизненный цикл главной бизнес-сущности `Transfer` (статусы из `Transfer`/`TransferDecisionService`); статусы `APPROVED`/`DECLINED` — промежуточные результаты сервисов, публикуемые в `limit-results`/`screening-results`.

```mermaid
stateDiagram-v2
    [*] --> NEW : POST /transfers<br/>TransferController.create

    NEW --> APPROVED : LimitLedger.decide<br/>(в рамках лимитов)
    NEW --> DECLINED : LimitLedger.decide<br/>(DAILY_LIMIT_EXCEEDED /<br/>MONTHLY_LIMIT_EXCEEDED /<br/>INVALID_AMOUNT)
    NEW --> DECLINED : ScreeningJournal.check<br/>(SENDER_BLOCKED)

    APPROVED --> COMPLETED : TransferDecisionService.decide<br/>APPROVED + APPROVED<br/>→ LimitLedger.settle: markCommitted
    APPROVED --> CANCELLED : decide: APPROVED + DECLINED<br/>→ LimitLedger.settle: usage.release + markReleased
    DECLINED --> CANCELLED : decide: DECLINED + APPROVED<br/>→ LimitLedger.settle: нет резерва
    DECLINED --> DENIED : decide: DECLINED + DECLINED<br/>→ обе причины в messageInfo

    COMPLETED --> [*]
    CANCELLED --> [*]
    DENIED --> [*]
```

Другая сущность с жизненным циклом — `Reservation` (limit-service), состояния из класса `Reservation`: `NONE` (declined) → финал; `RESERVED` → `COMMITTED` (при `COMPLETED`) или `RESERVED` → `RELEASED` (при `CANCELLED`/`DENIED`, с освобождением `SenderUsage`). Повторные обработки защищены: в `LimitLedger.decide` — проверкой `reservationRepository.findById` (возврат сохранённого решения), в `TransferTrackingService` — уникальным ограничением `uq_transfer_history_stage` и проверкой `existsByTransferIdAndStage`.