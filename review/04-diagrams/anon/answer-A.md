### ДИАГРАММА 1: Component diagram (C4)
Отображает основные компоненты системы (микросервисы, базы данных, брокер Kafka) и связи между ними, включая протоколы и названия топиков/эндпоинтов.

```mermaid
flowchart TB
    Client["Client (User / External System)"]

    subgraph "Transfer-Limit-System"
        subgraph "Transfer Service"
            TS_API["TransferController (REST)"]
            TS_Service["TransferTrackingService\nTransferDecisionService"]
            TS_Streams["Kafka Streams\n(DecisionTopologyConfig)"]
        end

        subgraph "Limit Service"
            LS_App["LimitService\nLimitLedger"]
        end

        subgraph "Screening Service"
            SS_App["ScreeningService\nScreeningJournal"]
        end

        subgraph "Databases (PostgreSQL)"
            DB_Transfer[("Transfer DB\n(port 5434)")]
            DB_Limit[("Limit DB\n(port 5432)")]
            DB_Screening[("Screening DB\n(port 5433)")]
        end

        subgraph "Kafka (Confluent)"
            K_Transfers[["Topic: transfers"]]
            K_LimitRes[["Topic: limit-results"]]
            K_ScreenRes[["Topic: screening-results"]]
            K_DLT[["DLT Topics\n(transfers.DLT, etc.)"]]
        end
    end

    Client -->|"HTTP POST /transfers"| TS_API
    Client -->|"HTTP GET /transfers"| TS_API

    TS_API -->|"JDBC / JPA"| DB_Transfer
    TS_Service -->|"JDBC / JPA"| DB_Transfer

    LS_App -->|"JDBC / JPA"| DB_Limit
    SS_App -->|"JDBC / JPA"| DB_Screening

    TS_API -->|"Producer.send()"| K_Transfers
    K_Transfers -->|"@KafkaListener"| LS_App
    K_Transfers -->|"@KafkaListener"| SS_App
    K_Transfers -->|"@KafkaListener"| TS_Service

    LS_App -->|"Producer.send()"| K_LimitRes
    SS_App -->|"Producer.send()"| K_ScreenRes

    K_LimitRes -->|"KStream.join"| TS_Streams
    K_ScreenRes -->|"KStream.join"| TS_Streams
    TS_Streams -->|"Result stream"| K_Transfers

    K_LimitRes -->|"@KafkaListener"| TS_Service
    K_ScreenRes -->|"@KafkaListener"| TS_Service
```

### ДИАГРАММА 2: Sequence diagram — основной успешный сценарий (happy path)
Сценарий успешного создания перевода: запрос сохраняется, отправляется в Kafka, сервисы лимитов и скрининга одобряют перевод, Kafka Streams агрегирует результаты, перевод переводится в статус `COMPLETED`.

```mermaid
sequenceDiagram
    participant Client
    participant TransferController
    participant TransferTrackingService
    participant TopicTransfers as Topic: transfers
    participant LimitService
    participant ScreeningService
    participant TopicLimit as Topic: limit-results
    participant TopicScreening as Topic: screening-results
    participant DecisionTopologyConfig as Kafka Streams
    participant TransferEventListener

    Client->>TransferController: "POST /transfers (TransferRequest)"
    TransferController->>TransferTrackingService: "createTransfer(request)"
    TransferTrackingService-->>TransferController: "TransferEntity (status=NEW)"
    TransferController->>TopicTransfers: "send('transfers', Transfer(NEW))"
    TransferController-->>Client: "201 Created"

    par Limit Validation
        TopicTransfers->>LimitService: "onEvent(Transfer(NEW))"
        LimitService->>LimitService: "LimitLedger.decide() -> APPROVED"
        LimitService->>TopicLimit: "send('limit-results', Transfer(APPROVED))"
    and Screening Validation
        TopicTransfers->>ScreeningService: "onEvent(Transfer(NEW))"
        ScreeningService->>ScreeningService: "ScreeningJournal.check() -> APPROVED"
        ScreeningService->>TopicScreening: "send('screening-results', Transfer(APPROVED))"
    end

    TopicLimit->>DecisionTopologyConfig: "consume stream"
    TopicScreening->>DecisionTopologyConfig: "consume stream"
    DecisionTopologyConfig->>DecisionTopologyConfig: "TransferDecisionService.decide(limit, screening)"
    DecisionTopologyConfig->>TopicTransfers: "send('transfers', Transfer(COMPLETED))"

    par Save Service Results History
        TopicLimit->>TransferEventListener: "onEvent(limit result)"
        TransferEventListener->>TransferTrackingService: "recordServiceResult(LIMIT)"
    and
        TopicScreening->>TransferEventListener: "onEvent(screening result)"
        TransferEventListener->>TransferTrackingService: "recordServiceResult(SCREENING)"
    end

    par Process Final Status
        TopicTransfers->>TransferEventListener: "onEvent(Transfer(COMPLETED))"
        TransferEventListener->>TransferTrackingService: "applyFinalStatus(COMPLETED)"
    and Settle Limit Reservation
        TopicTransfers->>LimitService: "onEvent(Transfer(COMPLETED))"
        LimitService->>LimitService: "LimitLedger.settle() -> markCommitted()"
    and Screening Nothing To Do
        TopicTransfers->>ScreeningService: "onEvent(Transfer(COMPLETED))"
    end
```

### ДИАГРАММА 3: Sequence diagram — основной сценарий отказа
Сценарий отказа по вине скрининга: сервис скрининга находит отправителя в черном списке и отклоняет перевод (`DECLINED`), сервис лимитов при этом одобряет (`APPROVED`). Итоговый статус перевода становится `CANCELLED`, а сервис лимитов возвращает зарезервированные средства.

```mermaid
sequenceDiagram
    participant Client
    participant TransferController
    participant TransferTrackingService
    participant TopicTransfers as Topic: transfers
    participant LimitService
    participant ScreeningService
    participant TopicLimit as Topic: limit-results
    participant TopicScreening as Topic: screening-results
    participant DecisionTopologyConfig as Kafka Streams
    participant TransferEventListener

    Client->>TransferController: "POST /transfers (TransferRequest)"
    TransferController->>TransferTrackingService: "createTransfer(request)"
    TransferController->>TopicTransfers: "send('transfers', Transfer(NEW))"
    TransferController-->>Client: "201 Created"

    par Limit Validation (OK)
        TopicTransfers->>LimitService: "onEvent(Transfer(NEW))"
        LimitService->>LimitService: "LimitLedger.decide() -> APPROVED"
        LimitService->>TopicLimit: "send('limit-results', Transfer(APPROVED))"
    and Screening Validation (Failed)
        TopicTransfers->>ScreeningService: "onEvent(Transfer(NEW))"
        ScreeningService->>ScreeningService: "ScreeningJournal.check() -> DECLINED (Blocked)"
        ScreeningService->>TopicScreening: "send('screening-results', Transfer(DECLINED))"
    end

    TopicLimit->>DecisionTopologyConfig: "consume stream"
    TopicScreening->>DecisionTopologyConfig: "consume stream"
    DecisionTopologyConfig->>DecisionTopologyConfig: "TransferDecisionService.decide(APPROVED, DECLINED) -> CANCELLED"
    DecisionTopologyConfig->>TopicTransfers: "send('transfers', Transfer(CANCELLED))"

    par Save History
        TopicLimit->>TransferEventListener: "onEvent(limit result)"
        TransferEventListener->>TransferTrackingService: "recordServiceResult(LIMIT)"
    and
        TopicScreening->>TransferEventListener: "onEvent(screening result)"
        TransferEventListener->>TransferTrackingService: "recordServiceResult(SCREENING)"
    end

    par Apply Final Status
        TopicTransfers->>TransferEventListener: "onEvent(Transfer(CANCELLED))"
        TransferEventListener->>TransferTrackingService: "applyFinalStatus(CANCELLED)"
    and Rollback Limit
        TopicTransfers->>LimitService: "onEvent(Transfer(CANCELLED))"
        LimitService->>LimitService: "LimitLedger.settle() -> usage.release(), markReleased()"
    and Screening Ignore
        TopicTransfers->>ScreeningService: "onEvent(Transfer(CANCELLED))"
    end
```

### ДИАГРАММА 4: ER-диаграмма
ER-модель разбита на 4 отдельные диаграммы (по одной на каждую базу данных + объекты в памяти/Kafka).

#### 4а. Transfer DB (transfer-db)
Отражает таблицы сервиса переводов и их физические связи (внешний ключ).
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

    transfers ||--o{ transfer_history : "id = transfer_id"
```

#### 4б. Limit DB (limit-db)
Отражает таблицы сервиса лимитов. В коде нет физических внешних ключей, но есть логическая связь.
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

    sender_usage ||..o{ reservations : "logical relation (sender_id)"
```

#### 4в. Screening DB (screening-db)
Отражает таблицы сервиса проверок (скрининга). Физических внешних ключей нет, связь логическая по `sender_id`.
```mermaid
erDiagram
    blocked_senders {
        BIGINT id PK
        BIGINT sender_id
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

    blocked_senders ||..o{ screening_checks : "logical relation (sender_id)"
```

#### 4г. Объекты передачи данных (Kafka Messages / DTOs)
Отражает записи (records), не хранящиеся в БД, передаваемые между сервисами.
```mermaid
erDiagram
    Transfer {
        Long id
        Long senderId
        Long recipientId
        BigDecimal amount
        String currency
        String status
        String decidedBy
        String messageInfo
        LocalDateTime createdAt
    }

    TransferRequest {
        Long id
        Long senderId
        Long recipientId
        BigDecimal amount
        String currency
    }
    
    TransferRequest ||..|| Transfer : "Maps to via TransferTrackingService"
```

**Логические связи между сущностями разных БД и объектов (по идентификаторам):**
*   `TransferRequest.id` == `Transfer.id` == `transfers.id` (Transfer DB)
*   `Transfer.id` == `reservations.transfer_id` (Limit DB) == `screening_checks.transfer_id` (Screening DB) == `transfer_history.transfer_id` (Transfer DB).
*   `TransferRequest.senderId` == `Transfer.senderId` == `transfers.sender_id` (Transfer DB) == `sender_usage.sender_id` (Limit DB) == `reservations.sender_id` (Limit DB) == `blocked_senders.sender_id` (Screening DB) == `screening_checks.sender_id` (Screening DB).

### ДИАГРАММА 5: Диаграмма состояний
Отображает жизненный цикл сущности `Transfer` (поле `status`), включая её создание, проверку и итоговые состояния (агрегация которых происходит в `TransferDecisionService.decide()`).

```mermaid
stateDiagram-v2
    [*] --> NEW : "POST /transfers (TransferTrackingService.createTransfer)"
    
    NEW --> COMPLETED : "LimitResult == APPROVED\nScreeningResult == APPROVED"
    NEW --> DENIED : "LimitResult == DECLINED\nScreeningResult == DECLINED"
    NEW --> CANCELLED : "LimitResult == DECLINED\nScreeningResult == APPROVED"
    NEW --> CANCELLED : "LimitResult == APPROVED\nScreeningResult == DECLINED"
    NEW --> CANCELLED : "Любая другая комбинация (UNEXPECTED_RESULT)"
    
    COMPLETED --> [*]
    DENIED --> [*]
    CANCELLED --> [*]
```
*(Сущность `Reservation` из сервиса лимитов также имеет свой внутренний жизненный цикл: `NONE` / `RESERVED` -> `COMMITTED` / `RELEASED` — в зависимости от финального статуса `Transfer`).*