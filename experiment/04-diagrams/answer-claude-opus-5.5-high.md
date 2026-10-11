## Диаграмма 1. Component diagram (C4)

Диаграмма показывает три Spring Boot сервиса, их базы PostgreSQL, брокер Kafka с рабочими и DLT-топиками и связи между ними. Взаимодействие сервисов асинхронное, через топики `transfers`, `limit-results` и `screening-results`; синхронный вход один: REST в `transfer-service`.

```mermaid
flowchart TB
    Client["Клиент REST API (внешний, в коде не описан)"]

    subgraph TS["transfer-service (Spring Boot, порт 8080)"]
        TC["TransferController"]
        TTS["TransferTrackingService"]
        TEL["TransferEventListener (id transfer-tracker)"]
        DTC["DecisionTopologyConfig.decisionStream (Kafka Streams)"]
        TDS["TransferDecisionService"]
    end

    subgraph TDB["transfer-db (PostgreSQL 16, transferdb)"]
        T1[("transfers")]
        T2[("transfer_history")]
    end

    subgraph LS["limit-service (Spring Boot)"]
        LTL["TransferEventsListener (id limit-transfers-listener)"]
        LSV["LimitService"]
        LLG["LimitLedger"]
    end

    subgraph LDB["limit-db (PostgreSQL 16, limitdb)"]
        L1[("sender_usage")]
        L2[("reservations")]
    end

    subgraph SS["screening-service (Spring Boot)"]
        STL["TransferEventsListener (id screening-transfers-listener)"]
        SSV["ScreeningService"]
        SJ["ScreeningJournal"]
    end

    subgraph SDB["screening-db (PostgreSQL 16, screeningdb)"]
        S1[("blocked_senders")]
        S2[("screening_checks")]
    end

    subgraph KAFKA["broker (Kafka cp-kafka 7.6.0, KRaft)"]
        KT[["transfers"]]
        KL[["limit-results"]]
        KS[["screening-results"]]
        DT[["transfers.DLT"]]
        DL[["limit-results.DLT"]]
        DSL[["screening-results.DLT"]]
        KI[["внутренние топики Kafka Streams для join (имена генерируются)"]]
    end

    Client -->|"HTTP/JSON: POST /transfers"| TC
    Client -->|"HTTP/JSON: GET /transfers, GET /transfers/{id}"| TC
    TC -->|"вызов: createTransfer"| TTS
    TC -->|"JPA/JDBC: findById, findAllByOrderByCreatedAtDesc"| T1
    TC -->|"JPA/JDBC: findByTransferIdOrderByChangedAtAsc"| T2
    TTS -->|"JPA/JDBC: INSERT/UPDATE"| T1
    TTS -->|"JPA/JDBC: INSERT, existsByTransferIdAndStage"| T2
    TC -->|"Kafka produce: transfers, status NEW, key = id"| KT

    KT -->|"Kafka consume: group limit"| LTL
    LTL -->|"onNewTransfer / onFinalStatus"| LSV
    LSV -->|"decide / settle"| LLG
    LLG -->|"JPA/JDBC"| L1
    LLG -->|"JPA/JDBC"| L2
    LSV -->|"Kafka produce: limit-results, APPROVED/DECLINED"| KL

    KT -->|"Kafka consume: group screening"| STL
    STL -->|"onNewTransfer"| SSV
    SSV -->|"check"| SJ
    SJ -->|"JPA/JDBC: findBySenderId"| S1
    SJ -->|"JPA/JDBC: findById, save"| S2
    SSV -->|"Kafka produce: screening-results, APPROVED/DECLINED"| KS

    KL -->|"Kafka Streams consume"| DTC
    KS -->|"Kafka Streams consume"| DTC
    DTC -->|"join в окне 10 с, вызов decide"| TDS
    DTC -->|"Kafka Streams produce: transfers, COMPLETED/CANCELLED/DENIED"| KT
    DTC -->|"state store changelog"| KI

    KT -->|"Kafka consume: group transfer-tracker"| TEL
    KL -->|"Kafka consume: group transfer-tracker"| TEL
    KS -->|"Kafka consume: group transfer-tracker"| TEL
    TEL -->|"recordServiceResult / applyFinalStatus"| TTS

    LTL -.->|"DefaultErrorHandler: 3 повтора по 1 с, затем DLT"| DT
    STL -.->|"DefaultErrorHandler: 3 повтора по 1 с, затем DLT"| DT
    TEL -.->|"DLT по исходному топику"| DT
    TEL -.->|"DLT по исходному топику"| DL
    TEL -.->|"DLT по исходному топику"| DSL
```

## Диаграмма 2. Sequence diagram: успешный сценарий (happy path)

Перевод проходит проверку лимитов и скрининг. После join результатов он получает статус `COMPLETED`, а резерв лимита фиксируется (`COMMITTED`).

```mermaid
sequenceDiagram
    autonumber
    actor Client as Клиент
    participant TC as TransferController
    participant TTS as TransferTrackingService
    participant TDB as transfer-db
    participant K as Kafka broker
    participant LTL as limit-service TransferEventsListener
    participant LS as LimitService
    participant LG as LimitLedger
    participant LDB as limit-db
    participant STL as screening-service TransferEventsListener
    participant SS as ScreeningService
    participant SJ as ScreeningJournal
    participant SDB as screening-db
    participant TEL as TransferEventListener
    participant DS as DecisionTopologyConfig.decisionStream
    participant TDS as TransferDecisionService

    Client->>TC: POST /transfers (TransferRequest, валидация Valid)
    TC->>TDB: transferRepository.findById(id)
    TDB-->>TC: пусто
    TC->>TTS: createTransfer(request)
    TTS->>TDB: transferRepository.saveAndFlush(TransferEntity status NEW)
    TTS->>TDB: historyRepository.save(stage NEW, status NEW)
    TTS-->>TC: TransferEntity
    TC->>K: publishNew, template.send(transfers, id, Transfer.newTransfer(...)).get(10 с)
    K-->>TC: ack
    TC-->>Client: 201 Created (TransferEntity, status NEW)
    K->>TEL: transfers, Transfer NEW
    Note over TEL: status NEW игнорируется

    par limit-service
        K->>LTL: onEvent(Transfer NEW), group limit
        LTL->>LS: onNewTransfer(transfer)
        LS->>LG: decide(transfer), транзакция
        LG->>LDB: reservationRepository.findById(id), пусто
        LG->>LDB: usageRepository.findById(senderId) или new SenderUsage
        LG->>LG: lazyReset(today), проверка daily-max 50000.00 и monthly-max 200000.00
        LG->>LG: usage.reserve(amount), reservation.approve(), state RESERVED
        LG->>LDB: usageRepository.save, reservationRepository.save
        LG-->>LS: Reservation (APPROVED)
        LS->>K: sendAndWait, send(limit-results, id, withDecision(APPROVED, limit, null))
    and screening-service
        K->>STL: onEvent(Transfer NEW), group screening
        STL->>SS: onNewTransfer(transfer)
        SS->>SJ: check(transfer), транзакция
        SJ->>SDB: checkRepository.findById(id), пусто
        SJ->>SDB: blockedSenderRepository.findBySenderId(senderId), пусто
        SJ->>SDB: checkRepository.save(ScreeningCheck APPROVED)
        SJ-->>SS: ScreeningCheck (APPROVED)
        SS->>K: sendAndWait, send(screening-results, id, withDecision(APPROVED, screening, null))
    end

    par трекинг результатов
        K->>TEL: limit-results (APPROVED)
        TEL->>TTS: recordServiceResult(transfer, LIMIT)
        TTS->>TDB: existsByTransferIdAndStage, INSERT transfer_history (LIMIT), UPDATE updated_at
        K->>TEL: screening-results (APPROVED)
        TEL->>TTS: recordServiceResult(transfer, SCREENING)
        TTS->>TDB: INSERT transfer_history (SCREENING), UPDATE updated_at
    and решение Kafka Streams
        K->>DS: limit-results и screening-results с одним key id
        DS->>TDS: decide(limitResult, screeningResult) в окне 10 с
        TDS-->>DS: withDecision(COMPLETED, null, null)
        DS->>K: to(transfers), Transfer COMPLETED
    end

    par фиксация статуса
        K->>TEL: transfers (COMPLETED)
        TEL->>TTS: applyFinalStatus(transfer)
        TTS->>TDB: UPDATE transfers status COMPLETED, INSERT transfer_history (FINAL)
    and фиксация резерва
        K->>LTL: onEvent(Transfer COMPLETED)
        LTL->>LS: onFinalStatus(transfer)
        LS->>LG: settle(transfer)
        LG->>LDB: findById, isReserved, reservation.markCommitted(), save (state COMMITTED)
    and screening-service
        K->>STL: onEvent(Transfer COMPLETED)
        Note over STL: только лог, действий нет
    end

    Client->>TC: GET /transfers/{id}
    TC->>TDB: findById(id), findByTransferIdOrderByChangedAtAsc(id)
    TC-->>Client: 200 (transfer COMPLETED, history NEW, LIMIT, SCREENING, FINAL)
```

## Диаграмма 3. Sequence diagram: основной сценарий отказа

Отправитель из `blocked_senders` (например, `senderId` 13 из `002-insert-test-data.sql`) проходит проверку лимитов, и сумма уже зарезервирована. Скрининг отклоняет перевод, перевод получает статус `CANCELLED`, а резерв компенсируется в `LimitLedger.settle` (состояние `RELEASED`).

```mermaid
sequenceDiagram
    autonumber
    actor Client as Клиент
    participant TC as TransferController
    participant TTS as TransferTrackingService
    participant TDB as transfer-db
    participant K as Kafka broker
    participant LTL as limit-service TransferEventsListener
    participant LS as LimitService
    participant LG as LimitLedger
    participant LDB as limit-db
    participant STL as screening-service TransferEventsListener
    participant SS as ScreeningService
    participant SJ as ScreeningJournal
    participant SDB as screening-db
    participant DS as DecisionTopologyConfig.decisionStream
    participant TDS as TransferDecisionService
    participant TEL as TransferEventListener

    Client->>TC: POST /transfers (senderId из blocked_senders)
    TC->>TTS: createTransfer(request)
    TTS->>TDB: INSERT transfers (NEW), INSERT transfer_history (stage NEW)
    TC->>K: publishNew, send(transfers, id, Transfer NEW)
    TC-->>Client: 201 Created (status NEW)

    par limit-service
        K->>LTL: onEvent(Transfer NEW)
        LTL->>LS: onNewTransfer(transfer)
        LS->>LG: decide(transfer)
        LG->>LDB: reservationRepository.findById, пусто, usageRepository.findById(senderId)
        LG->>LG: lazyReset, лимиты не превышены, usage.reserve(amount), reservation.approve()
        LG->>LDB: save sender_usage, save reservations (state RESERVED)
        Note over LDB: Уже выполнено, сумма добавлена в daily_used и monthly_used
        LS->>K: send(limit-results, withDecision(APPROVED, limit, null))
    and screening-service
        K->>STL: onEvent(Transfer NEW)
        STL->>SS: onNewTransfer(transfer)
        SS->>SJ: check(transfer)
        SJ->>SDB: blockedSenderRepository.findBySenderId(senderId), найден
        SJ->>SDB: checkRepository.save(ScreeningCheck DECLINED, SENDER_BLOCKED)
        SS->>K: send(screening-results, withDecision(DECLINED, screening, SENDER_BLOCKED))
    end

    par трекинг результатов
        K->>TEL: limit-results (APPROVED), screening-results (DECLINED)
        TEL->>TTS: recordServiceResult(LIMIT), recordServiceResult(SCREENING)
        TTS->>TDB: INSERT transfer_history (LIMIT APPROVED), (SCREENING DECLINED)
    and решение Kafka Streams
        K->>DS: join limit-results и screening-results по key id в окне 10 с
        DS->>TDS: decide(limitResult, screeningResult)
        TDS-->>DS: limitApproved и screeningDeclined, withDecision(CANCELLED, screening, SENDER_BLOCKED)
        DS->>K: to(transfers), Transfer CANCELLED
    end

    par компенсация резерва в limit-service
        K->>LTL: onEvent(Transfer CANCELLED)
        LTL->>LS: onFinalStatus(transfer)
        LS->>LG: settle(transfer)
        LG->>LDB: reservationRepository.findById, isReserved = true
        LG->>LDB: usageRepository.findById(senderId), SenderUsage.release(amount, reservedOn, today), save
        LG->>LDB: reservation.markReleased(), save (state RELEASED)
    and фиксация статуса
        K->>TEL: transfers (CANCELLED)
        TEL->>TTS: applyFinalStatus(transfer)
        TTS->>TDB: UPDATE transfers status CANCELLED, decided_by screening, message_info SENDER_BLOCKED
        TTS->>TDB: INSERT transfer_history (FINAL CANCELLED)
    and screening-service
        K->>STL: onEvent(Transfer CANCELLED)
        Note over STL: только лог, screening не держит резервов
    end

    Client->>TC: GET /transfers/{id}
    TC-->>Client: 200 (transfer CANCELLED, history NEW, LIMIT, SCREENING, FINAL)

    Note over LG,LDB: release уменьшает daily_used только если reservedOn = today, monthly_used только в том же месяце, не ниже 0
    Note over DS,K: Если результат не пришёл в окно 10 с или ушёл в DLT после 3 повторов, join не срабатывает. Перевод остаётся NEW, резерв остаётся RESERVED. Обработка таймаута и компенсация в коде не найдены
```

## Диаграмма 4а. ER: transferdb (transfer-service)

Сущности `TransferEntity` и `TransferHistoryEntry`. Связь по внешнему ключу `transfer_history.transfer_id REFERENCES transfers(id)`, ограничение `uq_transfer_history_stage` допускает не больше одной записи на этап.

```mermaid
erDiagram
    transfers {
        bigint id PK "задаётся клиентом, TransferRequest.id"
        bigint sender_id "NOT NULL, idx_transfers_sender_id"
        bigint recipient_id "NOT NULL"
        decimal amount "DECIMAL(15,2) NOT NULL"
        varchar currency "VARCHAR(10) NOT NULL DEFAULT RUB"
        varchar status "VARCHAR(20) NOT NULL, NEW/COMPLETED/CANCELLED/DENIED, idx_transfers_status"
        varchar decided_by "VARCHAR(20), limit/screening/null"
        text message_info "причина отказа"
        timestamp created_at "NOT NULL"
        timestamp updated_at "nullable"
    }
    transfer_history {
        bigserial id PK
        bigint transfer_id FK "NOT NULL, UNIQUE(transfer_id, stage)"
        varchar stage "VARCHAR(20), HistoryStage NEW/LIMIT/SCREENING/FINAL"
        varchar status "VARCHAR(20) NOT NULL"
        varchar decided_by "VARCHAR(20)"
        text message_info
        timestamp changed_at "NOT NULL DEFAULT NOW()"
    }
    transfers ||--|{ transfer_history : "transfer_id, от 1 до 4 записей"
```

## Диаграмма 4б. ER: limitdb (limit-service)

Сущности `SenderUsage` и `Reservation`. Внешних ключей нет, связь по `sender_id` логическая.

```mermaid
erDiagram
    sender_usage {
        bigint sender_id PK
        decimal daily_used "DECIMAL(15,2) NOT NULL DEFAULT 0"
        decimal monthly_used "DECIMAL(15,2) NOT NULL DEFAULT 0"
        date last_daily_reset "NOT NULL"
        date last_monthly_reset "NOT NULL"
        bigint version "JPA Version, оптимистичная блокировка"
    }
    reservations {
        bigint transfer_id PK "равен transfers.id"
        bigint sender_id "NOT NULL, idx_reservations_sender_id, без FK"
        decimal amount "DECIMAL(15,2) NOT NULL"
        varchar decision "VARCHAR(10), APPROVED/DECLINED"
        varchar reason "VARCHAR(50), INVALID_AMOUNT/DAILY_LIMIT_EXCEEDED/MONTHLY_LIMIT_EXCEEDED"
        varchar state "VARCHAR(20), RESERVED/COMMITTED/RELEASED/NONE"
        date reserved_on "NOT NULL"
        timestamp created_at "NOT NULL"
        timestamp updated_at "NOT NULL"
    }
    sender_usage |o..o{ reservations : "sender_id, логическая"
```

## Диаграмма 4в. ER: screeningdb (screening-service)

Сущности `BlockedSender` и `ScreeningCheck`. Внешних ключей нет, связь по `sender_id` логическая.

```mermaid
erDiagram
    blocked_senders {
        bigserial id PK
        bigint sender_id UK "NOT NULL UNIQUE, idx_blocked_senders_sender_id"
        varchar reason "VARCHAR(255) NOT NULL"
        timestamp blocked_at "NOT NULL DEFAULT NOW()"
    }
    screening_checks {
        bigint transfer_id PK "равен transfers.id"
        bigint sender_id "NOT NULL, idx_screening_checks_sender_id"
        varchar result "VARCHAR(10), APPROVED/DECLINED"
        varchar reason "VARCHAR(50), SENDER_BLOCKED или null"
        timestamp checked_at "NOT NULL"
    }
    blocked_senders |o..o{ screening_checks : "sender_id, логическая"
```

## Диаграмма 4г. Объекты, не хранящиеся в БД

Это REST-модели и сообщение Kafka `Transfer` из `transfer-common`. `Transfer` передаётся во всех топиках с ключом `Long id`, а ответ `GET /transfers/{id}` собирается как `Map` в `TransferController.getById` (в OpenAPI это `TransferWithHistory`).

```mermaid
erDiagram
    TransferRequest {
        Long id "NotNull, становится transfers.id"
        Long senderId "NotNull"
        Long recipientId "NotNull"
        BigDecimal amount "NotNull, Positive, Digits 13 и 2"
        String currency "три заглавные латинские буквы, по умолчанию RUB"
    }
    Transfer {
        Long id "ключ сообщения Kafka"
        Long senderId
        Long recipientId
        BigDecimal amount
        String currency
        String status "NEW, APPROVED, DECLINED, COMPLETED, CANCELLED, DENIED"
        String decidedBy "limit, screening или null"
        String messageInfo "причина отказа"
        LocalDateTime createdAt
    }
    TransferWithHistory {
        TransferEntity transfer "Map в TransferController.getById"
        List history "TransferHistoryEntry, сортировка по stage и changedAt"
    }
    ProblemDetail {
        URI type
        String title
        int status "400, 409, 500, 503"
        String detail
        URI instance
    }
    TransferRequest ||..o{ Transfer : "publishNew, тот же id"
    Transfer ||..o{ Transfer : "withDecision и decide, тот же id"
```

Логические связи между сущностями разных диаграмм (все без FK, через идентификатор):

- `TransferRequest.id` → `transfers.id`: создаётся в `TransferTrackingService.createTransfer`.
- `Transfer.id` ↔ `transfers.id`: сообщения во всех топиках относятся к переводу по id.
- `reservations.transfer_id` → `transfers.id` (limitdb → transferdb).
- `screening_checks.transfer_id` → `transfers.id` (screeningdb → transferdb).
- `sender_usage.sender_id`, `reservations.sender_id`, `blocked_senders.sender_id` и `screening_checks.sender_id` → `transfers.sender_id`.
- `reservations.decision` и `reason` → `Transfer.status` и `messageInfo` в топике `limit-results` (`LimitService.onNewTransfer`).
- `screening_checks.result` и `reason` → `Transfer.status` и `messageInfo` в топике `screening-results` (`ScreeningService.onNewTransfer`).
- `Transfer` из `limit-results`, `screening-results` и `transfers` (финальный статус) → строки `transfer_history` с `stage` LIMIT, SCREENING и FINAL, а также поля `transfers.status`, `decided_by`, `message_info`.
- `TransferWithHistory.transfer` → `transfers` (TransferEntity), `TransferWithHistory.history` → `transfer_history` (TransferHistoryEntry).
- Справочника отправителей и получателей (`senderId`, `recipientId`) в коде не найдено.

## Диаграмма 5. Диаграмма состояний: перевод (TransferEntity.status)

Главная бизнес-сущность — перевод в таблице `transfers`. Финальный статус вычисляет `TransferDecisionService.decide`, а применяет `TransferTrackingService.applyFinalStatus`; повторное применение отсекается проверкой этапа FINAL.

```mermaid
stateDiagram-v2
    [*] --> NEW : POST /transfers, TransferTrackingService.createTransfer
    NEW --> NEW : повторный POST с тем же id и параметрами, повторная publishNew
    NEW --> COMPLETED : limit APPROVED и screening APPROVED
    NEW --> DENIED : limit DECLINED и screening DECLINED
    NEW --> CANCELLED : limit DECLINED и screening APPROVED, decidedBy limit
    NEW --> CANCELLED : limit APPROVED и screening DECLINED, decidedBy screening
    NEW --> CANCELLED : иная комбинация, messageInfo UNEXPECTED_RESULT
    COMPLETED --> [*]
    CANCELLED --> [*]
    DENIED --> [*]

    note right of NEW
        Переход выполняет DecisionTopologyConfig.decisionStream
        через join limit-results и screening-results в окне 10 секунд.
        Если join не сработал, перевод остаётся NEW.
        Таймаут и перевод в отказ в коде не найдены.
    end note
```

Другие сущности с жизненным циклом:

- **Reservation.state** (limitdb). При одобрении в `decide` резерв получает `RESERVED`, при отказе `NONE`. Из `RESERVED` он переходит в `COMMITTED` в `settle` при `COMPLETED` или в `RELEASED` в `settle` при `CANCELLED` и `DENIED`. Отдельно хранится поле `Reservation.decision`: APPROVED или DECLINED.
- **transfer_history.stage** (transferdb). Этапы NEW, затем LIMIT и SCREENING в любом порядке, затем FINAL; на каждый этап одна запись.
- **ScreeningCheck.result** (screeningdb). Значение задаётся один раз (APPROVED или DECLINED), переходов в коде не найдено.