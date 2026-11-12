# Диаграммы системы transfer-limit-system

## Диаграмма 1. Component diagram (C4)

Компоненты трёх Spring Boot-сервисов (transfer-service, limit-service, screening-service), их базы PostgreSQL, Kafka-брокер с рабочими топиками и DLT и внешний клиент REST API. Сплошные стрелки — синхронные вызовы (HTTP, Java, JDBC), пунктирные — асинхронный обмен через Kafka (ключ Long = Transfer.id, значение — Transfer в JSON).

```mermaid
flowchart TB
    client(["Клиент REST API — внешняя система, в коде не определена, требует уточнения"])
    common[["transfer-common — общая библиотека: record com.example.common.Transfer, формат значения во всех топиках"]]

    subgraph SYS["Система transfer-limit-system — docker-compose.yml"]

        subgraph TS["transfer-service — Spring Boot 3.4.1, HTTP :8080"]
            t_ctrl["TransferController — REST /transfers"]
            t_exh["ApiExceptionHandler — ProblemDetail HTTP 500"]
            t_track["TransferTrackingService"]
            t_listener["TransferEventListener — @KafkaListener transfer-tracker"]
            t_topology["DecisionTopologyConfig.decisionStream — Kafka Streams join, LogAndContinueExceptionHandler"]
            t_decision["TransferDecisionService"]
            t_repo_tr["TransferRepository"]
            t_repo_hist["TransferHistoryRepository"]
            t_state[("Kafka Streams state-dir /tmp/transfer-service-streams")]
        end

        subgraph TDB["transfer-db — PostgreSQL 16, jdbc:postgresql://transfer-db:5432/transferdb, с хоста :5434"]
            tdb_transfers[("transfers")]
            tdb_history[("transfer_history")]
        end

        subgraph KAFKA["broker — Kafka confluentinc/cp-kafka:7.6.0, KRaft, broker:29092, с хоста localhost:9092"]
            k_transfers[/"transfers — 3 партиции, compact"/]
            k_limit[/"limit-results — 3 партиции, compact"/]
            k_screen[/"screening-results — 3 партиции, compact"/]
            k_tdlt[/"transfers.DLT — потребитель в коде не найден"/]
            k_ldlt[/"limit-results.DLT — потребитель в коде не найден"/]
            k_sdlt[/"screening-results.DLT — потребитель в коде не найден"/]
            k_note["Топики объявлены бинами NewTopic в TopicsConfig и KafkaErrorHandlingConfig сервисов, автосоздание топиков отключено"]
        end

        subgraph LS["limit-service — Spring Boot 3.4.1, без HTTP API"]
            l_listener["TransferEventsListener — @KafkaListener limit-transfers-listener"]
            l_service["LimitService"]
            l_ledger["LimitLedger — limits.daily-max 50000.00, limits.monthly-max 200000.00"]
            l_repo_usage["SenderUsageRepository"]
            l_repo_res["ReservationRepository"]
        end

        subgraph LDB["limit-db — PostgreSQL 16, jdbc:postgresql://limit-db:5432/limitdb, с хоста :5432"]
            ldb_usage[("sender_usage")]
            ldb_res[("reservations")]
        end

        subgraph SS["screening-service — Spring Boot 3.4.1, без HTTP API"]
            s_listener["TransferEventsListener — @KafkaListener screening-transfers-listener"]
            s_service["ScreeningService"]
            s_journal["ScreeningJournal"]
            s_repo_blocked["BlockedSenderRepository"]
            s_repo_check["ScreeningCheckRepository"]
        end

        subgraph SDB["screening-db — PostgreSQL 16, jdbc:postgresql://screening-db:5432/screeningdb, с хоста :5433"]
            sdb_blocked[("blocked_senders")]
            sdb_checks[("screening_checks")]
        end
    end

    client -->|"HTTP/JSON: POST /transfers, GET /transfers, GET /transfers/{id}"| t_ctrl
    t_ctrl -->|"Java: createTransfer"| t_track
    t_ctrl -->|"Java: findById, findAllByOrderByCreatedAtDesc"| t_repo_tr
    t_ctrl -->|"Java: findByTransferIdOrderByChangedAtAsc"| t_repo_hist
    t_ctrl -->|"Java: необработанные исключения, ответ HTTP 500"| t_exh
    t_ctrl -.->|"Kafka: топик transfers, Transfer NEW"| k_transfers
    t_track -->|"Java: saveAndFlush, findById, save"| t_repo_tr
    t_track -->|"Java: save, existsByTransferIdAndStage"| t_repo_hist
    t_repo_tr -->|"JDBC: таблица transfers"| tdb_transfers
    t_repo_hist -->|"JDBC: таблица transfer_history"| tdb_history
    k_transfers -.->|"Kafka: transfers, group transfer-tracker"| t_listener
    k_limit -.->|"Kafka: limit-results, group transfer-tracker"| t_listener
    k_screen -.->|"Kafka: screening-results, group transfer-tracker"| t_listener
    t_listener -->|"Java: recordServiceResult, applyFinalStatus"| t_track
    k_limit -.->|"Kafka Streams: stream limit-results"| t_topology
    k_screen -.->|"Kafka Streams: stream screening-results"| t_topology
    t_topology -->|"Java: decide в join, окно 10 с"| t_decision
    t_topology -->|"файловая система: состояние окон join"| t_state
    t_topology -.->|"Kafka Streams: to transfers, COMPLETED, CANCELLED, DENIED"| k_transfers
    t_listener -.->|"Kafka: transfers.DLT, DefaultErrorHandler после 3 повторов через 1 с"| k_tdlt
    t_listener -.->|"Kafka: limit-results.DLT"| k_ldlt
    t_listener -.->|"Kafka: screening-results.DLT"| k_sdlt

    k_transfers -.->|"Kafka: transfers, group limit"| l_listener
    l_listener -->|"Java: onNewTransfer, onFinalStatus"| l_service
    l_service -->|"Java: decide, settle"| l_ledger
    l_service -.->|"Kafka: топик limit-results, APPROVED или DECLINED"| k_limit
    l_ledger -->|"Java: findById, save"| l_repo_usage
    l_ledger -->|"Java: findById, save"| l_repo_res
    l_repo_usage -->|"JDBC: таблица sender_usage"| ldb_usage
    l_repo_res -->|"JDBC: таблица reservations"| ldb_res
    l_listener -.->|"Kafka: transfers.DLT, DefaultErrorHandler после 3 повторов через 1 с"| k_tdlt

    k_transfers -.->|"Kafka: transfers, group screening"| s_listener
    s_listener -->|"Java: onNewTransfer"| s_service
    s_service -->|"Java: check"| s_journal
    s_service -.->|"Kafka: топик screening-results, APPROVED или DECLINED"| k_screen
    s_journal -->|"Java: findBySenderId"| s_repo_blocked
    s_journal -->|"Java: findById, save"| s_repo_check
    s_repo_blocked -->|"JDBC: таблица blocked_senders"| sdb_blocked
    s_repo_check -->|"JDBC: таблица screening_checks"| sdb_checks
    s_listener -.->|"Kafka: transfers.DLT, DefaultErrorHandler после 3 повторов через 1 с"| k_tdlt

    classDef dlt stroke-dasharray: 5 5
    class k_tdlt,k_ldlt,k_sdlt dlt
```

## Диаграмма 2. Sequence diagram — основной успешный сценарий (happy path)

Создание перевода через POST /transfers, параллельные проверки в limit-service и screening-service, итоговое решение COMPLETED в Kafka Streams и фиксация резерва лимита. Клиент получает 201 Created сразу со статусом NEW, итоговый статус виден через GET /transfers/{id}.

```mermaid
sequenceDiagram
    autonumber
    actor Client as Клиент API
    box transfer-service
        participant TC as TransferController
        participant TTS as TransferTrackingService
        participant TEL as TransferEventListener
        participant DTC as DecisionTopologyConfig.decisionStream (Kafka Streams)
        participant TDS as TransferDecisionService
    end
    participant TDB as transfer-db
    participant K as Kafka broker
    box limit-service
        participant LEL as TransferEventsListener (limit)
        participant LS as LimitService
        participant LL as LimitLedger
    end
    participant LDB as limit-db
    box screening-service
        participant SEL as TransferEventsListener (screening)
        participant SS as ScreeningService
        participant SJ as ScreeningJournal
    end
    participant SDB as screening-db

    Client->>TC: POST /transfers, TransferRequest (id, senderId, recipientId, amount, currency)
    Note over TC: @Valid: @NotNull, @Positive, @Digits(13,2), currency [A-Z]{3}, по умолчанию RUB
    TC->>TDB: TransferRepository.findById(id)
    TDB-->>TC: Optional.empty, перевода с таким id нет
    TC->>TTS: createTransfer(request)
    TTS->>TDB: TransferRepository.saveAndFlush(TransferEntity status=NEW)
    TTS->>TDB: TransferHistoryRepository.save(stage=NEW, status=NEW)
    Note over TTS,TDB: одна транзакция @Transactional, INSERT в transfers и transfer_history
    TTS-->>TC: TransferEntity
    TC->>TC: publishNew(entity), Transfer.newTransfer(...)
    TC-)K: KafkaTemplate.send(transfers, id, Transfer status=NEW).get(10 s)
    K-->>TC: SendResult, запись подтверждена брокером
    TC-->>Client: 201 Created, TransferEntity status=NEW

    par limit-service, consumer group limit
        K-)LEL: onEvent(transfer), топик transfers, status=NEW
        LEL->>LS: onNewTransfer(transfer)
        LS->>LL: decide(transfer)
        LL->>LDB: ReservationRepository.findById(transferId)
        LDB-->>LL: Optional.empty, не дубликат
        LL->>LDB: SenderUsageRepository.findById(senderId)
        LDB-->>LL: SenderUsage или new SenderUsage(senderId, today)
        LL->>LL: usage.lazyReset(today)
        LL->>LL: dailyUsed + amount ≤ limits.daily-max и monthlyUsed + amount ≤ limits.monthly-max
        LL->>LL: usage.reserve(amount), reservation.approve(): decision=APPROVED, state=RESERVED
        LL->>LDB: SenderUsageRepository.save(usage), ReservationRepository.save(reservation)
        Note over LL,LDB: одна транзакция @Transactional, sender_usage с @Version
        LL-->>LS: Reservation decision=APPROVED
        LS-)K: sendAndWait: send(limit-results, id, withDecision(APPROVED, limit, null)).get(10 s)
    and screening-service, consumer group screening
        K-)SEL: onEvent(transfer), топик transfers, status=NEW
        SEL->>SS: onNewTransfer(transfer)
        SS->>SJ: check(transfer)
        SJ->>SDB: ScreeningCheckRepository.findById(transferId)
        SDB-->>SJ: Optional.empty, не дубликат
        SJ->>SDB: BlockedSenderRepository.findBySenderId(senderId)
        SDB-->>SJ: Optional.empty, отправитель не заблокирован
        SJ->>SDB: ScreeningCheckRepository.save(ScreeningCheck result=APPROVED)
        SJ-->>SS: ScreeningCheck result=APPROVED
        SS-)K: sendAndWait: send(screening-results, id, withDecision(APPROVED, screening, null)).get(10 s)
    and transfer-service, consumer group transfer-tracker
        K-)TEL: onEvent(transfer, topic=transfers), status=NEW
        Note over TEL: NEW в топике transfers игнорируется
    end

    par фиксация промежуточных результатов
        K-)TEL: onEvent, топик limit-results, status=APPROVED, decidedBy=limit
        TEL->>TTS: recordServiceResult(transfer, HistoryStage.LIMIT)
        TTS->>TDB: existsByTransferIdAndStage(id, LIMIT) = false, save(история LIMIT), save(updatedAt)
        K-)TEL: onEvent, топик screening-results, status=APPROVED, decidedBy=screening
        TEL->>TTS: recordServiceResult(transfer, HistoryStage.SCREENING)
        TTS->>TDB: existsByTransferIdAndStage(id, SCREENING) = false, save(история SCREENING), save(updatedAt)
    and итоговое решение
        K-)DTC: записи limit-results и screening-results с одним ключом id
        Note over DTC: join, JoinWindows.ofTimeDifferenceWithNoGrace(10 s)
        DTC->>TDS: decide(limitResult, screeningResult)
        TDS-->>DTC: limitResult.withDecision(COMPLETED, null, null)
        DTC-)K: peek, затем to(transfers): Transfer status=COMPLETED
    end

    par transfer-service
        K-)TEL: onEvent, топик transfers, status=COMPLETED
        TEL->>TTS: applyFinalStatus(transfer)
        TTS->>TDB: existsByTransferIdAndStage(id, FINAL) = false
        TTS->>TDB: TransferRepository.save(status=COMPLETED), TransferHistoryRepository.save(stage=FINAL)
    and limit-service
        K-)LEL: onEvent, топик transfers, status=COMPLETED
        LEL->>LS: onFinalStatus(transfer)
        LS->>LL: settle(transfer)
        LL->>LDB: ReservationRepository.findById(transferId)
        LDB-->>LL: Reservation state=RESERVED
        LL->>LL: reservation.markCommitted(): state=COMMITTED
        LL->>LDB: ReservationRepository.save(reservation)
        Note over LL,LDB: sender_usage не меняется, сумма остаётся в daily_used и monthly_used
    and screening-service
        K-)SEL: onEvent, топик transfers, status=COMPLETED
        Note over SEL: не NEW, только лог: screening holds no reservations
    end

    Client->>TC: GET /transfers/{id}
    TC->>TDB: findById(id), findByTransferIdOrderByChangedAtAsc(id)
    TDB-->>TC: TransferEntity и история NEW, LIMIT, SCREENING, FINAL
    TC-->>Client: 200 OK, transfer status=COMPLETED и history, сортировка по stage, затем changedAt
```

## Диаграмма 3. Sequence diagram — основной сценарий отказа

Отправитель есть в blocked_senders: screening-service отклоняет перевод с причиной SENDER_BLOCKED, когда limit-service уже зарезервировал сумму. Итоговый статус — CANCELLED, а уже выполненный резерв компенсируется в LimitLedger.settle (RESERVED → RELEASED).

```mermaid
sequenceDiagram
    autonumber
    actor Client as Клиент API
    box transfer-service
        participant TC as TransferController
        participant TTS as TransferTrackingService
        participant TEL as TransferEventListener
        participant DTC as DecisionTopologyConfig.decisionStream (Kafka Streams)
        participant TDS as TransferDecisionService
    end
    participant TDB as transfer-db
    participant K as Kafka broker
    box limit-service
        participant LEL as TransferEventsListener (limit)
        participant LS as LimitService
        participant LL as LimitLedger
    end
    participant LDB as limit-db
    box screening-service
        participant SEL as TransferEventsListener (screening)
        participant SS as ScreeningService
        participant SJ as ScreeningJournal
    end
    participant SDB as screening-db

    Client->>TC: POST /transfers, TransferRequest senderId=5
    Note over Client,TC: senderId=5 есть в blocked_senders, тестовые данные 002-insert-test-data.sql
    TC->>TTS: createTransfer(request)
    TTS->>TDB: INSERT transfers status=NEW и transfer_history stage=NEW
    TC-)K: publishNew: send(transfers, id, Transfer status=NEW).get(10 s)
    TC-->>Client: 201 Created, TransferEntity status=NEW

    par limit-service: лимит не превышен, сумма резервируется
        K-)LEL: onEvent, топик transfers, status=NEW
        LEL->>LS: onNewTransfer(transfer)
        LS->>LL: decide(transfer)
        LL->>LDB: ReservationRepository.findById(transferId) = empty, SenderUsageRepository.findById(senderId)
        LL->>LL: usage.lazyReset(today), проверки limits.daily-max и limits.monthly-max пройдены
        LL->>LL: usage.reserve(amount): dailyUsed и monthlyUsed увеличены на amount
        LL->>LL: reservation.approve(): decision=APPROVED, state=RESERVED
        LL->>LDB: SenderUsageRepository.save(usage), ReservationRepository.save(reservation)
        Note over LL,LDB: уже выполненное действие: сумма зарезервирована в sender_usage, reservations.state=RESERVED
        LS-)K: send(limit-results, id, withDecision(APPROVED, limit, null))
    and screening-service: отправитель заблокирован
        K-)SEL: onEvent, топик transfers, status=NEW
        SEL->>SS: onNewTransfer(transfer)
        SS->>SJ: check(transfer)
        SJ->>SDB: ScreeningCheckRepository.findById(transferId) = empty
        SJ->>SDB: BlockedSenderRepository.findBySenderId(5)
        SDB-->>SJ: BlockedSender reason=Fraud detected
        SJ->>SDB: ScreeningCheckRepository.save(result=DECLINED, reason=SENDER_BLOCKED)
        SJ-->>SS: ScreeningCheck result=DECLINED
        SS-)K: send(screening-results, id, withDecision(DECLINED, screening, SENDER_BLOCKED))
    end

    K-)TEL: топики limit-results и screening-results, group transfer-tracker
    TEL->>TTS: recordServiceResult(LIMIT), recordServiceResult(SCREENING)
    TTS->>TDB: transfer_history: LIMIT APPROVED и SCREENING DECLINED с messageInfo=SENDER_BLOCKED

    K-)DTC: join limit-results и screening-results по id в окне 10 с
    DTC->>TDS: decide(limitResult, screeningResult)
    Note over TDS: ветка limitApproved и screeningDeclined. Другие ветки: оба DECLINED → DENIED, limit DECLINED → CANCELLED с decidedBy=limit, тогда резерва нет и settle ничего не снимает
    TDS-->>DTC: withDecision(CANCELLED, screening, SENDER_BLOCKED)
    DTC-)K: to(transfers): Transfer status=CANCELLED, decidedBy=screening, messageInfo=SENDER_BLOCKED

    par transfer-service: фиксация отказа
        K-)TEL: onEvent, топик transfers, status=CANCELLED
        TEL->>TTS: applyFinalStatus(transfer)
        TTS->>TDB: existsByTransferIdAndStage(id, FINAL) = false
        TTS->>TDB: UPDATE transfers status=CANCELLED, decided_by=screening, message_info=SENDER_BLOCKED
        TTS->>TDB: TransferHistoryRepository.save(stage=FINAL, status=CANCELLED)
    and limit-service: компенсация уже выполненного резерва
        K-)LEL: onEvent, топик transfers, status=CANCELLED
        LEL->>LS: onFinalStatus(transfer)
        LS->>LL: settle(transfer)
        LL->>LDB: ReservationRepository.findById(transferId)
        LDB-->>LL: Reservation state=RESERVED, isReserved() = true
        alt settle выполнен, ветка CANCELLED, DENIED
            LL->>LDB: SenderUsageRepository.findById(senderId).orElseThrow()
            LL->>LL: usage.release(amount, reservedOn, LocalDate.now())
            Note over LL: lazyReset(today), dailyUsed уменьшается, если reservedOn сегодня, monthlyUsed, если тот же месяц, не ниже нуля
            LL->>LDB: SenderUsageRepository.save(usage)
            LL->>LL: reservation.markReleased(): state=RELEASED
            LL->>LDB: ReservationRepository.save(reservation)
        else исключение в settle, например конфликт @Version в SenderUsage или недоступна limit-db
            Note over LEL,LDB: откат транзакции, DefaultErrorHandler: 3 повтора через 1 с, затем запись в transfers.DLT, резерв остаётся RESERVED, потребитель DLT в коде не найден
        end
    and screening-service
        K-)SEL: onEvent, топик transfers, status=CANCELLED
        Note over SEL: только лог, компенсировать нечего: ScreeningCheck DECLINED остаётся в screening_checks
    end

    Client->>TC: GET /transfers/{id}
    TC->>TDB: findById(id), findByTransferIdOrderByChangedAtAsc(id)
    TC-->>Client: 200 OK, status=CANCELLED, decidedBy=screening, messageInfo=SENDER_BLOCKED, history NEW, LIMIT, SCREENING, FINAL
```

## Диаграмма 4. ER-диаграммы

Сущности хранятся в трёх отдельных базах PostgreSQL (4а–4в), сообщения и DTO, которые не хранятся в БД, вынесены на диаграмму 4г. Сплошная линия — внешний ключ, пунктир — логическая связь по идентификатору без FK; типы полей БД взяты из Liquibase-скриптов 001-create-tables.sql.

### 4а. transferdb (transfer-service)

Перевод и журнал его этапов; единственный внешний ключ в системе — transfer_history.transfer_id → transfers.id.

```mermaid
erDiagram
    transfers ||--|{ transfer_history : "transfer_id FK, не более одной записи на stage"
    transfers {
        BIGINT id PK "TransferEntity.id, задаётся клиентом в TransferRequest.id"
        BIGINT sender_id "NOT NULL, индекс idx_transfers_sender_id"
        BIGINT recipient_id "NOT NULL"
        DECIMAL amount "DECIMAL(15,2) NOT NULL"
        VARCHAR(10) currency "NOT NULL DEFAULT 'RUB'"
        VARCHAR(20) status "NOT NULL, NEW, COMPLETED, CANCELLED, DENIED, индекс idx_transfers_status"
        VARCHAR(20) decided_by "limit, screening или NULL"
        TEXT message_info "причина отказа или NULL"
        TIMESTAMP created_at "NOT NULL"
        TIMESTAMP updated_at "NULL"
    }
    transfer_history {
        BIGSERIAL id PK "TransferHistoryEntry.id"
        BIGINT transfer_id FK "NOT NULL REFERENCES transfers(id), индекс idx_transfer_history_transfer_id"
        VARCHAR(20) status "NOT NULL, NEW, APPROVED, DECLINED, COMPLETED, CANCELLED, DENIED"
        VARCHAR(20) decided_by "limit, screening или NULL"
        TEXT message_info "NULL"
        TIMESTAMP changed_at "NOT NULL DEFAULT NOW()"
        VARCHAR(20) stage "NOT NULL, enum HistoryStage NEW, LIMIT, SCREENING, FINAL, UNIQUE uq_transfer_history_stage вместе с transfer_id"
    }
```

### 4б. limitdb (limit-service)

Накопленное использование лимитов по отправителю и резерв суммы по каждому переводу; связь по sender_id логическая, FK в схеме нет.

```mermaid
erDiagram
    sender_usage |o..|{ reservations : "sender_id, логическая связь без FK"
    sender_usage {
        BIGINT sender_id PK "SenderUsage.senderId"
        DECIMAL daily_used "DECIMAL(15,2) NOT NULL DEFAULT 0"
        DECIMAL monthly_used "DECIMAL(15,2) NOT NULL DEFAULT 0"
        DATE last_daily_reset "NOT NULL DEFAULT CURRENT_DATE"
        DATE last_monthly_reset "NOT NULL DEFAULT CURRENT_DATE"
        BIGINT version "NOT NULL DEFAULT 0, @Version, оптимистичная блокировка"
    }
    reservations {
        BIGINT transfer_id PK "Reservation.transferId, равен id перевода"
        BIGINT sender_id "NOT NULL, индекс idx_reservations_sender_id"
        DECIMAL amount "DECIMAL(15,2) NOT NULL, 0 при INVALID_AMOUNT"
        VARCHAR(10) decision "NOT NULL, APPROVED или DECLINED"
        VARCHAR(50) reason "INVALID_AMOUNT, DAILY_LIMIT_EXCEEDED, MONTHLY_LIMIT_EXCEEDED или NULL"
        VARCHAR(20) state "NOT NULL, RESERVED, NONE, COMMITTED, RELEASED"
        DATE reserved_on "NOT NULL"
        TIMESTAMP created_at "NOT NULL"
        TIMESTAMP updated_at "NOT NULL"
    }
```

### 4в. screeningdb (screening-service)

Список заблокированных отправителей (заполняется только тестовыми данными 002-insert-test-data.sql, кода изменения списка не найдено) и журнал проверок по переводам.

```mermaid
erDiagram
    blocked_senders |o..o{ screening_checks : "sender_id, логическая связь без FK"
    blocked_senders {
        BIGSERIAL id PK "BlockedSender.id"
        BIGINT sender_id UK "NOT NULL UNIQUE, индекс idx_blocked_senders_sender_id"
        VARCHAR(255) reason "NOT NULL"
        TIMESTAMP blocked_at "NOT NULL DEFAULT NOW()"
    }
    screening_checks {
        BIGINT transfer_id PK "ScreeningCheck.transferId, равен id перевода"
        BIGINT sender_id "NOT NULL, индекс idx_screening_checks_sender_id"
        VARCHAR(10) result "NOT NULL, APPROVED или DECLINED"
        VARCHAR(50) reason "SENDER_BLOCKED или NULL"
        TIMESTAMP checked_at "NOT NULL"
    }
```

### 4г. Объекты, которые не хранятся в БД (сообщения Kafka и DTO REST API)

Transfer — значение Kafka-сообщения во всех топиках, TransferRequest, TransferWithHistory и ProblemDetail — тело запроса и ответов REST API transfer-service; типы — Java-типы из кода.

```mermaid
erDiagram
    TransferRequest ||..o{ Transfer : "id равен Transfer.id, сообщение NEW и результаты по этому id"
    TransferRequest {
        Long id "@NotNull, идентификатор перевода задаёт клиент"
        Long senderId "@NotNull"
        Long recipientId "@NotNull"
        BigDecimal amount "@NotNull @Positive @Digits integer=13 fraction=2"
        String currency "@Pattern [A-Z]{3}, если null, то RUB"
    }
    Transfer {
        Long id "он же ключ Kafka-записи, LongSerializer"
        Long senderId
        Long recipientId
        BigDecimal amount
        String currency
        String status "NEW, APPROVED, DECLINED, COMPLETED, CANCELLED, DENIED"
        String decidedBy "limit, screening или null"
        String messageInfo "SENDER_BLOCKED, DAILY_LIMIT_EXCEEDED, MONTHLY_LIMIT_EXCEEDED, INVALID_AMOUNT, UNEXPECTED_RESULT или null"
        LocalDateTime createdAt
    }
    TransferWithHistory {
        TransferEntity transfer "ключ transfer в Map ответа GET /transfers/{id}"
        TransferHistoryEntry[] history "ключ history, сортировка по stage, затем changedAt"
    }
    ProblemDetail {
        String type
        String title
        int status "400, 409, 500, 503"
        String detail "тексты KAFKA_UNAVAILABLE, INTERNAL_ERROR или о конфликте id"
        String instance
    }
```

### Логические связи между сущностями разных диаграмм (все без FK)

- `transfers.id` (4а) = `reservations.transfer_id` (4б) = `screening_checks.transfer_id` (4в) = `Transfer.id` и `TransferRequest.id` (4г) — единый идентификатор перевода, его задаёт клиент, он же ключ Kafka-записи; `transfers` 1 : 0..1 `reservations`, 1 : 0..1 `screening_checks`, 1 : 0..N сообщений `Transfer`.
- `transfers.sender_id` (4а) = `sender_usage.sender_id` и `reservations.sender_id` (4б) = `blocked_senders.sender_id` и `screening_checks.sender_id` (4в) = `Transfer.senderId` и `TransferRequest.senderId` (4г); справочник отправителей в коде не найден.
- `transfers.recipient_id` (4а) = `Transfer.recipientId` и `TransferRequest.recipientId` (4г); получатель нигде не проверяется, сущность получателя в коде не найдена.
- `TransferRequest` (4г) → `transfers` и `transfer_history` со stage NEW (4а) — `TransferTrackingService.createTransfer`.
- `Transfer` из топиков `limit-results` и `screening-results` (4г) → `transfer_history` со stage LIMIT и SCREENING (4а) — `TransferTrackingService.recordServiceResult`; итоговый `Transfer` из топика `transfers` → `transfers.status`, `decided_by`, `message_info` и `transfer_history` со stage FINAL — `TransferTrackingService.applyFinalStatus`.
- `reservations.decision` и `reason` (4б) → `Transfer.status` и `messageInfo` в топике `limit-results`, decidedBy=limit — `LimitService.onNewTransfer`; `screening_checks.result` и `reason` (4в) → `Transfer.status` и `messageInfo` в топике `screening-results`, decidedBy=screening — `ScreeningService.onNewTransfer`.
- `TransferWithHistory.transfer` (4г) → `transfers` (4а), `TransferWithHistory.history` → `transfer_history` (4а) — `TransferController.getById`; ответы POST /transfers и GET /transfers — сериализованный `TransferEntity` (в transfer-api.yaml это схема `Transfer`, не путать с record `com.example.common.Transfer`).
- Топики `transfers.DLT`, `limit-results.DLT`, `screening-results.DLT` получают исходную запись (`Transfer` в JSON или сырые byte[]), потребители этих топиков в коде не найдены.

## Диаграмма 5. Диаграмма состояний перевода (TransferEntity, transfers.status)

Жизненный цикл главной бизнес-сущности — перевода. Статус выставляют TransferTrackingService.createTransfer и applyFinalStatus по решению, которое TransferDecisionService.decide принимает внутри join в DecisionTopologyConfig.

```mermaid
stateDiagram-v2
    state decide_choice <<choice>>

    NEW : перевод сохранён в transfers и опубликован в топик transfers
    COMPLETED : оба сервиса одобрили, резерв лимита фиксируется COMMITTED
    CANCELLED : отказ одного из сервисов, резерв лимита, если был, снимается RELEASED
    DENIED : отказ обоих сервисов, резерва нет, причины объединены в messageInfo

    [*] --> NEW : POST /transfers с валидным TransferRequest и новым id, createTransfer
    NEW --> NEW : повторный POST с тем же id и параметрами, например после 503, publishNew повторно
    NEW --> NEW : recordServiceResult для LIMIT или SCREENING, меняются только история и updatedAt
    NEW --> decide_choice : join limit-results и screening-results по id в окне 10 с, TransferDecisionService.decide
    decide_choice --> COMPLETED : limit APPROVED и screening APPROVED
    decide_choice --> DENIED : limit DECLINED и screening DECLINED
    decide_choice --> CANCELLED : limit DECLINED и screening APPROVED, decidedBy limit
    decide_choice --> CANCELLED : limit APPROVED и screening DECLINED, decidedBy screening
    decide_choice --> CANCELLED : иная комбинация статусов, messageInfo UNEXPECTED_RESULT
    COMPLETED --> [*]
    CANCELLED --> [*]
    DENIED --> [*]

    note right of NEW
        Если результаты limit и screening разошлись больше чем на 10 с,
        join не срабатывает и перевод остаётся в NEW,
        пока клиент не повторит POST с тем же id.
        Автоматическая обработка таймаута в коде не найдена.
        POST с тем же id и другими параметрами получает 409, статус не меняется.
    end note

    note left of CANCELLED
        Финальные статусы не меняются.
        applyFinalStatus пропускает событие, если в transfer_history уже есть FINAL.
    end note
```

### Другие сущности с жизненным циклом

- `Reservation` (limit-service, таблица reservations, поле state): после `LimitLedger.decide` — RESERVED (`approve`, лимит не превышен) или NONE (`decline`: INVALID_AMOUNT, DAILY_LIMIT_EXCEEDED, MONTHLY_LIMIT_EXCEEDED); RESERVED → COMMITTED (`settle` при COMPLETED, `markCommitted`); RESERVED → RELEASED (`settle` при CANCELLED или DENIED, `SenderUsage.release` и `markReleased`); NONE, COMMITTED и RELEASED — конечные, `settle` их пропускает.
- Сообщение `Transfer` (transfer-common, поле status): NEW в топике transfers → APPROVED или DECLINED в limit-results и screening-results → COMPLETED, CANCELLED или DENIED в transfers.
- `TransferHistoryEntry` (transfer_history, поле stage): журнал этапов NEW → LIMIT и SCREENING → FINAL, по одной записи на этап; это лог, а не статус.
- `ScreeningCheck` (screening_checks, поле result): APPROVED или DECLINED задаётся один раз в `ScreeningJournal.check`, переходов нет.
- `SenderUsage` (sender_usage): статусов нет, только счётчики daily_used и monthly_used с ленивым сбросом в `lazyReset`.
