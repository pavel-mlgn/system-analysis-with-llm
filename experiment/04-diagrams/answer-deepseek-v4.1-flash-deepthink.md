## Диаграмма 1: Component diagram (C4)
Показаны контейнеры системы, Kafka-топики, PostgreSQL-базы и основные каналы взаимодействия между ними.

```mermaid
flowchart TB
    subgraph Client["Внешняя система / клиент"]
        User["API client"]
    end

    subgraph TransferService["transfer-service"]
        TransferController["TransferController"]
        TransferTrackingService["TransferTrackingService"]
        TransferEventListener["TransferEventListener"]
        DecisionTopologyConfig["DecisionTopologyConfig"]
        TransferDecisionService["TransferDecisionService"]
        TransferKafkaErrorHandlingConfig["KafkaErrorHandlingConfig"]
    end

    subgraph LimitService["limit-service"]
        LimitTransferEventsListener["TransferEventsListener"]
        LimitServiceClass["LimitService"]
        LimitLedger["LimitLedger"]
        LimitKafkaErrorHandlingConfig["KafkaErrorHandlingConfig"]
    end

    subgraph ScreeningService["screening-service"]
        ScreeningTransferEventsListener["TransferEventsListener"]
        ScreeningServiceClass["ScreeningService"]
        ScreeningJournal["ScreeningJournal"]
        ScreeningKafkaErrorHandlingConfig["KafkaErrorHandlingConfig"]
    end

    subgraph Kafka["Kafka broker"]
        Broker["broker: confluentinc/cp-kafka:7.6.0"]
        TopicTransfers["Topic: transfers (3 partitions, compact)"]
        TopicLimitResults["Topic: limit-results (3 partitions, compact)"]
        TopicScreeningResults["Topic: screening-results (3 partitions, compact)"]
        TopicTransfersDLT["Topic: transfers.DLT (3 partitions)"]
        TopicLimitResultsDLT["Topic: limit-results.DLT (3 partitions)"]
        TopicScreeningResultsDLT["Topic: screening-results.DLT (3 partitions)"]
    end

    subgraph Databases["PostgreSQL"]
        TransferDb[("transfer-db: transferdb")]
        LimitDb[("limit-db: limitdb")]
        ScreeningDb[("screening-db: screeningdb")]
    end

    User -->|"HTTP POST /transfers, GET /transfers/{id}"| TransferController
    TransferController -->|"createTransfer / duplicate"| TransferTrackingService
    TransferTrackingService -->|"JDBC"| TransferDb
    TransferController -->|"Kafka produce key=id"| TopicTransfers
    DecisionTopologyConfig -->|"Kafka Streams consume"| TopicLimitResults
    DecisionTopologyConfig -->|"Kafka Streams consume"| TopicScreeningResults
    DecisionTopologyConfig -->|"decide"| TransferDecisionService
    DecisionTopologyConfig -->|"Kafka Streams produce topic=transfers"| TopicTransfers
    TransferEventListener -->|"Kafka consume groupId=transfer-tracker"| TopicTransfers
    TransferEventListener -->|"Kafka consume groupId=transfer-tracker"| TopicLimitResults
    TransferEventListener -->|"Kafka consume groupId=transfer-tracker"| TopicScreeningResults
    TransferEventListener -->|"recordServiceResult / applyFinalStatus"| TransferTrackingService
    TopicTransfers -->|"Kafka consume groupId=limit (NEW и final)"| LimitTransferEventsListener
    TopicTransfers -->|"Kafka consume groupId=screening (NEW и final)"| ScreeningTransferEventsListener
    LimitTransferEventsListener -->|"onNewTransfer / onFinalStatus"| LimitServiceClass
    LimitServiceClass -->|"decide / settle"| LimitLedger
    LimitLedger -->|"JDBC"| LimitDb
    LimitServiceClass -->|"Kafka produce key=id"| TopicLimitResults
    ScreeningTransferEventsListener -->|"onNewTransfer"| ScreeningServiceClass
    ScreeningServiceClass -->|"check"| ScreeningJournal
    ScreeningJournal -->|"JDBC"| ScreeningDb
    ScreeningServiceClass -->|"Kafka produce key=id"| TopicScreeningResults
    LimitKafkaErrorHandlingConfig -.->|"DLT publish on error"| TopicTransfersDLT
    ScreeningKafkaErrorHandlingConfig -.->|"DLT publish on error"| TopicTransfersDLT
    TransferKafkaErrorHandlingConfig -.->|"DLT publish on error"| TopicTransfersDLT
    TransferKafkaErrorHandlingConfig -.->|"DLT publish on error"| TopicLimitResultsDLT
    TransferKafkaErrorHandlingConfig -.->|"DLT publish on error"| TopicScreeningResultsDLT
```

## Диаграмма 2: Sequence diagram — основной успешный сценарий (happy path)
Показан путь перевода от REST-запроса до финального статуса `COMPLETED` и коммита резерва лимита.

```mermaid
sequenceDiagram
    autonumber
    participant Client as Client
    participant TC as TransferController
    participant TTS as TransferTrackingService
    participant TR as TransferRepository
    participant THR as TransferHistoryRepository
    participant TTK as KafkaTemplate_transfer
    participant K as Kafka_broker
    participant LL as TransferEventsListener_limit
    participant LS as LimitService
    participant LLed as LimitLedger
    participant SUR as SenderUsageRepository
    participant RR as ReservationRepository
    participant LTK as KafkaTemplate_limit
    participant SL as TransferEventsListener_screening
    participant SS as ScreeningService
    participant SJ as ScreeningJournal
    participant BSR as BlockedSenderRepository
    participant SCR as ScreeningCheckRepository
    participant STK as KafkaTemplate_screening
    participant DS as DecisionTopologyConfig
    participant TDS as TransferDecisionService
    participant TEL as TransferEventListener_transfer

    Client->>TC: "POST /transfers (TransferRequest)"
    TC->>TR: "findById(request.id())"
    TR-->>TC: "Optional.empty()"
    TC->>TTS: "createTransfer(request)"
    TTS->>TR: "saveAndFlush(TransferEntity status=NEW)"
    TTS->>THR: "save(TransferHistoryEntry stage=NEW)"
    TTS-->>TC: "TransferEntity"
    TC->>TTK: "send(\"transfers\", id, Transfer NEW).get(10s)"
    TTK->>K: "produce topic=\"transfers\" key=id"
    TC-->>Client: "201 Created TransferEntity status=NEW"

    K-->>LL: "onEvent(Transfer NEW) groupId=\"limit\""
    K-->>SL: "onEvent(Transfer NEW) groupId=\"screening\""
    K-->>TEL: "onEvent(Transfer NEW) from \"transfers\""
    TEL->>TEL: "status=NEW, final status не применяется"

    LL->>LS: "onNewTransfer(transfer)"
    LS->>LLed: "decide(transfer)"
    LLed->>RR: "findById(transfer.id())"
    RR-->>LLed: "Optional.empty()"
    LLed->>SUR: "findById(senderId)"
    SUR-->>LLed: "Optional.empty()"
    LLed->>LLed: "new SenderUsage(senderId, today)"
    LLed->>LLed: "usage.lazyReset(today)"
    LLed->>LLed: "usage.reserve(amount)"
    LLed->>LLed: "reservation.approve()"
    LLed->>SUR: "save(usage)"
    LLed->>RR: "save(reservation)"
    LLed-->>LS: "Reservation decision=APPROVED state=RESERVED"
    LS->>LTK: "send(\"limit-results\", id, withDecision(APPROVED, \"limit\", null)).get(10s)"
    LTK->>K: "produce topic=\"limit-results\" key=id"

    SL->>SS: "onNewTransfer(transfer)"
    SS->>SJ: "check(transfer)"
    SJ->>SCR: "findById(transfer.id())"
    SCR-->>SJ: "Optional.empty()"
    SJ->>BSR: "findBySenderId(senderId)"
    BSR-->>SJ: "Optional.empty()"
    SJ->>SJ: "new ScreeningCheck(APPROVED)"
    SJ->>SCR: "save(check)"
    SJ-->>SS: "ScreeningCheck result=APPROVED"
    SS->>STK: "send(\"screening-results\", id, withDecision(APPROVED, \"screening\", null)).get(10s)"
    STK->>K: "produce topic=\"screening-results\" key=id"

    K-->>TEL: "onEvent(Transfer APPROVED) from \"limit-results\""
    TEL->>TTS: "recordServiceResult(transfer, HistoryStage.LIMIT)"
    TTS->>THR: "existsByTransferIdAndStage(id, LIMIT)"
    TTS->>THR: "save(TransferHistoryEntry stage=LIMIT)"
    K-->>TEL: "onEvent(Transfer APPROVED) from \"screening-results\""
    TEL->>TTS: "recordServiceResult(transfer, HistoryStage.SCREENING)"
    TTS->>THR: "save(TransferHistoryEntry stage=SCREENING)"

    K-->>DS: "stream(\"limit-results\") join stream(\"screening-results\")"
    DS->>TDS: "decide(limitResult, screeningResult)"
    TDS-->>DS: "withDecision(\"COMPLETED\", null, null)"
    DS->>K: "to(\"transfers\")"

    K-->>TEL: "onEvent(Transfer COMPLETED) from \"transfers\""
    TEL->>TTS: "applyFinalStatus(transfer)"
    TTS->>TR: "findById(result.id())"
    TTS->>TR: "save(entity status=COMPLETED)"
    TTS->>THR: "save(TransferHistoryEntry stage=FINAL status=COMPLETED)"

    K-->>LL: "onEvent(Transfer COMPLETED) groupId=\"limit\""
    LL->>LS: "onFinalStatus(transfer)"
    LS->>LLed: "settle(transfer)"
    LLed->>RR: "findById(transfer.id())"
    LLed->>LLed: "reservation.markCommitted()"
    LLed->>RR: "save(reservation state=COMMITTED)"

    K-->>SL: "onEvent(Transfer COMPLETED) groupId=\"screening\""
    SL->>SL: "screening holds no reservations, nothing to do"
```

## Диаграмма 3: Sequence diagram — основной сценарий отказа
Показан отказ публикации в Kafka после сохранения перевода: запись остаётся в БД со статусом `NEW`, клиент получает `503`.

```mermaid
sequenceDiagram
    autonumber
    participant Client as Client
    participant TC as TransferController
    participant TTS as TransferTrackingService
    participant TR as TransferRepository
    participant THR as TransferHistoryRepository
    participant TTK as KafkaTemplate_transfer
    participant K as Kafka_broker

    Client->>TC: "POST /transfers (TransferRequest)"
    TC->>TR: "findById(request.id())"
    TR-->>TC: "Optional.empty()"
    TC->>TTS: "createTransfer(request)"
    TTS->>TR: "saveAndFlush(TransferEntity status=NEW)"
    TTS->>THR: "save(TransferHistoryEntry stage=NEW)"
    TTS-->>TC: "TransferEntity"
    TC->>TTK: "send(\"transfers\", id, Transfer NEW).get(10s)"
    TTK--x K: "produce fail: timeout/unavailable"
    TC->>TC: "catch ExecutionException / TimeoutException / KafkaException"
    TC-->>Client: "503 ProblemDetail detail=\"Kafka недоступна, перевод сохранён — повторите запрос с тем же id\""
    Note over TR,THR: "Уже выполнено: transfers и transfer_history сохранены со статусом NEW"
    Note over TC: "Повторный POST с тем же id: duplicate() найдёт запись; при совпадении параметров и status=NEW вызовет publishNew(existing), иначе 409 CONFLICT"
```

## Диаграмма 4а: ER-диаграмма — transfer-db
Сущности базы `transferdb`, между `transfers` и `transfer_history` есть FK-связь.

```mermaid
erDiagram
    transfers {
        BIGINT id PK
        BIGINT sender_id
        BIGINT recipient_id
        DECIMAL amount
        VARCHAR currency
        VARCHAR status
        VARCHAR decided_by
        TEXT message_info
        TIMESTAMP created_at
        TIMESTAMP updated_at
    }
    transfer_history {
        BIGINT id PK
        BIGINT transfer_id FK
        VARCHAR status
        VARCHAR decided_by
        TEXT message_info
        TIMESTAMP changed_at
        VARCHAR stage
    }
    transfers ||--o{ transfer_history : "transfer_id FK"
```

## Диаграмма 4б: ER-диаграмма — limit-db
Сущности базы `limitdb`; связь `sender_usage` и `reservations` логическая по `sender_id`, без FK.

```mermaid
erDiagram
    sender_usage {
        BIGINT sender_id PK
        DECIMAL daily_used
        DECIMAL monthly_used
        DATE last_daily_reset
        DATE last_monthly_reset
        BIGINT version
    }
    reservations {
        BIGINT transfer_id PK
        BIGINT sender_id
        DECIMAL amount
        VARCHAR decision
        VARCHAR reason
        VARCHAR state
        DATE reserved_on
        TIMESTAMP created_at
        TIMESTAMP updated_at
    }
    sender_usage ||..o{ reservations : "sender_id логическая"
```

## Диаграмма 4в: ER-диаграмма — screening-db
Сущности базы `screeningdb`; связь `blocked_senders` и `screening_checks` логическая по `sender_id`, без FK.

```mermaid
erDiagram
    blocked_senders {
        BIGINT id PK
        BIGINT sender_id UK
        VARCHAR reason
        TIMESTAMP blocked_at
    }
    screening_checks {
        BIGINT transfer_id PK
        BIGINT sender_id
        VARCHAR result
        VARCHAR reason
        TIMESTAMP checked_at
    }
    blocked_senders ||..o{ screening_checks : "sender_id логическая"
```

## Диаграмма 4г: ER-диаграмма — объекты, не хранящиеся в БД
Показаны `TransferRequest` и `Transfer` — REST-запрос и Kafka-сообщение/ответ.

```mermaid
erDiagram
    TransferRequest {
        Long id PK
        Long senderId
        Long recipientId
        BigDecimal amount
        String currency
    }
    Transfer {
        Long id PK
        Long senderId
        Long recipientId
        BigDecimal amount
        String currency
        String status
        String decidedBy
        String messageInfo
        LocalDateTime createdAt
    }
    TransferRequest ||..|| Transfer : "REST-запрос создаёт сообщение"
    Transfer ||..|| Transfer : "withDecision() возвращает новую версию"
```

Логические связи между сущностями разных диаграмм:
- `transfer-db.transfers.id` ↔ `limit-db.reservations.transfer_id` — логическая, без FK.
- `transfer-db.transfers.sender_id` ↔ `limit-db.sender_usage.sender_id` — логическая, без FK.
- `transfer-db.transfers.sender_id` ↔ `limit-db.reservations.sender_id` — логическая, без FK.
- `transfer-db.transfers.id` ↔ `screening-db.screening_checks.transfer_id` — логическая, без FK.
- `transfer-db.transfers.sender_id` ↔ `screening-db.screening_checks.sender_id` — логическая, без FK.
- `transfer-db.transfers.sender_id` ↔ `screening-db.blocked_senders.sender_id` — логическая, без FK.
- `Transfer.id` ↔ `transfer-db.transfers.id`, `limit-db.reservations.transfer_id`, `screening-db.screening_checks.transfer_id` — через Kafka key/message.
- `Transfer.senderId` ↔ `limit-db.sender_usage.sender_id`, `limit-db.reservations.sender_id`, `screening-db.screening_checks.sender_id`, `screening-db.blocked_senders.sender_id` — логическая.
- `TransferRequest.id` ↔ `Transfer.id` — логическая связь REST-запроса и сообщения.

## Диаграмма 5: Диаграмма состояний — Transfer
Главная бизнес-сущность — перевод; статусы взяты из `TransferEntity`, `TransferDecisionService` и OpenAPI.

```mermaid
stateDiagram-v2
    [*] --> NEW: "POST /transfers, TransferTrackingService.createTransfer()"
    NEW --> NEW: "duplicate POST с тем же id и параметрами: publishNew(existing)"
    NEW --> COMPLETED: "TransferDecisionService.decide: limit=APPROVED и screening=APPROVED"
    NEW --> CANCELLED: "limit=DECLINED и screening=APPROVED, или limit=APPROVED и screening=DECLINED, или UNEXPECTED_RESULT"
    NEW --> DENIED: "limit=DECLINED и screening=DECLINED"
    COMPLETED --> [*]
    CANCELLED --> [*]
    DENIED --> [*]
```

Дополнительно у `Reservation` есть жизненный цикл: `NONE` → `RESERVED` при `approve()`, `RESERVED` → `COMMITTED` при `settle()` для `COMPLETED`, `RESERVED` → `RELEASED` при `settle()` для `CANCELLED` или `DENIED`. В коде также есть состояния `STATE_NONE`, `STATE_RESERVED`, `STATE_COMMITTED`, `STATE_RELEASED` в `limit-service/domain/Reservation.java`.