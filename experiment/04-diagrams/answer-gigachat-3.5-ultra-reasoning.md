**Диаграмма 1: Component Diagram (C4) — Архитектура системы**

```mermaid
flowchart TB
    subgraph "External Systems"
        Client["Client API"]
    end
    
    subgraph "transfer-service"
        TransferController["TransferController"]
        TransferTrackingService["TransferTrackingService"]
        TransferRepository["TransferRepository"]
        TransferHistoryRepository["TransferHistoryRepository"]
        KafkaProducer["KafkaTemplate<Long, Transfer>"]
        TransferDecisionService["TransferDecisionService"]
        TransferEventListener["TransferEventListener"]
    end
    
    subgraph "Kafka Broker"
        TransfersTopic["transfers"]
        LimitResultsTopic["limit-results"]
        ScreeningResultsTopic["screening-results"]
        TransfersDLT["transfers.DLT"]
        LimitResultsDLT["limit-results.DLT"]
        ScreeningResultsDLT["screening-results.DLT"]
    end
    
    subgraph "limit-service"
        LimitEventsListener["TransferEventsListener"]
        LimitService["LimitService"]
        LimitLedger["LimitLedger"]
        SenderUsageRepository["SenderUsageRepository"]
        ReservationRepository["ReservationRepository"]
    end
    
    subgraph "screening-service"
        ScreeningEventsListener["TransferEventsListener"]
        ScreeningService["ScreeningService"]
        ScreeningJournal["ScreeningJournal"]
        BlockedSenderRepository["BlockedSenderRepository"]
        ScreeningCheckRepository["ScreeningCheckRepository"]
    end
    
    subgraph "transfer-db"
        TransfersTable["transfers"]
        TransferHistoryTable["transfer_history"]
    end
    
    subgraph "limit-db"
        SenderUsageTable["sender_usage"]
        ReservationsTable["reservations"]
    end
    
    subgraph "screening-db"
        BlockedSendersTable["blocked_senders"]
        ScreeningChecksTable["screening_checks"]
    end
    
    Client -->|POST /transfers| TransferController
    TransferController -->|save| TransferRepository
    TransferController -->|publish| KafkaProducer
    KafkaProducer -->|send| TransfersTopic
    
    TransfersTopic -->|consume| LimitEventsListener
    LimitEventsListener -->|onEvent| LimitService
    LimitService -->|decide| LimitLedger
    LimitLedger -->|check/save| SenderUsageRepository
    LimitLedger -->|save| ReservationRepository
    LimitService -->|sendAndWait| KafkaProducer
    KafkaProducer -->|send| LimitResultsTopic
    
    TransfersTopic -->|consume| ScreeningEventsListener
    ScreeningEventsListener -->|onEvent| ScreeningService
    ScreeningService -->|check| ScreeningJournal
    ScreeningJournal -->|save| ScreeningCheckRepository
    ScreeningService -->|sendAndWait| KafkaProducer
    KafkaProducer -->|send| ScreeningResultsTopic
    
    LimitResultsTopic -->|consume| TransferDecisionService
    ScreeningResultsTopic -->|consume| TransferDecisionService
    TransferDecisionService -->|decide| KafkaProducer
    KafkaProducer -->|send| TransfersTopic
    
    TransfersTopic -->|consume| TransferEventListener
    TransferEventListener -->|recordServiceResult / applyFinalStatus| TransferTrackingService
    TransferTrackingService -->|save| TransferRepository
    TransferTrackingService -->|save| TransferHistoryRepository
    
    TransferRepository -->|persist| TransfersTable
    TransferHistoryRepository -->|persist| TransferHistoryTable
    SenderUsageRepository -->|persist| SenderUsageTable
    ReservationRepository -->|persist| ReservationsTable
    BlockedSenderRepository -->|persist| BlockedSendersTable
    ScreeningCheckRepository -->|persist| ScreeningChecksTable
```

**Диаграмма 2: Sequence Diagram — Основной успешный сценарий (Happy Path)**

```mermaid
sequenceDiagram
    participant Client
    participant TransferController
    participant TransferTrackingService
    participant KafkaProducer
    participant TransfersTopic
    participant LimitEventsListener
    participant LimitService
    participant LimitLedger
    participant SenderUsageRepository
    participant ReservationRepository
    participant ScreeningEventsListener
    participant ScreeningService
    participant ScreeningJournal
    participant ScreeningCheckRepository
    participant TransferDecisionService
    participant TransferEventListener
    
    Client->>TransferController: POST /transfers (TransferRequest)
    TransferController->>TransferTrackingService: createTransfer(request)
    TransferTrackingService->>TransferRepository: saveAndFlush(entity)
    TransferTrackingService->>TransferHistoryRepository: save(NEW)
    TransferController->>KafkaProducer: send("transfers", Transfer(id, NEW))
    KafkaProducer->>TransfersTopic: publish
    
    TransfersTopic->>LimitEventsListener: onEvent(Transfer)
    LimitEventsListener->>LimitService: onNewTransfer(transfer)
    LimitService->>LimitLedger: decide(transfer)
    LimitLedger->>SenderUsageRepository: findById / lazyReset
    LimitLedger->>ReservationRepository: save(reservation APPROVED)
    LimitLedger->>SenderUsageRepository: reserve(amount)
    LimitService->>KafkaProducer: send("limit-results", APPROVED)
    KafkaProducer->>LimitResultsTopic: publish
    
    TransfersTopic->>ScreeningEventsListener: onEvent(Transfer)
    ScreeningEventsListener->>ScreeningService: onNewTransfer(transfer)
    ScreeningService->>ScreeningJournal: check(transfer)
    ScreeningJournal->>ScreeningCheckRepository: save(check APPROVED)
    ScreeningService->>KafkaProducer: send("screening-results", APPROVED)
    KafkaProducer->>ScreeningResultsTopic: publish
    
    LimitResultsTopic->>TransferDecisionService: onEvent(limit APPROVED)
    ScreeningResultsTopic->>TransferDecisionService: onEvent(screening APPROVED)
    TransferDecisionService->>TransferDecisionService: decide(APP, APP) -> COMPLETED
    TransferDecisionService->>KafkaProducer: send("transfers", COMPLETED)
    KafkaProducer->>TransfersTopic: publish
    
    TransfersTopic->>TransferEventListener: onEvent(COMPLETED)
    TransferEventListener->>TransferTrackingService: applyFinalStatus(COMPLETED)
    TransferTrackingService->>TransferRepository: save
    TransferTrackingService->>TransferHistoryRepository: save(FINAL)
```

**Диаграмма 3: Sequence Diagram — Сценарий отказа (превышен дневной лимит)**

```mermaid
sequenceDiagram
    participant Client
    participant TransferController
    participant TransferTrackingService
    participant KafkaProducer
    participant TransfersTopic
    participant LimitEventsListener
    participant LimitService
    participant LimitLedger
    participant SenderUsageRepository
    participant ReservationRepository
    participant TransferDecisionService
    participant TransferEventListener
    
    Client->>TransferController: POST /transfers (TransferRequest)
    TransferController->>TransferTrackingService: createTransfer(request)
    TransferTrackingService->>TransferRepository: saveAndFlush(entity)
    TransferTrackingService->>TransferHistoryRepository: save(NEW)
    TransferController->>KafkaProducer: send("transfers", Transfer(id, NEW))
    KafkaProducer->>TransfersTopic: publish
    
    TransfersTopic->>LimitEventsListener: onEvent(Transfer)
    LimitEventsListener->>LimitService: onNewTransfer(transfer)
    LimitService->>LimitLedger: decide(transfer)
    LimitLedger->>SenderUsageRepository: findById / lazyReset
    LimitLedger->>ReservationRepository: save(reservation DECLINED)
    LimitLedger->>SenderUsageRepository: reserve(amount)
    LimitService->>KafkaProducer: send("limit-results", DECLINED, DAILY_LIMIT_EXCEEDED)
    KafkaProducer->>LimitResultsTopic: publish
    
    LimitResultsTopic->>TransferDecisionService: onEvent(limit DECLINED)
    TransferDecisionService->>TransferDecisionService: decide(DECLINED, null) -> CANCELLED
    TransferDecisionService->>KafkaProducer: send("transfers", CANCELLED, limit, DAILY_LIMIT_EXCEEDED)
    KafkaProducer->>TransfersTopic: publish
    
    TransfersTopic->>TransferEventListener: onEvent(CANCELLED)
    TransferEventListener->>TransferTrackingService: applyFinalStatus(CANCELLED)
    TransferTrackingService->>TransferRepository: save
    TransferTrackingService->>TransferHistoryRepository: save(FINAL)
    
    Note over ReservationRepository: Резервирование создано (state=RESERVED),<br>но при CANCELLED будет выполнено release()
    TransferEventListener->>TransferTrackingService: recordServiceResult(LIMIT)
    TransferTrackingService->>TransferHistoryRepository: save(LIMIT)
```

**Диаграмма 4: ER-диаграмма (все базы данных)**

```mermaid
erDiagram
    "transfer-service DB (transferdb)".transfers {
        BIGINT id PK
        BIGINT sender_id
        BIGINT recipient_id
        DECIMAL(15,2) amount
        VARCHAR(10) currency
        VARCHAR(20) status
        VARCHAR(20) decided_by
        TEXT message_info
        TIMESTAMP created_at
        TIMESTAMP updated_at
    }

    "transfer-service DB (transferdb)".transfer_history {
        BIGSERIAL id PK
        BIGINT transfer_id FK "references transfers(id)"
        VARCHAR(20) status
        VARCHAR(20) decided_by
        TEXT message_info
        TIMESTAMP changed_at
        VARCHAR(20) stage
    }

    "limit-service DB (limitdb)".sender_usage {
        BIGINT sender_id PK
        DECIMAL(15,2) daily_used
        DECIMAL(15,2) monthly_used
        DATE last_daily_reset
        DATE last_monthly_reset
        BIGINT version
    }

    "limit-service DB (limitdb)".reservations {
        BIGINT transfer_id PK
        BIGINT sender_id
        DECIMAL(15,2) amount
        VARCHAR(10) decision
        VARCHAR(50) reason
        VARCHAR(20) state
        DATE reserved_on
        TIMESTAMP created_at
        TIMESTAMP updated_at
    }

    "screening-service DB (screeningdb)".blocked_senders {
        BIGSERIAL id PK
        BIGINT sender_id
        VARCHAR(255) reason
        TIMESTAMP blocked_at
    }

    "screening-service DB (screeningdb)".screening_checks {
        BIGINT transfer_id PK
        BIGINT sender_id
        VARCHAR(10) result
        VARCHAR(50) reason
        TIMESTAMP checked_at
    }

    transfers ||--o{ transfer_history : "1:N"
    transfer_history }|--|| transfers : "FK transfer_id"
    
    "Logical links (no FK)":
    transfers.sender_id -> sender_usage.sender_id
    reservations.sender_id -> sender_usage.sender_id
    screening_checks.sender_id -> blocked_senders.sender_id
```

**Диаграмма 5: Диаграмма состояний — TransferEntity**

```mermaid
stateDiagram-v2
    state TransferEntity {
        [*] --> NEW
        NEW --> LIMIT : После публикации в Kafka
        LIMIT --> SCREENING : После получения limit-results
        SCREENING --> FINAL : После получения screening-results
        FINAL --> COMPLETED : if decision = COMPLETED
        FINAL --> CANCELLED : if decision = CANCELLED
        FINAL --> DENIED : if decision = DENIED
        
        state LIMIT {
            [*] --> PENDING
            PENDING --> APPROVED : if limit approved
            PENDING --> DECLINED : if limit declined
        }
        
        state SCREENING {
            [*] --> PENDING
            PENDING --> APPROVED : if screening approved
            PENDING --> DECLINED : if screening declined
        }
        
        COMPLETED --> [*]
        CANCELLED --> [*]
        DENIED --> [*]
    }
    
    note right of LIMIT : История: recordServiceResult(LIMIT)
    note right of SCREENING : История: recordServiceResult(SCREENING)
    note right of FINAL : История: applyFinalStatus()
```