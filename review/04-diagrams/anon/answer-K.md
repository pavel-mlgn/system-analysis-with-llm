## Диаграмма 1: Component diagram (C4)

Система transfer-limit-system состоит из трёх Spring Boot сервисов (transfer-service, limit-service, screening-service), трёх отдельных баз PostgreSQL и брокера Kafka с топиками transfers, limit-results, screening-results и их DLT-топиками.

```mermaid
flowchart TB
    client["Клиент"]

    subgraph transferService["transfer-service (порт 8080)"]
        controller["TransferController<br/>POST /transfers, GET /transfers, GET /transfers/{id}"]
        tracking["TransferTrackingService"]
        streams["DecisionTopologyConfig.decisionStream<br/>Kafka Streams join"]
        decisionSvc["TransferDecisionService"]
        tracker["TransferEventListener<br/>groupId transfer-tracker"]
    end

    subgraph limitService["limit-service"]
        limitListener["limit.TransferEventsListener<br/>groupId limit"]
        limitCore["LimitService / LimitLedger"]
    end

    subgraph screeningService["screening-service"]
        screeningListener["screening.TransferEventsListener<br/>groupId screening"]
        screeningCore["ScreeningService / ScreeningJournal"]
    end

    transferDb[("transfer-db<br/>PostgreSQL transferdb<br/>таблицы transfers, transfer_history")]
    limitDb[("limit-db<br/>PostgreSQL limitdb<br/>таблицы sender_usage, reservations")]
    screeningDb[("screening-db<br/>PostgreSQL screeningdb<br/>таблицы blocked_senders, screening_checks")]

    subgraph broker["Kafka broker — docker-compose.yml, образ confluentinc/cp-kafka:7.6.0"]
        transfersTopic[("topic transfers, 3 партиции, compact")]
        limitTopic[("topic limit-results, 3 партиции, compact")]
        screeningTopic[("topic screening-results, 3 партиции, compact")]
        dltTransfers[("topic transfers.DLT")]
        dltLimit[("topic limit-results.DLT")]
        dltScreening[("topic screening-results.DLT")]
    end

    client -- "HTTP REST: POST /transfers, GET /transfers, GET /transfers/{id}" --> controller
    controller -- "JPA/JDBC: TransferRepository, TransferHistoryRepository" --> transferDb
    tracking -- "JPA/JDBC: TransferRepository, TransferHistoryRepository" --> transferDb
    tracker -- "JPA/JDBC: recordServiceResult, applyFinalStatus" --> transferDb

    controller -- "Kafka JSON: KafkaTemplate.send(transfers) - publishNew" --> transfersTopic

    transfersTopic -- "consume @KafkaListener limit-transfers-listener" --> limitListener
    limitListener --> limitCore
    limitCore -- "JPA/JDBC: LimitLedger.decide, LimitLedger.settle" --> limitDb
    limitCore -- "Kafka JSON: KafkaTemplate.send(limit-results)" --> limitTopic

    transfersTopic -- "consume @KafkaListener screening-transfers-listener" --> screeningListener
    screeningListener --> screeningCore
    screeningCore -- "JPA/JDBC: ScreeningJournal.check" --> screeningDb
    screeningCore -- "Kafka JSON: KafkaTemplate.send(screening-results)" --> screeningTopic

    limitTopic -- "Kafka Streams: builder.stream(limit-results)" --> streams
    screeningTopic -- "Kafka Streams: builder.stream(screening-results)" --> streams
    streams --> decisionSvc
    decisionSvc -- "Kafka Streams JSON: to(transfers), окно join 10 c" --> transfersTopic

    transfersTopic -- "consume @KafkaListener transfer-tracker" --> tracker
    limitTopic -- "consume @KafkaListener transfer-tracker" --> tracker
    screeningTopic -- "consume @KafkaListener transfer-tracker" --> tracker

    limitListener -. "DefaultErrorHandler: FixedBackOff 1000 мс, 3 повторов -> DeadLetterPublishingRecoverer" .-> dltTransfers
    screeningListener -. "DefaultErrorHandler: FixedBackOff 1000 мс, 3 повторов -> DeadLetterPublishingRecoverer" .-> dltTransfers
    tracker -. "DefaultErrorHandler: FixedBackOff 1000 мс, 3 повторов -> DeadLetterPublishingRecoverer" .-> dltTransfers
    tracker -. "DefaultErrorHandler: FixedBackOff 1000 мс, 3 повторов -> DeadLetterPublishingRecoverer" .-> dltLimit
    tracker -. "DefaultErrorHandler: FixedBackOff 1000 мс, 3 повторов -> DeadLetterPublishingRecoverer" .-> dltScreening
```

Внешние системы, кроме клиента и брокера Kafka, в коде не найдены. Обработка сбоев Kafka Streams внутри decisionStream (конфигурация exception handler для Streams) в коде не найдена — требует уточнения.

## Диаграмма 2: Sequence diagram — happy path

Основной успешный сценарий: клиент создаёт перевод через POST /transfers, две проверки выносят APPROVED, Kafka Streams соединяет результаты и публикует финальный статус COMPLETED в transfers.

```mermaid
sequenceDiagram
    autonumber
    actor Client as Клиент
    participant TC as TransferController
    participant TTS as TransferTrackingService
    participant TDB as transfer-db
    participant K as Kafka broker
    participant LL as limit.TransferEventsListener
    participant LLM as LimitService
    participant Ledger as LimitLedger
    participant LDB as limit-db
    participant SL as screening.TransferEventsListener
    participant SSM as ScreeningService
    participant SJ as ScreeningJournal
    participant SDB as screening-db
    participant DS as DecisionTopologyConfig.decisionStream
    participant TDS as TransferDecisionService
    participant TEL as transfer.TransferEventListener

    Client->>TC: POST /transfers - TransferRequest
    TC->>TTS: createTransfer
    TTS->>TDB: save transfers (status NEW) + transfer_history (stage NEW)
    TTS-->>TC: TransferEntity
    TC->>K: KafkaTemplate.send(transfers) - Transfer.status NEW - publishNew
    K-->>TC: ack (ожидание get 10 секунд)
    TC-->>Client: 201 Created - TransferEntity
    K->>LL: consume transfers groupId limit - onEvent status NEW
    LL->>LLM: onNewTransfer
    LLM->>Ledger: decide(transfer)
    Ledger->>LDB: findById sender_usage - lazyReset
    LDB-->>Ledger: usage
    Ledger->>LDB: usage.reserve - save sender_usage
    Ledger->>LDB: save reservations (APPROVED - state RESERVED)
    LLM->>K: KafkaTemplate.send(limit-results) - Transfer APPROVED decidedBy limit
    K->>SL: consume transfers groupId screening - onEvent status NEW
    SL->>SSM: onNewTransfer
    SSM->>SJ: check(transfer)
    SJ->>SDB: findBySenderId blocked_senders - отправитель не заблокирован
    SJ->>SDB: save screening_checks (APPROVED)
    SSM->>K: KafkaTemplate.send(screening-results) - Transfer APPROVED decidedBy screening
    K->>DS: consume limit-results и screening-results
    DS->>TDS: decide(limitResult - screeningResult) в окне 10 с
    TDS-->>DS: Transfer COMPLETED
    DS->>K: to(transfers) - Transfer COMPLETED
    K->>TEL: consume transfers groupId transfer-tracker - onEvent status не NEW
    TEL->>TTS: applyFinalStatus
    TTS->>TDB: update transfers (status COMPLETED) + transfer_history (stage FINAL)
    Client->>TC: GET /transfers/{id}
    TC-->>Client: 200 OK - transfer + history
```

## Диаграмма 3: Sequence diagram — сценарий отказа

Показательный отказ: проверка лимита отклоняет перевод (DAILY_LIMIT_EXCEEDED), резерв уже создан и учтён в дневном расходе, поэтому после финального статуса CANCELLED LimitLedger.settle снимает резерв и возвращает сумму в счётчике отправителя.

```mermaid
sequenceDiagram
    autonumber
    actor Client as Клиент
    participant TC as TransferController
    participant TTS as TransferTrackingService
    participant TDB as transfer-db
    participant K as Kafka broker
    participant LL as limit.TransferEventsListener
    participant LLM as LimitService
    participant Ledger as LimitLedger
    participant LDB as limit-db
    participant SL as screening.TransferEventsListener
    participant SSM as ScreeningService
    participant SJ as ScreeningJournal
    participant SDB as screening-db
    participant DS as DecisionTopologyConfig.decisionStream
    participant TDS as TransferDecisionService
    participant TEL as transfer.TransferEventListener

    Client->>TC: POST /transfers - TransferRequest
    TC->>TTS: createTransfer
    TTS->>TDB: save transfers (status NEW) + transfer_history (stage NEW)
    TC->>K: KafkaTemplate.send(transfers) - Transfer.status NEW
    TC-->>Client: 201 Created
    K->>LL: consume transfers groupId limit - onEvent status NEW
    LL->>LLM: onNewTransfer
    LLM->>Ledger: decide(transfer)
    Ledger->>LDB: findById sender_usage
    Note over Ledger,LDB: dailyUsed + amount больше dailyMax 50000.00
    Ledger->>LDB: save reservations - decline DAILY_LIMIT_EXCEEDED - state NONE - decision DECLINED
    LLM->>K: KafkaTemplate.send(limit-results) - Transfer DECLINED decidedBy limit
    K->>SL: consume transfers groupId screening - onEvent status NEW
    SL->>SSM: onNewTransfer
    SSM->>SJ: check(transfer)
    SJ->>SDB: findBySenderId blocked_senders - отправитель не заблокирован
    SJ->>SDB: save screening_checks (APPROVED)
    SSM->>K: KafkaTemplate.send(screening-results) - Transfer APPROVED
    K->>DS: consume limit-results и screening-results
    DS->>TDS: decide(limitResult - screeningResult)
    Note over TDS: limitDeclined и screeningApproved - ветка CANCELLED с decidedBy limit
    TDS-->>DS: Transfer CANCELLED - messageInfo DAILY_LIMIT_EXCEEDED
    DS->>K: to(transfers) - Transfer CANCELLED
    K->>LL: consume transfers groupId limit - onEvent status CANCELLED - не NEW
    LL->>LLM: onFinalStatus
    LLM->>Ledger: settle(transfer)
    Ledger->>LDB: findById reservations - state RESERVED
    Ledger->>LDB: SenderUsage.release - возврат суммы в daily_used и monthly_used
    Ledger->>LDB: reservation.markReleased - state RELEASED
    K->>TEL: consume transfers groupId transfer-tracker
    TEL->>TTS: applyFinalStatus
    TTS->>TDB: update transfers (status CANCELLED) + transfer_history (stage FINAL)
    Client->>TC: GET /transfers/{id}
    TC-->>Client: 200 OK - CANCELLED - история FINAL
```

Дополнительно из кода: при неуспешной обработке любого сообщенияlistener'ы после 3 повторов (FixedBackOff 1000 мс) публикуют его в DLT-топик (DeadLetterPublishingRecoverer, KafkaErrorHandlingConfig); если Kafka недоступна при publishNew, TransferController выбрасывает ResponseStatusException 503 с текстом «Kafka недоступна, перевод сохранён — повторите запрос с тем же id», при этом перевод уже сохранён в transfer-db со статусом NEW.

## Диаграмма 4а: ER — transfer-db (PostgreSQL transferdb)

Схема из limit-… точнее из transfer-service: `transfer-service/src/main/resources/db/changelog/001-create-tables.sql`.

```mermaid
erDiagram
    transfers {
        bigint id PK
        bigint sender_id
        bigint recipient_id
        decimal amount
        varchar currency
        varchar status
        varchar decided_by
        text message_info
        timestamp created_at
        timestamp updated_at
    }
    transfer_history {
        bigserial id PK
        bigint transfer_id FK
        varchar stage
        varchar status
        varchar decided_by
        text message_info
        timestamp changed_at
    }
    transfers ||--o{ transfer_history : "transfer_id (FK)"
```

Уникальность transfer_history: CONSTRAINT uq_transfer_history_stage UNIQUE (transfer_id, stage) — stage перечисления HistoryStage: NEW, LIMIT, SCREENING, FINAL.

## Диаграмма 4б: ER — limit-db (PostgreSQL limitdb)

Схема из `limit-service/src/main/resources/db/changelog/001-create-tables.sql`; связь sender_id внешним ключом не оформлена.

```mermaid
erDiagram
    sender_usage {
        bigint sender_id PK
        decimal daily_used
        decimal monthly_used
        date last_daily_reset
        date last_monthly_reset
        bigint version
    }
    reservations {
        bigint transfer_id PK
        bigint sender_id
        decimal amount
        varchar decision
        varchar reason
        varchar state
        date reserved_on
        timestamp created_at
        timestamp updated_at
    }
    reservations }o..|| sender_usage : "sender_id (логическая связь)"
```

## Диаграмма 4в: ER — screening-db (PostgreSQL screeningdb)

Схема из `screening-service/src/main/resources/db/changelog/001-create-tables.sql` (включая тестовые данные 002-insert-test-data.sql для blocked_senders).

```mermaid
erDiagram
    blocked_senders {
        bigserial id PK
        bigint sender_id
        varchar reason
        timestamp blocked_at
    }
    screening_checks {
        bigint transfer_id PK
        bigint sender_id
        varchar result
        varchar reason
        timestamp checked_at
    }
    blocked_senders ||..o{ screening_checks : "sender_id (логическая связь)"
```

## Диаграмма 4г: ER — объекты, не хранящиеся в БД (сообщения и запросы)

Поля из transfer-common `Transfer.java` и transfer-service `TransferRequest.java`.

```mermaid
erDiagram
    TransferRequest {
        Long id
        Long senderId
        Long recipientId
        BigDecimal amount
        String currency
    }
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
    TransferRequest ||..|| Transfer : "создаётся в TransferTrackingService.createTransfer"
```

**Логические связи между сущностями разных диаграмм (без FK в коде):**
- transfers.id ↔ Transfer.id (сообщение Kafka) ↔ reservations.transfer_id ↔ screening_checks.transfer_id;
- transfers.sender_id ↔ reservations.sender_id ↔ sender_usage.sender_id ↔ blocked_senders.sender_id ↔ screening_checks.sender_id ↔ Transfer.senderId;
- Transfer.id ↔ TransferRequest.id (один и тот же идентификатор перевода, задаваемый клиентом).

## Диаграмма 5: Диаграмма состояний — Transfer (статусы)

Ключевая бизнес-сущность со жизненным циклом — статус перевода (TransferStatus в openapi/transfer-api.yaml, поля TransferEntity.status); переходы выполняет TransferDecisionService.decide через Kafka Streams, а применяет TransferTrackingService.applyFinalStatus.

```mermaid
stateDiagram-v2
    [*] --> NEW : TransferTrackingService.createTransfer
    NEW --> COMPLETED : TransferDecisionService.decide - limit APPROVED и screening APPROVED
    NEW --> CANCELLED : TransferDecisionService.decide - только limit DECLINED - decidedBy limit
    NEW --> CANCELLED : TransferDecisionService.decide - только screening DECLINED - decidedBy screening
    NEW --> CANCELLED : TransferDecisionService.decide - UNEXPECTED_RESULT
    NEW --> DENIED : TransferDecisionService.decide - limit DECLINED и screening DECLINED
    COMPLETED --> [*]
    DENIED --> [*]
    CANCELLED --> [*]
```

Ограничение из кода: решение издаётся только если результаты limit-results и screening-results попали в окно Kafka Streams 10 секунд (JoinWindows.ofTimeDifferenceWithNoGrace в DecisionTopologyConfig); при выходе результата за окно перевод остаётся в NEW, явной обработки этого случая в коде не найдено — требует уточнения.

Другие сущности с жизненным циклом: Reservation в limit-service (`limit/domain/Reservation.java`) — состояния NONE (отказ решением decline), RESERVED (approve), COMMITTED (markCommitted по статусу COMPLETED), RELEASED (markReleased по CANCELLED/DENIED в LimitLedger.settle). У ScreeningCheck жизненного цикла в коде не найдено — она создаётся один раз с результатом APPROVED или DECLINED.