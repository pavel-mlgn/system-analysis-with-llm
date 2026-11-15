## Диаграмма 1. Component diagram (C4)

Контейнеры из `docker-compose.yml` и их основные компоненты: три Spring Boot-сервиса, брокер Kafka и три базы PostgreSQL; между собой сервисы общаются только через топики Kafka, REST API есть только у `transfer-service` (`TransferController.java`). Внешних систем, кроме клиента REST API, и потребителей DLT-топиков в коде не найдено; модуль `transfer-common` (record `Transfer`) — общая библиотека формата сообщений, а не контейнер.

```mermaid
flowchart TB
    subgraph EXT["Внешние системы"]
        client["Клиент REST API<br/>тип клиента и аутентификация в коде не найдены"]
    end

    subgraph TRANSFER_SVC["transfer-service: Spring Boot 3.4.1, Java 21, порт 8080"]
        tc["TransferController, ApiExceptionHandler<br/>REST /transfers, ошибки в формате ProblemDetail"]
        tts["TransferTrackingService"]
        tel["TransferEventListener<br/>KafkaListener id=transfer-tracker"]
        dtc["DecisionTopologyConfig.decisionStream<br/>Kafka Streams, join в окне 10 с, application.id явно не задан"]
        tds["TransferDecisionService"]
        trepo["TransferRepository, TransferHistoryRepository<br/>Spring Data JPA"]
    end

    subgraph KAFKA["broker: Kafka, образ confluentinc/cp-kafka:7.6.0, режим KRaft, адреса broker:29092 и localhost:9092"]
        tpTransfers(["топик transfers<br/>3 партиции, compact"])
        tpLimit(["топик limit-results<br/>3 партиции, compact"])
        tpScreening(["топик screening-results<br/>3 партиции, compact"])
        tpDlt(["топики transfers.DLT, limit-results.DLT, screening-results.DLT<br/>по 3 партиции, запись после 3 повторов с интервалом 1 с<br/>потребители в коде не найдены"])
    end

    subgraph LIMIT_SVC["limit-service: Spring Boot 3.4.1, Java 21, HTTP-эндпоинтов нет"]
        lel["TransferEventsListener<br/>KafkaListener id=limit-transfers-listener"]
        lsvc["LimitService"]
        lled["LimitLedger<br/>limits.daily-max=50000.00, limits.monthly-max=200000.00"]
        lrepo["ReservationRepository, SenderUsageRepository<br/>Spring Data JPA"]
    end

    subgraph SCREENING_SVC["screening-service: Spring Boot 3.4.1, Java 21, HTTP-эндпоинтов нет"]
        sel["TransferEventsListener<br/>KafkaListener id=screening-transfers-listener"]
        ssvc["ScreeningService"]
        sjrn["ScreeningJournal"]
        srepo["BlockedSenderRepository, ScreeningCheckRepository<br/>Spring Data JPA"]
    end

    subgraph DATABASES["Базы данных: PostgreSQL, образ postgres:16-alpine, отдельный контейнер на сервис, схемы создаёт Liquibase"]
        tdb[("transfer-db, БД transferdb<br/>таблицы transfers, transfer_history")]
        ldb[("limit-db, БД limitdb<br/>таблицы sender_usage, reservations")]
        sdb[("screening-db, БД screeningdb<br/>таблицы blocked_senders, screening_checks")]
    end

    client -->|"HTTP/JSON, порт 8080: POST /transfers, GET /transfers, GET /transfers/{id}"| tc
    tc -->|"вызов метода: createTransfer"| tts
    tc -->|"вызов метода: findById, findAllByOrderByCreatedAtDesc,<br/>findByTransferIdOrderByChangedAtAsc"| trepo
    tts -->|"вызов метода: saveAndFlush, save, findById, existsByTransferIdAndStage"| trepo
    tel -->|"вызов метода: recordServiceResult, applyFinalStatus"| tts
    dtc -->|"вызов метода: decide"| tds
    trepo -->|"JDBC, PostgreSQL: jdbc:postgresql://transfer-db:5432/transferdb"| tdb

    tc -->|"Kafka produce: топик transfers, key=id, value=Transfer JSON, status=NEW"| tpTransfers
    tpTransfers -->|"Kafka consume: топик transfers, groupId=transfer-tracker"| tel
    tpLimit -->|"Kafka consume: топик limit-results, groupId=transfer-tracker"| tel
    tpScreening -->|"Kafka consume: топик screening-results, groupId=transfer-tracker"| tel
    tpLimit -->|"Kafka Streams consume: топик limit-results"| dtc
    tpScreening -->|"Kafka Streams consume: топик screening-results"| dtc
    dtc -->|"Kafka Streams produce: топик transfers,<br/>status=COMPLETED, CANCELLED или DENIED"| tpTransfers
    tel -.->|"Kafka produce при ошибке обработки, KafkaErrorHandlingConfig:<br/>transfers.DLT, limit-results.DLT, screening-results.DLT"| tpDlt

    tpTransfers -->|"Kafka consume: топик transfers, groupId=limit"| lel
    lel -->|"вызов метода: onNewTransfer, onFinalStatus"| lsvc
    lsvc -->|"вызов метода: decide, settle"| lled
    lled -->|"вызов метода: findById, save"| lrepo
    lrepo -->|"JDBC, PostgreSQL: jdbc:postgresql://limit-db:5432/limitdb"| ldb
    lsvc -->|"Kafka produce: топик limit-results, key=id, value=Transfer JSON,<br/>status=APPROVED или DECLINED"| tpLimit
    lel -.->|"Kafka produce при ошибке обработки, KafkaErrorHandlingConfig: transfers.DLT"| tpDlt

    tpTransfers -->|"Kafka consume: топик transfers, groupId=screening"| sel
    sel -->|"вызов метода: onNewTransfer"| ssvc
    ssvc -->|"вызов метода: check"| sjrn
    sjrn -->|"вызов метода: findById, findBySenderId, save"| srepo
    srepo -->|"JDBC, PostgreSQL: jdbc:postgresql://screening-db:5432/screeningdb"| sdb
    ssvc -->|"Kafka produce: топик screening-results, key=id, value=Transfer JSON,<br/>status=APPROVED или DECLINED"| tpScreening
    sel -.->|"Kafka produce при ошибке обработки, KafkaErrorHandlingConfig: transfers.DLT"| tpDlt
```

## Диаграмма 2. Sequence diagram — успешный сценарий

Отправитель не числится в `blocked_senders`, сумма укладывается в лимиты из `limit-service/src/main/resources/application.yml`: обе проверки возвращают APPROVED, и `TransferDecisionService.decide` (`TransferDecisionService.java`) выставляет COMPLETED. Ответ 201 уходит сразу после публикации NEW, итог клиент читает через `GET /transfers/{id}`; движение денег по счетам в коде не найдено.

```mermaid
sequenceDiagram
    autonumber
    actor Client as Клиент REST API
    box transfer-service
        participant TC as TransferController
        participant TTS as TransferTrackingService
        participant TEL as TransferEventListener
        participant DS as DecisionTopologyConfig.decisionStream
        participant TDS as TransferDecisionService
    end
    participant TDB as transfer-db
    participant K as broker Kafka
    box limit-service
        participant LEL as TransferEventsListener
        participant LSV as LimitService
        participant LLG as LimitLedger
    end
    participant LDB as limit-db
    box screening-service
        participant SEL as TransferEventsListener
        participant SSV as ScreeningService
        participant SJR as ScreeningJournal
    end
    participant SDB as screening-db

    Client->>TC: POST /transfers, тело TransferRequest
    TC->>TDB: TransferRepository.findById(id)
    TDB-->>TC: запись не найдена
    TC->>TTS: createTransfer(request)
    TTS->>TDB: saveAndFlush(TransferEntity status=NEW)
    TTS->>TDB: save(TransferHistoryEntry stage=NEW, status=NEW)
    TTS-->>TC: TransferEntity
    TC->>K: publishNew, send в топик transfers, key=id, Transfer status=NEW
    K-->>TC: запись подтверждена, ожидание get до 10 с
    TC-->>Client: 201 Created, TransferEntity status=NEW
    Note over Client,K: Ответ отдан до принятия решения, дальше обработка асинхронная

    par limit-service, groupId=limit
        K-)LEL: топик transfers, Transfer status=NEW
        LEL->>LSV: onNewTransfer(transfer)
        LSV->>LLG: decide(transfer)
        LLG->>LDB: ReservationRepository.findById(transferId), записи нет
        LLG->>LDB: SenderUsageRepository.findById(senderId)
        LLG->>LLG: SenderUsage.lazyReset(today), проверка лимитов<br/>dailyMax=50000.00 и monthlyMax=200000.00
        LLG->>LDB: save(SenderUsage после reserve(amount))
        LLG->>LDB: save(Reservation decision=APPROVED, state=RESERVED)
        LLG-->>LSV: Reservation APPROVED
        LSV->>K: sendAndWait, топик limit-results,<br/>Transfer status=APPROVED, decidedBy=limit
    and screening-service, groupId=screening
        K-)SEL: топик transfers, Transfer status=NEW
        SEL->>SSV: onNewTransfer(transfer)
        SSV->>SJR: check(transfer)
        SJR->>SDB: ScreeningCheckRepository.findById(transferId), записи нет
        SJR->>SDB: BlockedSenderRepository.findBySenderId(senderId), отправитель не найден
        SJR->>SDB: save(ScreeningCheck result=APPROVED)
        SJR-->>SSV: ScreeningCheck APPROVED
        SSV->>K: sendAndWait, топик screening-results,<br/>Transfer status=APPROVED, decidedBy=screening
    end

    par TransferEventListener, groupId=transfer-tracker
        K-)TEL: топик limit-results, Transfer status=APPROVED
        TEL->>TTS: recordServiceResult(transfer, HistoryStage.LIMIT)
        TTS->>TDB: save(TransferHistoryEntry stage=LIMIT, status=APPROVED)
        K-)TEL: топик screening-results, Transfer status=APPROVED
        TEL->>TTS: recordServiceResult(transfer, HistoryStage.SCREENING)
        TTS->>TDB: save(TransferHistoryEntry stage=SCREENING, status=APPROVED)
    and Kafka Streams
        K-)DS: топики limit-results и screening-results,<br/>join по ключу id в окне 10 с
        DS->>TDS: decide(limitResult, screeningResult)
        TDS-->>DS: оба результата APPROVED, Transfer status=COMPLETED
        DS->>K: to(transfers), Transfer status=COMPLETED
    end

    par transfer-service, groupId=transfer-tracker
        K-)TEL: топик transfers, Transfer status=COMPLETED
        TEL->>TTS: applyFinalStatus(transfer)
        TTS->>TDB: save(TransferEntity status=COMPLETED)
        TTS->>TDB: save(TransferHistoryEntry stage=FINAL, status=COMPLETED)
    and limit-service, groupId=limit
        K-)LEL: топик transfers, Transfer status=COMPLETED
        LEL->>LSV: onFinalStatus(transfer)
        LSV->>LLG: settle(transfer)
        LLG->>LDB: ReservationRepository.findById(transferId), state=RESERVED
        LLG->>LDB: Reservation.markCommitted, save(Reservation state=COMMITTED)
    and screening-service, groupId=screening
        K-)SEL: топик transfers, Transfer status=COMPLETED
        Note over SEL: только запись в лог, резервов нет
    end

    Client->>TC: GET /transfers/{id}
    TC->>TDB: findById(id), findByTransferIdOrderByChangedAtAsc(id)
    TDB-->>TC: TransferEntity и список TransferHistoryEntry
    TC-->>Client: 200 OK, transfer status=COMPLETED,<br/>history NEW, LIMIT, SCREENING, FINAL
```

## Диаграмма 3. Sequence diagram — сценарий отказа

Отправитель найден в `blocked_senders` (тестовые строки — `screening-service/src/main/resources/db/changelog/002-insert-test-data.sql`), а `limit-service` к этому моменту уже зарезервировал лимит, поэтому итог — CANCELLED с `decidedBy=screening`. Уже сделанный резерв откатывается в `LimitLedger.settle` (`LimitLedger.java`) через `SenderUsage.release` и `Reservation.markReleased`; запись перевода и история в `transfer-service` не удаляются, а получают финальный статус.

```mermaid
sequenceDiagram
    autonumber
    actor Client as Клиент REST API
    box transfer-service
        participant TC as TransferController
        participant TTS as TransferTrackingService
        participant TEL as TransferEventListener
        participant DS as DecisionTopologyConfig.decisionStream
        participant TDS as TransferDecisionService
    end
    participant TDB as transfer-db
    participant K as broker Kafka
    box limit-service
        participant LEL as TransferEventsListener
        participant LSV as LimitService
        participant LLG as LimitLedger
    end
    participant LDB as limit-db
    box screening-service
        participant SEL as TransferEventsListener
        participant SSV as ScreeningService
        participant SJR as ScreeningJournal
    end
    participant SDB as screening-db

    Client->>TC: POST /transfers, TransferRequest с senderId из blocked_senders
    TC->>TTS: createTransfer(request)
    TTS->>TDB: saveAndFlush(TransferEntity status=NEW),<br/>save(TransferHistoryEntry stage=NEW)
    TC->>K: publishNew, топик transfers, Transfer status=NEW
    TC-->>Client: 201 Created, TransferEntity status=NEW

    par limit-service, groupId=limit
        K-)LEL: топик transfers, Transfer status=NEW
        LEL->>LSV: onNewTransfer(transfer)
        LSV->>LLG: decide(transfer)
        LLG->>LDB: save(SenderUsage после reserve(amount)),<br/>save(Reservation decision=APPROVED, state=RESERVED)
        Note over LLG,LDB: лимит отправителя уже зарезервирован
        LSV->>K: sendAndWait, топик limit-results,<br/>Transfer status=APPROVED, decidedBy=limit
    and screening-service, groupId=screening
        K-)SEL: топик transfers, Transfer status=NEW
        SEL->>SSV: onNewTransfer(transfer)
        SSV->>SJR: check(transfer)
        SJR->>SDB: BlockedSenderRepository.findBySenderId(senderId)
        SDB-->>SJR: BlockedSender найден
        SJR->>SDB: save(ScreeningCheck result=DECLINED, reason=SENDER_BLOCKED)
        SSV->>K: sendAndWait, топик screening-results, Transfer status=DECLINED,<br/>decidedBy=screening, messageInfo=SENDER_BLOCKED
    end

    par TransferEventListener, groupId=transfer-tracker
        K-)TEL: топик limit-results, Transfer status=APPROVED
        TEL->>TTS: recordServiceResult(transfer, HistoryStage.LIMIT)
        TTS->>TDB: save(TransferHistoryEntry stage=LIMIT, status=APPROVED)
        K-)TEL: топик screening-results, Transfer status=DECLINED
        TEL->>TTS: recordServiceResult(transfer, HistoryStage.SCREENING)
        TTS->>TDB: save(TransferHistoryEntry stage=SCREENING, status=DECLINED)
    and Kafka Streams
        K-)DS: топики limit-results и screening-results,<br/>join по ключу id в окне 10 с
        DS->>TDS: decide(limitResult, screeningResult)
        TDS-->>DS: limit APPROVED и screening DECLINED, Transfer status=CANCELLED,<br/>decidedBy=screening, messageInfo=SENDER_BLOCKED
        DS->>K: to(transfers), Transfer status=CANCELLED
    end

    par transfer-service, groupId=transfer-tracker
        K-)TEL: топик transfers, Transfer status=CANCELLED
        TEL->>TTS: applyFinalStatus(transfer)
        TTS->>TDB: save(TransferEntity status=CANCELLED,<br/>decidedBy=screening, messageInfo=SENDER_BLOCKED)
        TTS->>TDB: save(TransferHistoryEntry stage=FINAL, status=CANCELLED)
    and limit-service, компенсация резерва
        K-)LEL: топик transfers, Transfer status=CANCELLED
        LEL->>LSV: onFinalStatus(transfer)
        LSV->>LLG: settle(transfer)
        LLG->>LDB: ReservationRepository.findById(transferId), state=RESERVED
        LLG->>LDB: SenderUsageRepository.findById(senderId)
        LLG->>LLG: SenderUsage.release(amount, reservedOn, today)
        Note over LLG,LDB: dailyUsed уменьшается, если reservedOn равен текущей дате,<br/>monthlyUsed уменьшается, если месяц тот же
        LLG->>LDB: save(SenderUsage)
        LLG->>LDB: Reservation.markReleased, save(Reservation state=RELEASED)
    and screening-service, groupId=screening
        K-)SEL: топик transfers, Transfer status=CANCELLED
        Note over SEL: только запись в лог, компенсировать нечего
    end

    Client->>TC: GET /transfers/{id}
    TC-->>Client: 200 OK, transfer status=CANCELLED,<br/>decidedBy=screening, messageInfo=SENDER_BLOCKED
```

## Диаграмма 4. ER-диаграммы

У каждого сервиса своя база, схемы взяты из `db/changelog/001-create-tables.sql` соответствующего модуля; объекты, которые не хранятся в БД, вынесены в 4г. `DECIMAL(15,2)` записан как `DECIMAL` с уточнением в комментарии, потому что запятая в типе ломает разбор Mermaid.

### 4а. База transferdb (transfer-service)

Таблицы классов `TransferEntity` и `TransferHistoryEntry`.

```mermaid
erDiagram
    transfers {
        BIGINT id PK "задаёт клиент в TransferRequest.id"
        BIGINT sender_id "NOT NULL"
        BIGINT recipient_id "NOT NULL"
        DECIMAL amount "DECIMAL(15,2), NOT NULL"
        VARCHAR(10) currency "NOT NULL, DEFAULT 'RUB'"
        VARCHAR(20) status "NOT NULL, значения NEW, COMPLETED, CANCELLED, DENIED"
        VARCHAR(20) decided_by "допускает NULL, значения limit, screening"
        TEXT message_info "допускает NULL"
        TIMESTAMP created_at "NOT NULL"
        TIMESTAMP updated_at "допускает NULL"
    }
    transfer_history {
        BIGSERIAL id PK
        BIGINT transfer_id FK "NOT NULL, REFERENCES transfers(id)"
        VARCHAR(20) status "NOT NULL, значения NEW, APPROVED, DECLINED, COMPLETED, CANCELLED, DENIED"
        VARCHAR(20) decided_by "допускает NULL"
        TEXT message_info "допускает NULL"
        TIMESTAMP changed_at "NOT NULL, DEFAULT NOW()"
        VARCHAR(20) stage "NOT NULL, enum HistoryStage, значения NEW, LIMIT, SCREENING, FINAL"
    }
    transfers ||--o{ transfer_history : "FK transfer_id, UNIQUE (transfer_id, stage)"
```

### 4б. База limitdb (limit-service)

Таблицы классов `SenderUsage` и `Reservation`; внешних ключей в схеме нет.

```mermaid
erDiagram
    sender_usage {
        BIGINT sender_id PK
        DECIMAL daily_used "DECIMAL(15,2), NOT NULL, DEFAULT 0"
        DECIMAL monthly_used "DECIMAL(15,2), NOT NULL, DEFAULT 0"
        DATE last_daily_reset "NOT NULL, DEFAULT CURRENT_DATE"
        DATE last_monthly_reset "NOT NULL, DEFAULT CURRENT_DATE"
        BIGINT version "NOT NULL, DEFAULT 0, оптимистическая блокировка, аннотация Version"
    }
    reservations {
        BIGINT transfer_id PK
        BIGINT sender_id "NOT NULL, индекс idx_reservations_sender_id"
        DECIMAL amount "DECIMAL(15,2), NOT NULL"
        VARCHAR(10) decision "NOT NULL, значения APPROVED, DECLINED"
        VARCHAR(50) reason "допускает NULL, значения DAILY_LIMIT_EXCEEDED, MONTHLY_LIMIT_EXCEEDED, INVALID_AMOUNT"
        VARCHAR(20) state "NOT NULL, значения RESERVED, COMMITTED, RELEASED, NONE"
        DATE reserved_on "NOT NULL"
        TIMESTAMP created_at "NOT NULL"
        TIMESTAMP updated_at "NOT NULL"
    }
    sender_usage |o..o{ reservations : "sender_id, без FK"
```

### 4в. База screeningdb (screening-service)

Таблицы классов `BlockedSender` и `ScreeningCheck`; внешних ключей в схеме нет.

```mermaid
erDiagram
    blocked_senders {
        BIGSERIAL id PK
        BIGINT sender_id "NOT NULL, UNIQUE"
        VARCHAR(255) reason "NOT NULL"
        TIMESTAMP blocked_at "NOT NULL, DEFAULT NOW()"
    }
    screening_checks {
        BIGINT transfer_id PK
        BIGINT sender_id "NOT NULL, индекс idx_screening_checks_sender_id"
        VARCHAR(10) result "NOT NULL, значения APPROVED, DECLINED"
        VARCHAR(50) reason "допускает NULL, значение SENDER_BLOCKED"
        TIMESTAMP checked_at "NOT NULL"
    }
    blocked_senders |o..o{ screening_checks : "sender_id, без FK"
```

### 4г. Объекты, которые не хранятся в БД

Record `Transfer` (`transfer-common`, сообщение во всех трёх топиках) и record `TransferRequest` (тело `POST /transfers`). `TransferWithHistory` и `Problem` — имена схем из `openapi/transfer-api.yaml`; в Java это `Map` в `TransferController.getById` и `ProblemDetail`.

```mermaid
erDiagram
    TransferRequest {
        Long id "NotNull"
        Long senderId "NotNull"
        Long recipientId "NotNull"
        BigDecimal amount "NotNull, Positive, Digits(integer=13, fraction=2)"
        String currency "Pattern [A-Z]{3}, если null, то RUB"
    }
    Transfer {
        Long id "он же ключ сообщения Kafka"
        Long senderId
        Long recipientId
        BigDecimal amount
        String currency
        String status "NEW, APPROVED, DECLINED, COMPLETED, CANCELLED, DENIED"
        String decidedBy "limit, screening или null"
        String messageInfo "причина отказа или null"
        LocalDateTime createdAt
    }
    TransferWithHistory {
        TransferEntity transfer "строка таблицы transfers"
        List history "элементы TransferHistoryEntry, сортировка по stage и changedAt"
    }
    Problem {
        String type
        String title
        Integer status
        String detail
        String instance
    }
    TransferRequest ||..o{ Transfer : "один id у запроса и всех сообщений перевода"
```

Логические связи между сущностями разных диаграмм (без FK, по значению идентификатора):

- `transfers.id` (4а) — `reservations.transfer_id` (4б): 1 к 0..1, резерв создаёт `LimitLedger.decide`.
- `transfers.id` (4а) — `screening_checks.transfer_id` (4в): 1 к 0..1, запись создаёт `ScreeningJournal.check`.
- `transfers.sender_id` (4а) — `sender_usage.sender_id` (4б): N к 0..1.
- `transfers.sender_id` (4а) — `blocked_senders.sender_id` (4в): N к 0..1.
- `Transfer.id` (4г) — `transfers.id` (4а), `reservations.transfer_id` (4б), `screening_checks.transfer_id` (4в): N к 1, id перевода служит и ключом сообщения Kafka.
- `Transfer.senderId` (4г) — `sender_usage.sender_id` (4б), `blocked_senders.sender_id` (4в): N к 0..1.
- `Transfer` (4г) — `transfer_history` (4а): сообщения из `limit-results`, `screening-results` и итоговое из `transfers` сохраняются строками со stage LIMIT, SCREENING, FINAL, не более одной на stage (`TransferTrackingService.recordServiceResult`, `applyFinalStatus`).
- `TransferRequest.id` (4г) — `transfers.id` (4а): N к 1, повтор запроса с тем же id возвращает существующую запись (`TransferController.duplicate`).
- `TransferWithHistory` (4г) — `transfers` и `transfer_history` (4а): один перевод и до 4 записей истории, собирает `TransferController.getById`.
- Ответы `POST /transfers` и `GET /transfers` — сериализованный `TransferEntity` (схема `Transfer` в `transfer-api.yaml`); отдельного DTO в коде не найдено.
- Справочники отправителей и получателей, на которые указывают `sender_id` и `recipient_id`, в коде не найдены.

## Диаграмма 5. Диаграмма состояний

Ключевая сущность — перевод: поле `status` класса `TransferEntity` (таблица `transfers`). Финальный статус вычисляет `TransferDecisionService.decide` и записывает `TransferTrackingService.applyFinalStatus` (`TransferTrackingService.java`).

```mermaid
stateDiagram-v2
    state decision <<choice>>

    [*] --> NEW : POST /transfers, TransferTrackingService.createTransfer
    NEW --> NEW : повторный POST с тем же id и параметрами, TransferController.duplicate заново публикует NEW
    NEW --> decision : пара результатов limit и screening соединена в окне 10 с, TransferDecisionService.decide
    decision --> COMPLETED : limit APPROVED и screening APPROVED
    decision --> CANCELLED : limit DECLINED и screening APPROVED, decidedBy = limit
    decision --> CANCELLED : limit APPROVED и screening DECLINED, decidedBy = screening
    decision --> CANCELLED : любые другие статусы результатов, messageInfo = UNEXPECTED_RESULT
    decision --> DENIED : limit DECLINED и screening DECLINED, decidedBy = null
    COMPLETED --> [*]
    CANCELLED --> [*]
    DENIED --> [*]

    note left of NEW
        Таймаут ожидания результатов в коде не найден.
        Если пара результатов не сошлась в окне, перевод остаётся NEW
    end note
    note right of COMPLETED
        Финальный статус применяется один раз.
        Повторные финальные сообщения TransferTrackingService.applyFinalStatus пропускает
    end note
```

Другие сущности со статусами:

- `Reservation.state` (`limit-service`, `Reservation.java`, переходы в `LimitLedger.settle`): RESERVED → COMMITTED при итоге COMPLETED, RESERVED → RELEASED при CANCELLED или DENIED; NONE — лимит отклонён, переходов нет.
- `Reservation.decision` и `ScreeningCheck.result`: APPROVED или DECLINED, задаются один раз, переходов нет.
- `TransferHistoryEntry.stage` (NEW, LIMIT, SCREENING, FINAL) — журнал этапов, не более одной записи на этап; жизненным циклом не является.