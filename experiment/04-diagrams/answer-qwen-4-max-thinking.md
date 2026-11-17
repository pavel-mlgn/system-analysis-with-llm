## Диаграмма 1. Component diagram (C4)

Три Spring Boot‑сервиса, три отдельные PostgreSQL‑базы и один брокер Kafka (KRaft) из `docker-compose.yml`; внешних интеграций (платёжные шлюзы, СМЭВ и т.п.) в коде не найдено. Все междусервисные связи — только через топики Kafka, синхронный вход один: REST `TransferController`.

```mermaid
flowchart TB
    client["Внешний клиент REST API - в коде не найден"]
    common["transfer-common - Maven-библиотека, record com.example.common.Transfer"]

    subgraph system["transfer-limit-system - docker-compose.yml"]
        subgraph c_transfer["Контейнер transfer-service - порт 8080"]
            transfer["transfer-service - TransferApp, Spring Web + Kafka Streams + JPA"]
        end
        subgraph c_limit["Контейнер limit-service - HTTP-порт не опубликован"]
            limit["limit-service - LimitApp, Spring Kafka + JPA"]
        end
        subgraph c_screen["Контейнер screening-service - HTTP-порт не опубликован"]
            screen["screening-service - ScreeningApp, Spring Kafka + JPA"]
        end
        subgraph c_broker["Контейнер broker - confluentinc/cp-kafka 7.6.0, KRaft, внутри broker 29092, снаружи localhost 9092"]
            t_transfers["topic transfers - 3 партиции, compact"]
            t_limit["topic limit-results - 3 партиции, compact"]
            t_screen["topic screening-results - 3 партиции, compact"]
            t_dlt["topics transfers.DLT, limit-results.DLT, screening-results.DLT - 3 партиции"]
        end
        subgraph c_tdb["Контейнер transfer-db - postgres 16-alpine, host-порт 5434"]
            transferdb[("transferdb - transfers, transfer_history")]
        end
        subgraph c_ldb["Контейнер limit-db - postgres 16-alpine, host-порт 5432"]
            limitdb[("limitdb - sender_usage, reservations")]
        end
        subgraph c_sdb["Контейнер screening-db - postgres 16-alpine, host-порт 5433"]
            screeningdb[("screeningdb - blocked_senders, screening_checks")]
        end
    end

    client -->|"HTTP REST - POST /transfers, GET /transfers, GET /transfers/{id}, порт 8080"| transfer
    transfer -->|"Kafka PLAINTEXT broker 29092 - produce transfers, ключ transferId, статус NEW"| t_transfers
    t_transfers -->|"Kafka consume - groupId limit, listener id limit-transfers-listener"| limit
    t_transfers -->|"Kafka consume - groupId screening, listener id screening-transfers-listener"| screen
    t_transfers -->|"Kafka consume - groupId transfer-tracker, финальные статусы COMPLETED, CANCELLED, DENIED"| transfer
    limit -->|"Kafka produce limit-results - APPROVED или DECLINED, decidedBy limit"| t_limit
    screen -->|"Kafka produce screening-results - APPROVED или DECLINED, decidedBy screening"| t_screen
    t_limit -->|"Kafka Streams consume - join по ключу transferId, JoinWindows 10 секунд"| transfer
    t_screen -->|"Kafka Streams consume - join по ключу transferId, JoinWindows 10 секунд"| transfer
    t_limit -->|"Kafka consume - groupId transfer-tracker, история stage LIMIT"| transfer
    t_screen -->|"Kafka consume - groupId transfer-tracker, история stage SCREENING"| transfer
    transfer -->|"Kafka Streams produce - результат join в transfers, финальный статус"| t_transfers
    limit -->|"Kafka produce - DLT после 3 повторов FixedBackOff 1000 мс, KafkaErrorHandlingConfig"| t_dlt
    screen -->|"Kafka produce - DLT после 3 повторов FixedBackOff 1000 мс, KafkaErrorHandlingConfig"| t_dlt
    transfer -->|"Kafka produce - DLT для KafkaListener, для Streams задан LogAndContinueExceptionHandler"| t_dlt
    transfer -->|"JDBC PostgreSQL transfer-db 5432 - JPA + Liquibase changelog-master.yaml"| transferdb
    limit -->|"JDBC PostgreSQL limit-db 5432 - JPA + Liquibase changelog-master.yaml"| limitdb
    screen -->|"JDBC PostgreSQL screening-db 5432 - JPA + Liquibase changelog-master.yaml"| screeningdb
    common -.->|"Maven dependency com.example transfer-common 1.0-SNAPSHOT"| transfer
    common -.->|"Maven dependency com.example transfer-common 1.0-SNAPSHOT"| limit
    common -.->|"Maven dependency com.example transfer-common 1.0-SNAPSHOT"| screen
```

## Диаграмма 2. Sequence diagram — happy path (перевод одобрен)

Основной успешный сценарий: `POST /transfers` → публикация `NEW` → одобрение лимита и скрининга → join в Kafka Streams (`DecisionTopologyConfig`) → `COMPLETED` → фиксация в `transferdb` и подтверждение резерва в `limitdb`. Обращения к БД показаны через participant‑базы, но выполняются репозиториями `TransferRepository`, `TransferHistoryRepository`, `SenderUsageRepository`, `ReservationRepository`, `BlockedSenderRepository`, `ScreeningCheckRepository`.

```mermaid
sequenceDiagram
    autonumber
    actor Client as "Клиент API"
    participant TC as "TransferController"
    participant TTS as "TransferTrackingService"
    participant TDB as "transferdb"
    participant K as "Kafka broker"
    participant LEL as "limit TransferEventsListener"
    participant LS as "LimitService"
    participant LL as "LimitLedger"
    participant LDB as "limitdb"
    participant SEL as "screening TransferEventsListener"
    participant SS as "ScreeningService"
    participant SJ as "ScreeningJournal"
    participant SDB as "screeningdb"
    participant ST as "DecisionTopologyConfig - Kafka Streams"
    participant TDS as "TransferDecisionService"
    participant TEL as "TransferEventListener"

    Note over K: Топики transfers, limit-results, screening-results - compacted, ключ transferId
    Client->>TC: POST /transfers, TransferRequest(id, senderId, recipientId, amount, currency)
    TC->>TDB: transferRepository.findById(request.id())
    TDB-->>TC: пусто - перевода с таким id нет
    TC->>TTS: createTransfer(request)
    Note over TTS,TDB:.Transactional метод, статус NEW и запись истории stage NEW
    TTS->>TDB: saveAndFlush(TransferEntity) и historyRepository.save(stage NEW)
    TDB-->>TTS: ok
    TTS-->>TC: TransferEntity(status=NEW)
    TC->>K: template.send('transfers', id, Transfer.newTransfer(...)).get(10 секунд)
    K-->>TC: ack от брокера
    TC-->>Client: 201 Created, TransferEntity(status=NEW)

    par limit-service
        K->>LEL: transfers, groupId=limit, Transfer(status=NEW)
        LEL->>LS: onNewTransfer(transfer)
        LS->>LL: decide(transfer)
        LL->>LDB: reservationRepository.findById(transferId) - записи нет
        LL->>LDB: senderUsageRepository.findById(senderId)
        LDB-->>LL: SenderUsage или новая запись
        Note over LL: lazyReset(today), dailyUsed плюс amount не больше limits.daily-max 50000.00, monthlyUsed плюс amount не больше limits.monthly-max 200000.00
        LL->>LDB: usage.reserve(amount), save(SenderUsage), save(Reservation APPROVED, state RESERVED)
        LL-->>LS: Reservation(decision=APPROVED)
        LS->>K: send('limit-results', id, transfer.withDecision(APPROVED, limit, null)).get(10 секунд)
    and screening-service
        K->>SEL: transfers, groupId=screening, Transfer(status=NEW)
        SEL->>SS: onNewTransfer(transfer)
        SS->>SJ: check(transfer)
        SJ->>SDB: checkRepository.findById(transferId) - записи нет
        SJ->>SDB: blockedSenderRepository.findBySenderId(senderId)
        SDB-->>SJ: пусто - отправитель не заблокирован
        SJ->>SDB: save(ScreeningCheck APPROVED, reason null)
        SJ-->>SS: ScreeningCheck(result=APPROVED)
        SS->>K: send('screening-results', id, transfer.withDecision(APPROVED, screening, null)).get(10 секунд)
    and transfer-service - журнал проверок
        K->>TEL: limit-results, groupId=transfer-tracker
        TEL->>TTS: recordServiceResult(transfer, HistoryStage.LIMIT)
        TTS->>TDB: history stage LIMIT со статусом APPROVED и transfers.updated_at
        K->>TEL: screening-results, groupId=transfer-tracker
        TEL->>TTS: recordServiceResult(transfer, HistoryStage.SCREENING)
        TTS->>TDB: history stage SCREENING со статусом APPROVED и transfers.updated_at
    end

    K->>ST: limit-results и screening-results, ключ transferId
    Note over ST: JoinWindows.ofTimeDifferenceWithNoGrace(10 секунд) - обе проверки должны попасть в окно
    ST->>TDS: decide(limitResult, screeningResult)
    TDS-->>ST: Transfer(status=COMPLETED, decidedBy null, messageInfo null)
    ST->>K: to('transfers') - Transfer(status=COMPLETED)

    par Финализация в transfer-service
        K->>TEL: transfers, groupId=transfer-tracker, status=COMPLETED
        TEL->>TTS: applyFinalStatus(transfer)
        TTS->>TDB: transfers.status=COMPLETED и history stage FINAL
    and Подтверждение резерва в limit-service
        K->>LEL: transfers, status=COMPLETED
        LEL->>LS: onFinalStatus(transfer)
        LS->>LL: settle(transfer)
        LL->>LDB: reservation.markCommitted(), state=COMMITTED, usage остаётся списанным
    and screening-service
        K->>SEL: transfers, status=COMPLETED
        Note over SEL: Резервов нет, только лог - nothing to do
    end

    Client->>TC: GET /transfers/{id}
    TC->>TDB: findById и findByTransferIdOrderByChangedAtAsc
    TDB-->>TC: TransferEntity и история NEW, LIMIT, SCREENING, FINAL
    TC-->>Client: 200 OK, transfer и history
```

## Диаграмма 3. Sequence diagram — отказ (отправитель заблокирован, резерв компенсируется)

Наиболее показательный отказ: `senderId=5` есть в `blocked_senders` (`screening-service/src/main/resources/db/changelog/002-insert-test-data.sql`), поэтому скрининг возвращает `DECLINED SENDER_BLOCKED`, а уже списанный лимит откатывается через `LimitLedger.settle` → `SenderUsage.release`. HTTP‑запрос при этом отвечает 201 — отказ формируется асинхронно; технические отказы (503 при недоступной Kafka, 409 при конфликте id) отмечены в примечаниях.

```mermaid
sequenceDiagram
    autonumber
    actor Client as "Клиент API"
    participant TC as "TransferController"
    participant TTS as "TransferTrackingService"
    participant TDB as "transferdb"
    participant K as "Kafka broker"
    participant LEL as "limit TransferEventsListener"
    participant LS as "LimitService"
    participant LL as "LimitLedger"
    participant LDB as "limitdb"
    participant SEL as "screening TransferEventsListener"
    participant SS as "ScreeningService"
    participant SJ as "ScreeningJournal"
    participant SDB as "screeningdb"
    participant ST as "DecisionTopologyConfig - Kafka Streams"
    participant TDS as "TransferDecisionService"
    participant TEL as "TransferEventListener"

    Client->>TC: POST /transfers, id=1001, senderId=5 - заблокированный отправитель
    TC->>TTS: createTransfer(request)
    TTS->>TDB: TransferEntity(status=NEW) и history stage NEW
    TC->>K: send('transfers', id, Transfer(status=NEW))
    TC-->>Client: 201 Created, status=NEW - запрос принят, отказ будет асинхронным

    par limit-service - резерв создан
        K->>LEL: transfers, groupId=limit, status=NEW
        LEL->>LS: onNewTransfer(transfer)
        LS->>LL: decide(transfer)
        LL->>LDB: usage.reserve(amount), Reservation(APPROVED, state=RESERVED)
        Note over LL,LDB: Лимит уже списан - это действие потребуется компенсировать
        LS->>K: send('limit-results', APPROVED, decidedBy=limit)
    and screening-service - отказ
        K->>SEL: transfers, groupId=screening, status=NEW
        SEL->>SS: onNewTransfer(transfer)
        SS->>SJ: check(transfer)
        SJ->>SDB: blockedSenderRepository.findBySenderId(5)
        SDB-->>SJ: BlockedSender(senderId=5, reason=Fraud detected)
        SJ->>SDB: save(ScreeningCheck DECLINED, reason=SENDER_BLOCKED)
        SS->>K: send('screening-results', DECLINED, decidedBy=screening, messageInfo=SENDER_BLOCKED)
    end

    K->>TEL: limit-results и screening-results, groupId=transfer-tracker
    TEL->>TTS: recordServiceResult(LIMIT) и recordServiceResult(SCREENING)
    TTS->>TDB: history LIMIT=APPROVED и SCREENING=DECLINED
    K->>ST: join limit-results и screening-results по ключу transferId
    ST->>TDS: decide(limitResult=APPROVED, screeningResult=DECLINED)
    TDS-->>ST: Transfer(status=CANCELLED, decidedBy=screening, messageInfo=SENDER_BLOCKED)
    ST->>K: to('transfers') - CANCELLED

    K->>TEL: transfers, status=CANCELLED
    TEL->>TTS: applyFinalStatus(transfer)
    TTS->>TDB: transfers.status=CANCELLED, decided_by=screening и history stage FINAL

    K->>LEL: transfers, status=CANCELLED
    LEL->>LS: onFinalStatus(transfer)
    LS->>LL: settle(transfer)
    Note over LL: reservation.isReserved() true, статус CANCELLED - ветка компенсации
    LL->>LDB: usage.release(amount, reservedOn, today) - daily только если reservedOn равен today, monthly только если тот же YearMonth, результат не ниже нуля
    LL->>LDB: reservation.markReleased(), state=RELEASED
    K->>SEL: transfers, status=CANCELLED
    Note over SEL: Screening не держит резервов - ничего не компенсирует

    Client->>TC: GET /transfers/{id}
    TC-->>Client: 200 OK, status=CANCELLED, decidedBy=screening, messageInfo=SENDER_BLOCKED

    Note over TDS: Другие ветки decide - обе DECLINED дают DENIED с склейкой messageInfo через разделитель точка с запятой, DECLINED только у limit даёт CANCELLED decidedBy=limit, при этом Reservation сразу в state=NONE и компенсация не нужна
    Note over TC,TDB: Технический отказ - publishNew при недоступной Kafka бросает ResponseStatusException 503, запись transfers остаётся в NEW без отката, повторный POST с тем же id переотправляет NEW через TransferController.duplicate, а при других параметрах возвращается 409 Conflict
```

## Диаграмма 4а. ER — transferdb (transfer-service)

Единственная связь по внешнему ключу во всём проекте: `transfer_history.transfer_id → transfers.id` плюс уникальность `(transfer_id, stage)`. Источник: `transfer-service/src/main/resources/db/changelog/001-create-tables.sql`, `TransferEntity`, `TransferHistoryEntry`.

```mermaid
erDiagram
    transfers {
        BIGINT id PK "задаётся клиентом в TransferRequest.id"
        BIGINT sender_id "NOT NULL"
        BIGINT recipient_id "NOT NULL"
        DECIMAL amount "DECIMAL(15,2) NOT NULL"
        VARCHAR currency "VARCHAR(10) NOT NULL DEFAULT RUB"
        VARCHAR status "VARCHAR(20) NOT NULL - NEW, COMPLETED, CANCELLED, DENIED"
        VARCHAR decided_by "VARCHAR(20) - limit, screening или null"
        TEXT message_info "причина отказа"
        TIMESTAMP created_at "NOT NULL"
        TIMESTAMP updated_at "nullable"
        BOOLEAN newEntity "@Transient, флаг Persistable.isNew, в БД не хранится"
    }
    transfer_history {
        BIGSERIAL id PK
        BIGINT transfer_id FK "REFERENCES transfers(id), UNIQUE(transfer_id, stage)"
        VARCHAR stage "VARCHAR(20) NOT NULL - HistoryStage NEW, LIMIT, SCREENING, FINAL"
        VARCHAR status "VARCHAR(20) NOT NULL"
        VARCHAR decided_by "VARCHAR(20)"
        TEXT message_info
        TIMESTAMP changed_at "NOT NULL DEFAULT NOW()"
    }
    transfers ||--o{ transfer_history : "FK transfer_id, не более 4 записей - по одной на stage"
```

## Диаграмма 4б. ER — limitdb (limit-service)

FK между таблицами нет: `reservations.sender_id` ссылается на `sender_usage.sender_id` логически. Источник: `limit-service/src/main/resources/db/changelog/001-create-tables.sql`, `SenderUsage`, `Reservation`.

```mermaid
erDiagram
    sender_usage {
        BIGINT sender_id PK
        DECIMAL daily_used "DECIMAL(15,2) NOT NULL DEFAULT 0"
        DECIMAL monthly_used "DECIMAL(15,2) NOT NULL DEFAULT 0"
        DATE last_daily_reset "NOT NULL DEFAULT CURRENT_DATE"
        DATE last_monthly_reset "NOT NULL DEFAULT CURRENT_DATE"
        BIGINT version "NOT NULL DEFAULT 0, @Version - оптимистичная блокировка"
    }
    reservations {
        BIGINT transfer_id PK "id перевода из сообщения Transfer"
        BIGINT sender_id "NOT NULL, логическая ссылка на sender_usage"
        DECIMAL amount "DECIMAL(15,2) NOT NULL, ноль при INVALID_AMOUNT"
        VARCHAR decision "VARCHAR(10) NOT NULL - APPROVED или DECLINED"
        VARCHAR reason "VARCHAR(50) - INVALID_AMOUNT, DAILY_LIMIT_EXCEEDED, MONTHLY_LIMIT_EXCEEDED"
        VARCHAR state "VARCHAR(20) NOT NULL - NONE, RESERVED, COMMITTED, RELEASED"
        DATE reserved_on "NOT NULL"
        TIMESTAMP created_at "NOT NULL"
        TIMESTAMP updated_at "NOT NULL"
    }
    sender_usage ||..o{ reservations : "логически по sender_id, FK нет"
```

## Диаграмма 4в. ER — screeningdb (screening-service)

FK нет, связь `screening_checks.sender_id` с `blocked_senders.sender_id` логическая; `blocked_senders` наполняется тестовыми отправителями 5, 13, 21, 34, 42 в `002-insert-test-data.sql`. Источник: `screening-service/src/main/resources/db/changelog/001-create-tables.sql`, `BlockedSender`, `ScreeningCheck`.

```mermaid
erDiagram
    blocked_senders {
        BIGSERIAL id PK
        BIGINT sender_id "NOT NULL UNIQUE"
        VARCHAR reason "VARCHAR(255) NOT NULL"
        TIMESTAMP blocked_at "NOT NULL DEFAULT NOW()"
    }
    screening_checks {
        BIGINT transfer_id PK "id перевода из сообщения Transfer"
        BIGINT sender_id "NOT NULL, логическая ссылка на blocked_senders"
        VARCHAR result "VARCHAR(10) NOT NULL - APPROVED или DECLINED"
        VARCHAR reason "VARCHAR(50) - SENDER_BLOCKED или null"
        TIMESTAMP checked_at "NOT NULL"
    }
    blocked_senders |o..o{ screening_checks : "логически по sender_id, FK нет"
```

## Диаграмма 4г. ER — объекты вне БД (сообщения и DTO)

`Transfer` (`transfer-common`) — сообщение Kafka, `TransferRequest` — входной DTO, `TransferWithHistory` описан только в `openapi/transfer-api.yaml` (в контроллере это `Map`), `ProblemDetail` — стандартный ответ об ошибке Spring.

```mermaid
erDiagram
    TransferRequest {
        Long id "@NotNull"
        Long senderId "@NotNull"
        Long recipientId "@NotNull"
        BigDecimal amount "@NotNull @Positive @Digits(integer=13, fraction=2)"
        String currency "@Pattern - три заглавные буквы, по умолчанию RUB"
    }
    Transfer {
        Long id "ключ сообщения Kafka во всех топиках"
        Long senderId
        Long recipientId
        BigDecimal amount
        String currency
        String status "NEW, APPROVED, DECLINED, COMPLETED, CANCELLED, DENIED"
        String decidedBy "limit, screening или null"
        String messageInfo "причина отказа или UNEXPECTED_RESULT"
        LocalDateTime createdAt "поля updatedAt в сообщении нет"
    }
    TransferWithHistory {
        Transfer transfer "схема openapi, в TransferController.getById фактически TransferEntity"
        List history "список TransferHistoryEntry, Java-класса в коде нет"
    }
    ProblemDetail {
        String type "org.springframework.http.ProblemDetail"
        String title
        int status "400, 409, 503, 500"
        String detail "русские тексты в файле в повреждённой кодировке"
        String instance
    }
    TransferRequest ||..|| Transfer : "Transfer.newTransfer в TransferController.publishNew"
    TransferWithHistory ||..|| Transfer : "поле transfer в ответе GET /transfers/{id}"
```

Логические связи между сущностями разных диаграмм (по идентификатору, без FK):
- `transferdb.transfers.id` ↔ `Transfer.id` (сообщение в топиках `transfers`, `limit-results`, `screening-results`) и ↔ `TransferRequest.id`.
- `transferdb.transfers.id` ↔ `limitdb.reservations.transfer_id`.
- `transferdb.transfers.id` ↔ `screeningdb.screening_checks.transfer_id`.
- `transferdb.transfers.sender_id` ↔ `limitdb.sender_usage.sender_id` и ↔ `limitdb.reservations.sender_id`.
- `transferdb.transfers.sender_id` ↔ `screeningdb.blocked_senders.sender_id` и ↔ `screeningdb.screening_checks.sender_id`.
- `transferdb.transfer_history.transfer_id` ↔ `TransferWithHistory.history` (элементы `TransferHistoryEntry`), `transferdb.transfers` ↔ `TransferWithHistory.transfer`.
- `Transfer.status` в сообщении ↔ `limitdb.reservations.decision` и ↔ `screeningdb.screening_checks.result` (значения APPROVED/DECLINED), финальные статусы сообщения ↔ `transferdb.transfers.status`.

## Диаграмма 5. Состояния ключевой сущности — TransferEntity.status (transfers.status)

Жизненный цикл перевода: `NEW` создаётся синхронно в `TransferTrackingService.createTransfer`, а финальный статус выставляется только в `applyFinalStatus` по результату `TransferDecisionService.decide`; обратных переходов и таймаута ожидания проверок в коде нет.

```mermaid
stateDiagram-v2
    [*] --> NEW : POST /transfers, TransferTrackingService.createTransfer, history stage NEW
    NEW --> NEW : повторный POST с тем же id и теми же параметрами, TransferController.duplicate переотправляет NEW в transfers
    NEW --> COMPLETED : Streams join, limit APPROVED и screening APPROVED, decidedBy null
    NEW --> CANCELLED : Streams join, limit DECLINED и screening APPROVED, decidedBy limit
    NEW --> CANCELLED : Streams join, limit APPROVED и screening DECLINED, decidedBy screening
    NEW --> CANCELLED : Streams join, неожиданный статус проверки, messageInfo UNEXPECTED_RESULT
    NEW --> DENIED : Streams join, limit DECLINED и screening DECLINED, messageInfo из обеих причин
    COMPLETED --> [*]
    CANCELLED --> [*]
    DENIED --> [*]

    note right of NEW
        Переход выполняет только TransferTrackingService.applyFinalStatus
        Дубликат финального статуса пропускается по existsByTransferIdAndStage FINAL
        Если проверки не попали в окно JoinWindows 10 секунд, статус остаётся NEW, таймаута и компенсации в коде нет
        При недоступной Kafka publishNew отдаёт 503, запись остаётся NEW
        Промежуточные APPROVED и DECLINED в transfers.status не пишутся, они только в transfer_history
    end note

    note left of COMPLETED
        Терминальные статусы, обратных переходов в коде нет
        Повторный POST с тем же id возвращает 200 OK без переотправки в Kafka
    end note
```

Остальные сущности с состояниями (диаграммы не рисовались):
- `Reservation` (`limitdb.reservations`): `decision` = APPROVED/DECLINED задаётся в `LimitLedger.decide`; `state`: NONE → RESERVED (approve) → COMMITTED (markCommitted при COMPLETED) или RELEASED (markReleased при CANCELLED/DENIED); при decline сразу NONE.
- `SenderUsage` — статусов нет, есть циклы `lazyReset` (сброс дня/месяца), `reserve` и `release`.
- `ScreeningCheck.result` — APPROVED/DECLINED, выставляется один раз в `ScreeningJournal.check`, переходов нет.
- `HistoryStage` (`transfer_history.stage`) — последовательность этапов NEW → LIMIT/SCREENING → FINAL, уникальна по `(transfer_id, stage)`, это журнал, а не статусная модель.