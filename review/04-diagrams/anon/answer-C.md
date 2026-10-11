### Диаграмма 1. Component diagram (C4)

Контейнеры из docker-compose.yml и компоненты внутри сервисов. Сервисы не вызывают друг друга напрямую: они обмениваются сообщением Transfer из transfer-common через топики Kafka, а единственная синхронная точка входа — REST API TransferController. Других внешних систем (аутентификации, платёжного ядра, уведомлений клиента) в коде не найдено.

```mermaid
flowchart TB
    CLIENT(["Внешний REST-клиент<br/>в коде не определён"])

    subgraph TS["Контейнер transfer-service: Spring Boot 3.4.1, порт 8080"]
        TC["TransferController<br/>REST /transfers"]
        AEH["ApiExceptionHandler<br/>ProblemDetail"]
        TTS["TransferTrackingService"]
        TEL["TransferEventListener<br/>@KafkaListener id=transfer-tracker"]
        KS["DecisionTopologyConfig.decisionStream<br/>Kafka Streams join, окно 10 с<br/>LogAndContinueExceptionHandler"]
        TDS["TransferDecisionService"]
        TEH["KafkaErrorHandlingConfig<br/>DefaultErrorHandler, 3 повтора через 1 с"]
        KSS[("Состояние Kafka Streams<br/>/tmp/transfer-service-streams")]
    end

    subgraph LSVC["Контейнер limit-service: Spring Boot 3.4.1"]
        LEL["TransferEventsListener<br/>@KafkaListener id=limit-transfers-listener"]
        LS["LimitService"]
        LL["LimitLedger<br/>limits.daily-max=50000.00<br/>limits.monthly-max=200000.00"]
        LEH["KafkaErrorHandlingConfig<br/>DefaultErrorHandler, 3 повтора через 1 с"]
    end

    subgraph SSVC["Контейнер screening-service: Spring Boot 3.4.1"]
        SEL["TransferEventsListener<br/>@KafkaListener id=screening-transfers-listener"]
        SS["ScreeningService"]
        SJ["ScreeningJournal"]
        SEH["KafkaErrorHandlingConfig<br/>DefaultErrorHandler, 3 повтора через 1 с"]
    end

    subgraph BROKER["Контейнер broker: cp-kafka 7.6.0, KRaft, broker:29092, auto-create выключен"]
        T_TR[/"transfers<br/>key Long, value Transfer JSON<br/>3 партиции, compact"/]
        T_LR[/"limit-results<br/>key Long, value Transfer JSON<br/>3 партиции, compact"/]
        T_SR[/"screening-results<br/>key Long, value Transfer JSON<br/>3 партиции, compact"/]
        D_TR[/"transfers.DLT<br/>потребитель в коде не найден"/]
        D_LR[/"limit-results.DLT<br/>потребитель в коде не найден"/]
        D_SR[/"screening-results.DLT<br/>потребитель в коде не найден"/]
    end

    subgraph TDBC["Контейнер transfer-db: postgres:16-alpine, порт 5434:5432"]
        TDB[("transferdb<br/>transfers, transfer_history")]
    end

    subgraph LDBC["Контейнер limit-db: postgres:16-alpine, порт 5432:5432"]
        LDB[("limitdb<br/>sender_usage, reservations")]
    end

    subgraph SDBC["Контейнер screening-db: postgres:16-alpine, порт 5433:5432"]
        SDB[("screeningdb<br/>blocked_senders, screening_checks")]
    end

    CLIENT -->|"HTTP/JSON :8080, POST /transfers, GET /transfers, GET /transfers/{id}"| TC
    TC -->|"in-process: createTransfer()"| TTS
    TC -.->|"in-process: исключения, ответы 400, 409, 500, 503"| AEH
    TC -->|"JDBC: чтение через TransferRepository, TransferHistoryRepository"| TDB
    TTS -->|"JDBC: jdbc:postgresql://transfer-db:5432/transferdb, запись"| TDB
    TC -->|"Kafka produce: transfers, key=id, Transfer status=NEW"| T_TR

    T_TR -->|"Kafka consume: transfers, group=limit"| LEL
    T_TR -->|"Kafka consume: transfers, group=screening"| SEL
    T_TR -->|"Kafka consume: transfers, group=transfer-tracker, только не NEW"| TEL
    T_LR -->|"Kafka consume: limit-results, group=transfer-tracker"| TEL
    T_SR -->|"Kafka consume: screening-results, group=transfer-tracker"| TEL
    TEL -->|"in-process: recordServiceResult(), applyFinalStatus()"| TTS

    LEL -->|"in-process: onNewTransfer() для NEW, onFinalStatus() для остальных"| LS
    LS -->|"in-process: decide(), settle()"| LL
    LL -->|"JDBC: jdbc:postgresql://limit-db:5432/limitdb"| LDB
    LS -->|"Kafka produce: limit-results, key=id, APPROVED или DECLINED"| T_LR

    SEL -->|"in-process: onNewTransfer() только для NEW"| SS
    SS -->|"in-process: check()"| SJ
    SJ -->|"JDBC: jdbc:postgresql://screening-db:5432/screeningdb"| SDB
    SS -->|"Kafka produce: screening-results, key=id, APPROVED или DECLINED"| T_SR

    T_LR -->|"Kafka Streams consume: limit-results"| KS
    T_SR -->|"Kafka Streams consume: screening-results"| KS
    KS -->|"in-process: decide(limitResult, screeningResult)"| TDS
    KS -->|"локальный диск: оконные хранилища join"| KSS
    KS -->|"Kafka Streams produce: transfers, COMPLETED, CANCELLED или DENIED"| T_TR

    TEL -.->|"in-process: исключение обработчика"| TEH
    LEL -.->|"in-process: исключение обработчика"| LEH
    SEL -.->|"in-process: исключение обработчика"| SEH
    TEH -.->|"Kafka produce: transfers.DLT"| D_TR
    TEH -.->|"Kafka produce: limit-results.DLT"| D_LR
    TEH -.->|"Kafka produce: screening-results.DLT"| D_SR
    LEH -.->|"Kafka produce: transfers.DLT"| D_TR
    SEH -.->|"Kafka produce: transfers.DLT"| D_TR

    classDef ext fill:#eeeeee,stroke:#666666,color:#000000
    classDef store fill:#dae8fc,stroke:#6c8ebf,color:#000000
    classDef topic fill:#fff2cc,stroke:#d6b656,color:#000000
    class CLIENT ext
    class TDB,LDB,SDB,KSS store
    class T_TR,T_LR,T_SR,D_TR,D_LR,D_SR topic
```

### Диаграмма 2. Sequence diagram — основной успешный сценарий

Перевод в пределах лимитов от отправителя, которого нет в blocked_senders. limit-service резервирует сумму, screening-service одобряет перевод, join в DecisionTopologyConfig.decisionStream даёт статус COMPLETED, после чего LimitLedger.settle фиксирует резерв. Клиент получает 201 со статусом NEW сразу после публикации; финальный статус виден только через GET /transfers/{id}.

```mermaid
sequenceDiagram
    autonumber
    actor C as Клиент
    box transfer-service и transfer-db
        participant TC as TransferController
        participant TTS as TransferTrackingService
        participant TEL as TransferEventListener
        participant KS as DecisionTopologyConfig.decisionStream
        participant TDS as TransferDecisionService
        participant TDB as transferdb
    end
    participant K as Kafka broker
    box limit-service и limit-db
        participant LEL as TransferEventsListener
        participant LS as LimitService
        participant LL as LimitLedger
        participant LDB as limitdb
    end
    box screening-service и screening-db
        participant SEL as TransferEventsListener
        participant SS as ScreeningService
        participant SJ as ScreeningJournal
        participant SDB as screeningdb
    end

    C->>TC: POST /transfers, TransferRequest, create()
    Note over TC: @Valid TransferRequest, при ошибке был бы ответ 400
    TC->>TDB: transferRepository.findById(id) → пусто
    TC->>TTS: createTransfer(request)
    TTS->>TDB: saveAndFlush TransferEntity status=NEW
    TTS->>TDB: save TransferHistoryEntry stage=NEW, commit
    TTS-->>TC: TransferEntity
    TC->>K: publishNew() → send(transfers, key=id, Transfer.newTransfer).get(10 с)
    K-->>TC: SendResult
    TC-->>C: 201 Created, TransferEntity status=NEW

    par group limit
        K->>LEL: transfers → onEvent(Transfer NEW)
        LEL->>LS: onNewTransfer(transfer)
        LS->>LL: decide(transfer)
        LL->>LDB: reservationRepository.findById(id) → пусто
        LL->>LDB: usageRepository.findById(senderId) → SenderUsage или new SenderUsage
        Note over LL: lazyReset(today), dailyUsed + amount ≤ limits.daily-max, monthlyUsed + amount ≤ limits.monthly-max
        LL->>LDB: reserve(amount), save SenderUsage и Reservation APPROVED, state=RESERVED, commit
        LL-->>LS: Reservation decision=APPROVED
        LS->>K: sendAndWait() → send(limit-results, key=id, withDecision APPROVED, limit, null).get(10 с)
    and group screening
        K->>SEL: transfers → onEvent(Transfer NEW)
        SEL->>SS: onNewTransfer(transfer)
        SS->>SJ: check(transfer)
        SJ->>SDB: checkRepository.findById(id) → пусто
        SJ->>SDB: blockedSenderRepository.findBySenderId(senderId) → пусто
        SJ->>SDB: save ScreeningCheck result=APPROVED, commit
        SJ-->>SS: ScreeningCheck APPROVED
        SS->>K: sendAndWait() → send(screening-results, key=id, withDecision APPROVED, screening, null).get(10 с)
    and group transfer-tracker
        K->>TEL: transfers → onEvent(Transfer NEW)
        Note over TEL: status=NEW пропускается
    end

    par group transfer-tracker, запись промежуточных результатов
        K->>TEL: limit-results → onEvent(Transfer APPROVED)
        TEL->>TTS: recordServiceResult(transfer, HistoryStage.LIMIT)
        TTS->>TDB: existsByTransferIdAndStage → false, save TransferHistoryEntry LIMIT, updatedAt
        K->>TEL: screening-results → onEvent(Transfer APPROVED)
        TEL->>TTS: recordServiceResult(transfer, HistoryStage.SCREENING)
        TTS->>TDB: existsByTransferIdAndStage → false, save TransferHistoryEntry SCREENING, updatedAt
    and Kafka Streams, принятие решения
        K->>KS: limit-results и screening-results с одним ключом id в JoinWindows 10 с
        KS->>TDS: decide(limitResult, screeningResult)
        TDS-->>KS: limitResult.withDecision(COMPLETED, null, null)
        KS->>K: to(transfers), Transfer status=COMPLETED
    end

    par group transfer-tracker
        K->>TEL: transfers → onEvent(Transfer COMPLETED)
        TEL->>TTS: applyFinalStatus(transfer)
        TTS->>TDB: existsByTransferIdAndStage FINAL → false, transfers.status=COMPLETED, save TransferHistoryEntry FINAL
    and group limit
        K->>LEL: transfers → onEvent(Transfer COMPLETED)
        LEL->>LS: onFinalStatus(transfer)
        LS->>LL: settle(transfer)
        LL->>LDB: isReserved() → markCommitted(), save Reservation state=COMMITTED, SenderUsage не меняется
    and group screening
        K->>SEL: transfers → onEvent(Transfer COMPLETED)
        Note over SEL: только лог, резервов у screening нет
    end

    C->>TC: GET /transfers/{id}, getById()
    TC->>TDB: findById(id), findByTransferIdOrderByChangedAtAsc(id)
    TC-->>C: 200 OK, transfer status=COMPLETED, history NEW, LIMIT, SCREENING, FINAL
```

### Диаграмма 3. Sequence diagram — основной сценарий отказа

Отправитель из стоп-листа (senderId=5 из 002-insert-test-data.sql) переводит сумму в пределах лимитов. limit-service уже успел зарезервировать сумму, screening-service отклоняет перевод с причиной SENDER_BLOCKED, и TransferDecisionService.decide возвращает CANCELLED. Выполненный резерв компенсируется в LimitLedger.settle (SenderUsage.release, Reservation переходит в RELEASED), а перевод, его история и ScreeningCheck сохраняются.

```mermaid
sequenceDiagram
    autonumber
    actor C as Клиент
    box transfer-service и transfer-db
        participant TC as TransferController
        participant TTS as TransferTrackingService
        participant TEL as TransferEventListener
        participant KS as DecisionTopologyConfig.decisionStream
        participant TDS as TransferDecisionService
        participant TDB as transferdb
    end
    participant K as Kafka broker
    box limit-service и limit-db
        participant LEL as TransferEventsListener
        participant LS as LimitService
        participant LL as LimitLedger
        participant LDB as limitdb
    end
    box screening-service и screening-db
        participant SEL as TransferEventsListener
        participant SS as ScreeningService
        participant SJ as ScreeningJournal
        participant SDB as screeningdb
    end

    C->>TC: POST /transfers, senderId=5, amount в пределах лимитов
    TC->>TTS: createTransfer(request)
    TTS->>TDB: INSERT transfers status=NEW и transfer_history stage=NEW
    TC->>K: publishNew() → send(transfers, key=id, Transfer status=NEW)
    TC-->>C: 201 Created, status=NEW

    par group limit, лимит одобрен и сумма зарезервирована
        K->>LEL: transfers → onEvent(Transfer NEW)
        LEL->>LS: onNewTransfer(transfer)
        LS->>LL: decide(transfer)
        Note over LL: лимиты не превышены
        LL->>LDB: SenderUsage.reserve(amount), save SenderUsage
        LL->>LDB: save Reservation decision=APPROVED, state=RESERVED, commit
        LL-->>LS: Reservation APPROVED
        LS->>K: sendAndWait() → send(limit-results, Transfer APPROVED, decidedBy=limit)
    and group screening, отправитель в стоп-листе
        K->>SEL: transfers → onEvent(Transfer NEW)
        SEL->>SS: onNewTransfer(transfer)
        SS->>SJ: check(transfer)
        SJ->>SDB: blockedSenderRepository.findBySenderId(5) → BlockedSender, reason Fraud detected
        SJ->>SDB: save ScreeningCheck result=DECLINED, reason=SENDER_BLOCKED
        SJ-->>SS: ScreeningCheck DECLINED
        SS->>K: sendAndWait() → send(screening-results, Transfer DECLINED, decidedBy=screening, messageInfo=SENDER_BLOCKED)
    end

    par group transfer-tracker, история
        K->>TEL: limit-results → onEvent(Transfer APPROVED)
        TEL->>TTS: recordServiceResult(transfer, LIMIT)
        TTS->>TDB: save TransferHistoryEntry LIMIT, APPROVED
        K->>TEL: screening-results → onEvent(Transfer DECLINED)
        TEL->>TTS: recordServiceResult(transfer, SCREENING)
        TTS->>TDB: save TransferHistoryEntry SCREENING, DECLINED, SENDER_BLOCKED
    and Kafka Streams, решение
        K->>KS: limit-results APPROVED и screening-results DECLINED в JoinWindows 10 с
        KS->>TDS: decide(limitResult, screeningResult)
        Note over TDS: ветка limitApproved и screeningDeclined
        TDS-->>KS: withDecision(CANCELLED, screening, SENDER_BLOCKED)
        KS->>K: to(transfers), Transfer status=CANCELLED
    end

    par group limit, компенсация резерва
        K->>LEL: transfers → onEvent(Transfer CANCELLED)
        LEL->>LS: onFinalStatus(transfer)
        LS->>LL: settle(transfer)
        LL->>LDB: reservationRepository.findById(id) → isReserved() = true
        LL->>LDB: usageRepository.findById(senderId) → SenderUsage
        Note over LL: release(amount, reservedOn, today) уменьшает dailyUsed, если reservedOn = today, и monthlyUsed в том же месяце, не ниже 0
        LL->>LDB: save SenderUsage, markReleased(), save Reservation state=RELEASED, commit
    and group transfer-tracker, финальный статус
        K->>TEL: transfers → onEvent(Transfer CANCELLED)
        TEL->>TTS: applyFinalStatus(transfer)
        TTS->>TDB: transfers.status=CANCELLED, decided_by=screening, message_info=SENDER_BLOCKED, history FINAL
    and group screening
        K->>SEL: transfers → onEvent(Transfer CANCELLED)
        Note over SEL: только лог, компенсировать нечего
    end

    C->>TC: GET /transfers/{id}
    TC-->>C: 200 OK, status=CANCELLED, decidedBy=screening, messageInfo=SENDER_BLOCKED
    Note over C,SDB: Если результаты limit и screening не попали в одно окно 10 с, join ничего не публикует.<br/>Перевод остаётся NEW, резерв остаётся RESERVED. Таймаута и автоматической компенсации в коде не найдено.
```

### Диаграмма 4. ER-диаграммы

Сущности хранятся в трёх независимых БД, поэтому диаграмма разбита на 4а–4в по базам и 4г для объектов вне БД. Сплошная линия обозначает FK, пунктир — логическую связь по идентификатору без FK.

#### 4а. БД transferdb (transfer-service)

Таблицы из 001-create-tables.sql, JPA-классы TransferEntity и TransferHistoryEntry; это единственная связь по FK во всём проекте.

```mermaid
erDiagram
    transfers {
        BIGINT id PK "задаёт клиент в TransferRequest.id"
        BIGINT sender_id "NOT NULL, idx_transfers_sender_id"
        BIGINT recipient_id "NOT NULL"
        DECIMAL amount "DECIMAL(15,2) NOT NULL"
        VARCHAR(10) currency "NOT NULL DEFAULT RUB"
        VARCHAR(20) status "NOT NULL, NEW, COMPLETED, CANCELLED, DENIED, idx_transfers_status"
        VARCHAR(20) decided_by "limit, screening или NULL"
        TEXT message_info "причина решения или NULL"
        TIMESTAMP created_at "NOT NULL"
        TIMESTAMP updated_at "NULL"
    }
    transfer_history {
        BIGSERIAL id PK
        BIGINT transfer_id FK "NOT NULL, idx_transfer_history_transfer_id"
        VARCHAR(20) status "NOT NULL, NEW, APPROVED, DECLINED, COMPLETED, CANCELLED, DENIED"
        VARCHAR(20) decided_by "limit, screening или NULL"
        TEXT message_info
        TIMESTAMP changed_at "NOT NULL DEFAULT NOW()"
        VARCHAR(20) stage "NOT NULL, HistoryStage NEW, LIMIT, SCREENING, FINAL"
    }
    transfers ||--|{ transfer_history : "FK transfer_id, UNIQUE transfer_id+stage, 1..4 записи"
```

#### 4б. БД limitdb (limit-service)

JPA-классы SenderUsage и Reservation. FK между таблицами нет, лимиты задаются в application.yml.

```mermaid
erDiagram
    sender_usage {
        BIGINT sender_id PK "senderId перевода"
        DECIMAL daily_used "DECIMAL(15,2) NOT NULL DEFAULT 0, предел limits.daily-max 50000.00"
        DECIMAL monthly_used "DECIMAL(15,2) NOT NULL DEFAULT 0, предел limits.monthly-max 200000.00"
        DATE last_daily_reset "NOT NULL DEFAULT CURRENT_DATE"
        DATE last_monthly_reset "NOT NULL DEFAULT CURRENT_DATE"
        BIGINT version "NOT NULL DEFAULT 0, JPA Version, оптимистическая блокировка"
    }
    reservations {
        BIGINT transfer_id PK "id перевода"
        BIGINT sender_id "NOT NULL, idx_reservations_sender_id"
        DECIMAL amount "DECIMAL(15,2) NOT NULL, 0 при INVALID_AMOUNT"
        VARCHAR(10) decision "NOT NULL, APPROVED или DECLINED"
        VARCHAR(50) reason "DAILY_LIMIT_EXCEEDED, MONTHLY_LIMIT_EXCEEDED, INVALID_AMOUNT или NULL"
        VARCHAR(20) state "NOT NULL, RESERVED, COMMITTED, RELEASED, NONE"
        DATE reserved_on "NOT NULL"
        TIMESTAMP created_at "NOT NULL"
        TIMESTAMP updated_at "NOT NULL"
    }
    sender_usage |o..|{ reservations : "sender_id, логическая связь без FK"
```

#### 4в. БД screeningdb (screening-service)

JPA-классы BlockedSender и ScreeningCheck. Стоп-лист заполняется только миграцией 002-insert-test-data.sql; API для его ведения в коде не найдено.

```mermaid
erDiagram
    blocked_senders {
        BIGSERIAL id PK
        BIGINT sender_id "NOT NULL UNIQUE, idx_blocked_senders_sender_id, тестовые 5, 13, 21, 34, 42"
        VARCHAR(255) reason "NOT NULL"
        TIMESTAMP blocked_at "NOT NULL DEFAULT NOW()"
    }
    screening_checks {
        BIGINT transfer_id PK "id перевода"
        BIGINT sender_id "NOT NULL, idx_screening_checks_sender_id"
        VARCHAR(10) result "NOT NULL, APPROVED или DECLINED"
        VARCHAR(50) reason "SENDER_BLOCKED или NULL"
        TIMESTAMP checked_at "NOT NULL"
    }
    blocked_senders |o..o{ screening_checks : "sender_id, логическая связь без FK"
```

#### 4г. Объекты вне БД

Объекты, которые передаются между компонентами, но не хранятся в БД:

- сообщение Transfer из transfer-common — значение во всех топиках, включая *.DLT;
- тело запроса TransferRequest;
- ответ GET /transfers/{id} — в коде это LinkedHashMap, в transfer-api.yaml ему соответствует схема TransferWithHistory;
- ProblemDetail — тело ответа при ошибках.

```mermaid
erDiagram
    TransferRequest {
        Long id "NotNull, задаёт клиент, ключ идемпотентности"
        Long senderId "NotNull"
        Long recipientId "NotNull"
        BigDecimal amount "NotNull, Positive, Digits integer=13 fraction=2"
        String currency "Pattern [A-Z]{3}, по умолчанию RUB"
    }
    Transfer {
        Long id "также ключ Kafka-записи"
        Long senderId
        Long recipientId
        BigDecimal amount
        String currency
        String status "NEW, APPROVED, DECLINED, COMPLETED, CANCELLED, DENIED"
        String decidedBy "limit, screening или null"
        String messageInfo "причина решения или null"
        LocalDateTime createdAt
    }
    TransferWithHistory {
        TransferEntity transfer "строка transfers"
        TransferHistoryEntry[] history "строки transfer_history, сортировка по stage и changedAt"
    }
    ProblemDetail {
        String type
        String title
        Integer status "400, 409, 500 или 503"
        String detail
        String instance
    }
    TransferRequest ||..o{ Transfer : "id, сообщения в transfers, limit-results, screening-results"
    TransferRequest ||..o| ProblemDetail : "ответ на ошибочный POST /transfers"
```

Логические связи между диаграммами 4а–4г:

- `TransferRequest.id` (4г) ↔ `transfers.id` (4а). Клиентский id становится PK перевода в TransferTrackingService.createTransfer; повторный запрос с тем же id новую строку не создаёт.
- `transfers.id` (4а) ↔ `Transfer.id` (4г). Это ключ Kafka-записи во всех топиках; на один перевод приходится NEW (возможны повторы), по одному результату от limit и screening и финальное сообщение.
- `transfers.id` (4а) ↔ `reservations.transfer_id` (4б), 1 : 0..1. Резерв создаёт LimitLedger.decide по сообщению NEW.
- `transfers.id` (4а) ↔ `screening_checks.transfer_id` (4в), 1 : 0..1. Запись создаёт ScreeningJournal.check.
- `transfers.sender_id` (4а) и `Transfer.senderId` (4г) ↔ `sender_usage.sender_id`, `reservations.sender_id` (4б) ↔ `blocked_senders.sender_id`, `screening_checks.sender_id` (4в). Это общий идентификатор отправителя; справочник отправителей в коде не найден.
- `transfers.recipient_id` (4а) и `Transfer.recipientId` (4г). Справочник получателей и какие-либо проверки получателя в коде не найдены, назначение поля требует уточнения.
- `reservations.decision` и `reservations.reason` (4б) → `Transfer.status` и `Transfer.messageInfo` в limit-results (4г) → `transfer_history` со stage=LIMIT (4а).
- `screening_checks.result` и `screening_checks.reason` (4в) → `Transfer` в screening-results (4г) → `transfer_history` со stage=SCREENING (4а).
- Финальный `Transfer` в transfers (4г) → `transfers.status`, `decided_by`, `message_info` и `transfer_history` со stage=FINAL (4а), а также `reservations.state` COMMITTED или RELEASED (4б).
- `TransferWithHistory.transfer` и `TransferWithHistory.history` (4г) → строки `transfers` и `transfer_history` (4а).

### Диаграмма 5. Диаграмма состояний перевода

Главная сущность — перевод: TransferEntity, поле transfers.status, значения совпадают с TransferStatus из transfer-api.yaml. Статус меняется только в TransferTrackingService.createTransfer и applyFinalStatus.

```mermaid
stateDiagram-v2
    [*] --> NEW : POST /transfers, createTransfer()
    NEW --> NEW : повторный POST с тем же id и параметрами, publishNew() повторно
    state decision <<choice>>
    NEW --> decision : join limit-results и screening-results в окне 10 с, decide(), applyFinalStatus()
    decision --> COMPLETED : limit APPROVED и screening APPROVED
    decision --> DENIED : limit DECLINED и screening DECLINED, messageInfo обеих причин
    decision --> CANCELLED : limit DECLINED и screening APPROVED, decidedBy=limit
    decision --> CANCELLED : limit APPROVED и screening DECLINED, decidedBy=screening
    decision --> CANCELLED : иное сочетание статусов, UNEXPECTED_RESULT
    COMPLETED --> [*]
    DENIED --> [*]
    CANCELLED --> [*]

    note right of NEW
        Перевод сохраняется в NEW даже при ответе 503 от Kafka.
        Результаты LIMIT и SCREENING пишутся только в transfer_history.
        POST с тем же id и другими параметрами даёт 409 без смены состояния.
        Таймаута для NEW в коде не найдено.
    end note

    note left of CANCELLED
        COMPLETED, DENIED и CANCELLED терминальные.
        Повторный FINAL пропускается в applyFinalStatus().
        Повторный POST отвечает 200 без публикации.
    end note
```

Другие сущности с жизненным циклом:

- **`Reservation.state`** (limit-service, reservations).
  - LimitLedger.decide создаёт резерв в состоянии RESERVED (decision=APPROVED) или NONE (decision=DECLINED с причиной DAILY_LIMIT_EXCEEDED, MONTHLY_LIMIT_EXCEEDED или INVALID_AMOUNT).
  - settle переводит RESERVED → COMMITTED при COMPLETED и RESERVED → RELEASED при CANCELLED или DENIED, возвращая сумму в SenderUsage.
  - NONE, COMMITTED и RELEASED — конечные состояния.
- **`ScreeningCheck.result`** (screening-service, screening_checks). Значение APPROVED или DECLINED (SENDER_BLOCKED) задаётся один раз в ScreeningJournal.check, переходов нет.
- **`TransferHistoryEntry.stage`** (transfer-service, transfer_history). Этапы NEW, LIMIT, SCREENING, FINAL — по одной записи на этап (ограничение uq_transfer_history_stage). При выдаче в TransferController.getById записи сортируются по stage.
- **`SenderUsage`** (limit-service, sender_usage). Статусов нет, но lazyReset сбрасывает счётчики dailyUsed и monthlyUsed при смене дня и месяца.