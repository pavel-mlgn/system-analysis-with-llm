# Проверка эталона на живом коде

5 октября 2026. Проверяемый документ: `reference/etalon.md`, код: `transfer-limit-system/` (= `experiment/code.txt`)

**Метод.** (1) Сверка распакованного кода с `code.txt`. (2) Статическая проверка каждого фактического утверждения эталона по коду, с файлом и строкой. (3) Запуск системы в Docker (`docker compose up -d --build`) и 14 обязательных проверок R1–R14 плюс 9 дополнительных (R15–R23) там, где утверждение эталона можно подтвердить только поведением. Код проекта, эталон и `experiment/` не изменялись, ответы моделей не читались. В конце выполнен `docker compose down -v`.

**Окружение запуска.** Docker 29.8.2, Compose v5.5.1. В образе transfer-service: Spring Boot 3.4.1, Java 21.0.12.1, spring-kafka 3.3.1, kafka-clients / kafka-streams 3.8.1, Hibernate 6.6.4, Jackson 2.18.2, hibernate-validator 8.0.2, Liquibase 4.29.2. Брокер - `confluentinc/cp-kafka:7.6.0`. Часовой пояс всех контейнеров - UTC.

**Шаг 0. Сверка кода.** `code.txt` содержит 57 блоков `===== FILE: … =====`, и все 57 побайтно совпадают с файлами в `transfer-limit-system/`. На диске есть еще 8 файлов, которых нет в `code.txt`: `.dockerignore`, `.gitattributes`, `.gitignore`, `.mvn/wrapper/maven-wrapper.properties`, `LICENSE`, `README.md`, `mvnw`, `mvnw.cmd`. Это ровно тот список, который эталон (0.1, п. 2) исключает из оценки. Расхождений в Java-коде, конфигурации и миграциях нет, поэтому проверка продолжена. Повторная сверка после всех запусков дала тот же результат: 57 из 57 файлов идентичны, новых файлов нет.

---

## 1. Сводка

| | Количество |
|---|---|
| Проверено утверждений (строк таблицы раздела 2; составные таблицы эталона, например "частые искажения", считаются одной строкой) | **178** |
| Подтверждено | **169** (из них **76** дополнительно подтверждены запуском) |
| Неточно | **8**: P-6, P-7, L2, K-13, B2, B7, G-3, G-8 |
| Неверно | **0** |
| Не проверено | **1**: G-0, рендер Mermaid в mermaid-cli 11 (вне задания) |
| Проверки на запуске | **R1–R14 выполнены все**, плюс R4b и R15–R23; ожидания эталона подтвердились во всех 24 проверках, в двух (R15, R20) запуск уточнил формулировку эталона |

**Главное.**

1. В эталоне **не найдено ни одного неверного утверждения о поведении системы**. Все бизнес-правила (T, L, S, матрица 2.2, сценарии 1–12), модель данных, топики, группы и REST-контракт подтвердились кодом, а ключевые из них - еще и запуском.
2. Оба пункта с пометкой "проверить запуском" подтвердились запуском, пометку можно снять:
   - `createdAt` в Kafka сериализуется **массивом** (`[2026,10,5,15,13,32,938123000]`), в REST - ISO-строкой;
   - `amount: 10.100` дает **400** (O8).
3. Спорный пункт T4 подтвержден: при недоступной Kafka клиент получает **503, а не 500**. Время ответа - 10,05 с при прогретом продюсере и 5,1 с, если метаданных топика еще нет.
4. Неточности такие:
   - неверная перекрестная ссылка в L2 (B15 вместо B14);
   - неполный список зависимостей limit- и screening-service;
   - "молча" в B2: на деле Streams пишет WARN;
   - заниженный масштаб B7: повторы дают до N² финалов, а не "несколько";
   - настройки продюсера в 5.2 не относятся к продюсеру Kafka Streams;
   - на диаграмме 9.3 нет публикации NEW, а пометка в ней путает состояние резерва и занятый лимит;
   - внутреннее противоречие в правиле подсчета 9.4.
5. Найдено **14 фактов**, которых в эталоне нет (раздел 5). Самые важные для оценки ответов:
   - квадратичное размножение финалов при повторных POST;
   - ответ limit- и screening-service на повторный NEW собирается из нового сообщения;
   - битое сообщение в `transfers` попадает в DLT тремя копиями, по одной на группу;
   - transfer-service не стартует, если имя брокера не резолвится;
   - тексты ошибок 400 - английские.

## 2. Статическая проверка

**Сокращения путей** (все - внутри `transfer-limit-system/`):
`TS/` = `transfer-service/src/main/java/com/example/transfer/`, `TR/` = `transfer-service/src/main/resources/`,
`LS/` = `limit-service/src/main/java/com/example/limit/`, `LR/` = `limit-service/src/main/resources/`,
`SS/` = `screening-service/src/main/java/com/example/screening/`, `SR/` = `screening-service/src/main/resources/`,
`CM` = `transfer-common/src/main/java/com/example/common/Transfer.java`, `DC` = `docker-compose.yml`, `DF` = `Dockerfile`.
Номера строк одинаковы в `transfer-limit-system/` и в соответствующих блоках `experiment/code.txt` (файлы идентичны, шаг 0).

Вердикты: подтверждено, неверно, неточно. Пометка "запуск" значит, что утверждение дополнительно проверено запуском (итог - в разделе 3, здесь - итоговый вердикт с учетом запуска).

### 2.0 Раздел 0 и шапка

| ID | Утверждение | Вердикт | Доказательство |
|---|---|---|---|
| 0-1 | `code.txt` - 57 файлов проекта | подтверждено | Шаг 0: 57 блоков `===== FILE:`; все 57 совпадают с диском побайтно |
| 0-2 | У моделей не было README, LICENSE, mvnw, mvnw.cmd, .mvn/, .gitignore, .gitattributes, .dockerignore | подтверждено | Шаг 0: на диске ровно эти 8 файлов отсутствуют в `code.txt` |

### 2.1 Раздел 1. Паспорт системы

| ID | Утверждение | Вердикт | Доказательство |
|---|---|---|---|
| P-1 | Назначение: две независимые проверки (лимиты, стоп-лист отправителей), итог в transfer-service через Streams JOIN; движения денег нет | подтверждено | `LS/service/LimitLedger.java:37-72`, `SS/service/ScreeningJournal.java:28-47`, `TS/config/DecisionTopologyConfig.java:29-38`; ни одной операции со счетами/балансами |
| P-2 | Хореография, без оркестратора, database-per-service; проверки параллельны, итог - JOIN | подтверждено | `LS/listener/TransferEventsListener.java:21`, `SS/listener/TransferEventsListener.java:21` (разные группы на одном топике); `DC:28-80` (три БД) |
| P-3 | Корневой `pom.xml`: packaging `pom`, агрегатор без общего родителя | подтверждено | `pom.xml:9,17-22`; у сервисов parent - `spring-boot-starter-parent` (`*/pom.xml:5-10`), у `transfer-common` parent нет |
| P-4 | `transfer-common`: record `Transfer`, без зависимостей и без родителя Spring Boot | подтверждено | `transfer-common/pom.xml:1-18` (нет `<parent>`, нет `<dependencies>`); `CM:6` |
| P-5 | transfer-service: HTTP 8080; web, validation, spring-kafka, kafka-streams, data-jpa, postgresql, liquibase, transfer-common | подтверждено | `transfer-service/pom.xml:22-55`; `DC:89-90` |
| P-6 | limit-service: без web; starter, spring-kafka, data-jpa, postgresql, liquibase, jackson-jsr310, transfer-common | неточно | Список неполный: явно объявлен еще `jackson-databind` (`limit-service/pom.xml:43-46`). Отсутствие web верно (`:22-25` - только `spring-boot-starter`) |
| P-7 | screening-service: то же | неточно | То же: `screening-service/pom.xml:43-46` - `jackson-databind` |
| P-8 | Java 21; Spring Boot 3.4.1 parent во всех трех сервисах | подтверждено | `*/pom.xml:8,18`; `pom.xml:13` |
| P-9 | Версии spring-kafka и kafka-streams не указаны явно | подтверждено | `transfer-service/pom.xml:30-37` (без `<version>`). В собранном образе: spring-kafka 3.3.1, kafka-clients и kafka-streams 3.8.1 |
| P-10 | PostgreSQL 16 (`postgres:16-alpine`); `cp-kafka:7.6.0` в KRaft без ZooKeeper | подтверждено | `DC:3,12-14,29,47,65`; сервиса zookeeper нет |
| P-11 | Liquibase (SQL-changelog), Jackson JSR-310, тестов нет | подтверждено | `*R/db/changelog/001-create-tables.sql:1` (`--liquibase formatted sql`), мастер - YAML; `src/test` нет ни в одном модуле, test-зависимостей нет |
| P-12 | Таблица каналов: кто пишет и читает `transfers`, `*-results`, DLT | подтверждено, запуск | `TS/controller/TransferController.java:120`, `TS/config/DecisionTopologyConfig.java:38`, `TS/listener/TransferEventListener.java:23-25`, `LS/service/LimitService.java:38`, `SS/service/ScreeningService.java:34`; DLT - `*/config/KafkaErrorHandlingConfig.java`; R13: группы `limit`, `screening` -> `transfers`; `transfer-tracker` -> все три топика |
| P-13 | Сноска ¹: `application.id` не задан, берется `spring.application.name` = `transfer-service` | подтверждено, запуск | `TR/application.yml:1,20-26` (нет `application-id`); R13: группа `transfer-service` читает только `limit-results` и `screening-results`; в логе `application.id = transfer-service` |
| P-14 | Брокер: 9092 наружу, 29092 внутри, автосоздание выключено | подтверждено | `DC:6,10-11,17` |
| P-15 | Три БД: transferdb 5434, limitdb 5432, screeningdb 5433 | подтверждено | `DC:32,50,68` |
| P-16 | Профиль `docker`; HTTP наружу только transfer-service 8080 | подтверждено | `DC:89-90,97,112,127`; у limit/screening нет `ports` |
| P-17 | Многостадийный `Dockerfile` с `ARG MODULE`, JRE 21, не root | подтверждено | `DF:1,6-7,15-20` (`useradd ... app`, `USER app`) |
| P-18 | Healthcheck у брокера и трех БД; `service_healthy`; `restart: unless-stopped` | подтверждено | `DC:21-26,39-44,57-62,75-80,91-95,106-110,121-125`; `restart` у всех 7 сервисов |
| P-19 | Именованные тома у брокера и БД; у state-dir Streams тома нет | подтверждено | `DC:19-20,37-38,55-56,73-74,129-133`; `TR/application.yml:26` |

### 2.2 Раздел 2.1. transfer-service (T1–T13)

| ID | Утверждение | Вердикт | Доказательство |
|---|---|---|---|
| T1 | id задает клиент, ключ идемпотентности; `TransferEntity implements Persistable` | подтверждено | `TS/controller/TransferRequest.java:11`; `TS/domain/TransferEntity.java:12,17-18,33-42` |
| T2 | Валидация: 4 поля обязательны; amount > 0, `@Digits(13,2)`; currency `[A-Z]{3}`, по умолчанию RUB | подтверждено, запуск | `TS/controller/TransferRequest.java:11-21`; R3, R5 |
| T3 | Новый id: INSERT перевода и истории NEW в одной транзакции, затем публикация с ожиданием до 10 с, 201 | подтверждено, запуск | `TS/service/TransferTrackingService.java:31-49`; `TS/controller/TransferController.java:62-70,120`; R1 |
| T4 | Kafka недоступна -> 503 problem+json через 5–10 с; NEW в БД; 503 обрабатывает `ResponseEntityExceptionHandler`, а не `handleUnexpected` | подтверждено, запуск | `TS/controller/TransferController.java:116-128`; `TS/controller/ApiExceptionHandler.java:12,17-21`; R4: 503 через 10,05 с (прогретый продюсер) и 5,1 с (без метаданных); `handleUnexpected` не вызывался |
| T5 | Тот же id и параметры -> 200; amount через `compareTo`, currency с умолчанием; NEW публикуется повторно | подтверждено, запуск | `TS/controller/TransferController.java:95-114`; R2 |
| T6 | Другие параметры -> 409 problem+json от контроллера | подтверждено, запуск | `TS/controller/TransferController.java:96-100`; R2 |
| T7 | Гонка: нарушение PK -> ветка дубликата; строки нет -> 500 | подтверждено, запуск | `TS/controller/TransferController.java:61-67`; `orElseThrow(() -> e)` -> `ApiExceptionHandler.handleUnexpected` -> 500; R15 (гонка 10 запросов) |
| T8 | Трекер пишет LIMIT/SCREENING только в историю; статус не меняется; обновляется `updatedAt`, сущность сохраняется целиком | подтверждено | `TS/service/TransferTrackingService.java:51-68` (полный UPDATE - Hibernate без `@DynamicUpdate`; виден в show-sql, R1) |
| T9 | Финал (любой статус ≠ NEW) -> status, decidedBy, messageInfo и история FINAL | подтверждено | `TS/listener/TransferEventListener.java:31-35`; `TS/service/TransferTrackingService.java:70-93` |
| T10 | Не больше одной записи на этап: `existsByTransferIdAndStage` + UNIQUE; первый финал побеждает | подтверждено | `TS/service/TransferTrackingService.java:58,77`; `TR/db/changelog/001-create-tables.sql:29` |
| T11 | Ответ по неизвестному переводу - пропуск с предупреждением | подтверждено, запуск | `TS/service/TransferTrackingService.java:54-56,73-75` (WARN); R10 |
| T12 | История: сортировка по этапу, затем по времени; два запроса без общей транзакции | подтверждено | `TS/controller/TransferController.java:80,84-87`; `TS/domain/HistoryStage.java:3-8` (порядок enum); у `getById` нет `@Transactional`, `open-in-view: false` (`TR/application.yml:35`) |
| T13 | `createdAt/updatedAt/changedAt` усечены до микросекунд | подтверждено, запуск | `TS/service/TransferTrackingService.java:95-97,99-104` (конструктор истории ставит `now()` без усечения, но `historyEntry` перезаписывает `changedAt`); R1: `2026-10-05T15:13:32.938123` |

### 2.3 Раздел 2.2. Решение JOIN

| ID | Утверждение | Вердикт | Доказательство |
|---|---|---|---|
| J-1 | APPROVED/APPROVED -> COMPLETED, decidedBy null | подтверждено, запуск | `TS/service/TransferDecisionService.java:18-19`; R1 |
| J-2 | APPROVED/DECLINED -> CANCELLED, `screening`, причина screening | подтверждено, запуск | `:25-26`; R8 |
| J-3 | DECLINED/APPROVED -> CANCELLED, `limit`, причина limit | подтверждено, запуск | `:23-24`; R7 |
| J-4 | DECLINED/DECLINED -> DENIED, null, `"<limit>; <screening>"` | подтверждено, запуск | `:20-22`; R9 |
| J-5 | Прочее (null, другой регистр) -> CANCELLED, null, `UNEXPECTED_RESULT: limit=…, screening=…`; NEW невозможен | подтверждено, запуск | `:27-29`; R19 (инъекция `approved`) |
| J-6 | Итог строится из ответа limit через `withDecision` | подтверждено | `:19,21,24,26,28`; `CM:23-25` |
| J-7 | Inner join `limit-results` и `screening-results`, окно 10 с без grace, результат в `transfers` | подтверждено | `TS/config/DecisionTopologyConfig.java:29-38` |
| J-8 | Нет пары в окне -> финала нет, NEW | подтверждено, запуск | там же; R14 |
| J-9 | Последствия для резерва (COMMITTED / RELEASED / NONE / NONE / RELEASED) | подтверждено, запуск | `LS/service/LimitLedger.java:74-102`; R1, R7, R8, R9, R19 |

### 2.4 Раздел 2.3. limit-service (L1–L15)

| ID | Утверждение | Вердикт | Доказательство |
|---|---|---|---|
| L1 | Группа `limit`: NEW -> `onNewTransfer`, иначе -> `onFinalStatus` | подтверждено | `LS/listener/TransferEventsListener.java:21-29` |
| L2 | Повторный NEW -> сохраненное решение, счетчики не меняются, ответ снова; сверяется только transferId **(B15)** | неточно | Суть подтверждена: `LS/service/LimitLedger.java:39-43`, `LS/service/LimitService.java:27-30`; R23 (при этом ответ собирается из *нового* сообщения - см. раздел 5). **Ссылка неверна:** дефект "идемпотентность только по transferId" - это B14, а B15 - "дубль после 503" |
| L3 | amount null или ≤ 0 -> DECLINED `INVALID_AMOUNT`, резерв с суммой 0, счетчики не трогаются | подтверждено, запуск | `LS/service/LimitLedger.java:48-52`; R10 |
| L4 | Счетчики на отправителя; новый - строка с нулями | подтверждено | `LS/service/LimitLedger.java:55-56`; `LS/domain/SenderUsage.java:25-31` |
| L5 | Ленивое обнуление дня и месяца | подтверждено, запуск | `LS/domain/SenderUsage.java:33-42`; R17 |
| L6 | Порядок проверок; накопленная сумма; при отказе DAILY/MONTHLY `sender_usage` сохраняется, при INVALID_AMOUNT - нет | подтверждено, запуск | `LS/service/LimitLedger.java:61-71`; `LR/application.yml:35-37`; R7, R17 |
| L7 | Граница включительна, `> 0`; один перевод > 50 000.00 всегда отклоняется | подтверждено, запуск | `LS/service/LimitLedger.java:61`; `SenderUsage.release` не опускает ниже 0 (`:52,55`); R7 |
| L8 | Оба лимита превышены -> DAILY | подтверждено, запуск | `LS/service/LimitLedger.java:61-64`; R7 (250 000) |
| L9 | Лимиты общие, из конфигурации, без валюты | подтверждено | `LS/service/LimitLedger.java:26-30`; `LR/application.yml:35-37` |
| L10 | Ответ в `limit-results`, decidedBy `limit`, ожидание 10 с, ошибка -> повтор; решение коммитится до отправки | подтверждено | `LS/service/LimitService.java:27-46`; `decide` - `@Transactional` на другом бине (`LimitLedger.java:37`) |
| L11 | Финализация только из RESERVED; иначе и для неизвестного id - ничего | подтверждено, запуск | `LS/service/LimitLedger.java:76-85`; R10, R19 (лог `No reservation ... skipped`) |
| L12 | COMPLETED -> COMMITTED | подтверждено, запуск | `:88`; R1 |
| L13 | CANCELLED/DENIED -> RELEASED, вычитание только из периода резерва, не ниже 0; DENIED-ветка недостижима | подтверждено, запуск | `:89-94`; `LS/domain/SenderUsage.java:49-57`; R8, R9, R18 |
| L14 | Неожиданный финальный статус -> WARN, без изменений | подтверждено | `LS/service/LimitLedger.java:95-98` (срабатывает только для резерва в RESERVED) |
| L15 | `@Version` в `SenderUsage` | подтверждено | `LS/domain/SenderUsage.java:19-20` |
| L-st | Состояния резерва RESERVED/NONE/COMMITTED/RELEASED и методы | подтверждено | `LS/domain/Reservation.java:15-18,43-63` |

### 2.5 Раздел 2.4. screening-service (S1–S5)

| ID | Утверждение | Вердикт | Доказательство |
|---|---|---|---|
| S1 | Группа `screening`; только NEW, финалы логируются | подтверждено, запуск | `SS/listener/TransferEventsListener.java:21-30`; R1 (лог `screening holds no reservations`) |
| S2 | Только senderId по `blocked_senders`; `SENDER_BLOCKED`; причина наружу не уходит | подтверждено | `SS/service/ScreeningJournal.java:36-45` (причина только в лог, `:39`) |
| S3 | Журнал `screening_checks`; повторный NEW -> сохраненный результат | подтверждено | `SS/service/ScreeningJournal.java:30-34,46` |
| S4 | Ответ в `screening-results`, decidedBy `screening` | подтверждено | `SS/service/ScreeningService.java:17,27-35` |
| S5 | Стоп-лист 5, 13, 21, 34, 42 миграцией 002 в основной цепочке | подтверждено, запуск | `SR/db/changelog/002-insert-test-data.sql:5-10`; `changelog-master.yaml:4-5` |

### 2.6 Раздел 3. Сценарии

| ID | Утверждение | Вердикт | Доказательство |
|---|---|---|---|
| Sc-1 | Оба APPROVED -> COMPLETED, RESERVED -> COMMITTED | подтверждено, запуск | J-1, L12; R1 |
| Sc-2 | limit DECLINED -> CANCELLED/limit, резерв NONE | подтверждено, запуск | J-3, `Reservation.decline` (`LS/domain/Reservation.java:49-53`); R7 |
| Sc-3 | screening DECLINED -> CANCELLED/screening, RESERVED -> RELEASED, счетчики уменьшены | подтверждено, запуск | J-2, L13; R8 |
| Sc-4 | Оба DECLINED -> DENIED, `"DAILY_LIMIT_EXCEEDED; SENDER_BLOCKED"`, NONE | подтверждено, запуск | J-4; R9 |
| Sc-5 | Неожиданный ответ -> CANCELLED UNEXPECTED_RESULT; RESERVED -> RELEASED | подтверждено, запуск | J-5, L13; R19 (ветка JOIN; резерв в R19 не проверялся - инъекция без резерва) |
| Sc-6 | Разница > 10 с -> NEW, RESERVED, лимит занят до обнуления; восстановление повторным POST | подтверждено, запуск | J-8; R14 |
| Sc-7 | Kafka недоступна -> 503 через 5–10 с, NEW; повтор после восстановления -> 200 -> обработка | подтверждено, запуск | T4; R4 |
| Sc-8 | Повтор с теми же параметрами -> 200, повторная публикация, счетчики не удваиваются | подтверждено, запуск | T5, L2; R2 |
| Sc-9 | Другие параметры -> 409 | подтверждено, запуск | T6; R2 |
| Sc-10 | Невалидный запрос -> 400 problem+json, перевод не создается | подтверждено, запуск | валидация до входа в метод; R3 |
| Sc-11 | Битое сообщение -> сразу DLT без повторов, байты сохраняются; ошибка обработки -> 3 повтора по 1 с -> DLT | подтверждено, запуск | `*/config/KafkaErrorHandlingConfig.java` (`byte[]` -> raw-шаблон, `FixedBackOff(1000L, 3L)`); R11, R21 |
| Sc-12 | Смена суток/месяца между резервом и отменой: дневной не уменьшается, месячный - если месяц тот же | подтверждено, запуск | `LS/domain/SenderUsage.java:49-57`; R18 |
| Sc-hp | Порядок шагов happy path (1–6) | подтверждено, запуск | T3, L1, S1, T8, J-7, T9; R1 |

### 2.7 Раздел 4. Модель данных

| ID | Утверждение | Вердикт | Доказательство |
|---|---|---|---|
| D-1 | `transfers`: все 10 колонок, типы, NOT NULL, DEFAULT 'RUB', индексы sender_id и status, `updated_at` nullable; без `@Version` | подтверждено | `TR/db/changelog/001-create-tables.sql:5-19`; `TS/domain/TransferEntity.java` (нет `@Version`) |
| D-2 | `transfer_history`: колонки, FK, индекс, `changed_at DEFAULT NOW()`, UNIQUE(transfer_id, stage) | подтверждено | `TR/db/changelog/001-create-tables.sql:21-32` |
| D-3 | 1–4 записи истории; NEW в той же транзакции | подтверждено | T3, T10 |
| D-4 | `sender_usage`: колонки, DEFAULT, version | подтверждено | `LR/db/changelog/001-create-tables.sql:5-12` |
| D-5 | `reservations`: колонки, индекс sender_id | подтверждено | `LR/db/changelog/001-create-tables.sql:14-26` |
| D-6 | FK в limitdb нет; связь логическая; 0..1 строки счетчиков (INVALID_AMOUNT); CHECK нет | подтверждено, запуск | там же; R10 |
| D-7 | `blocked_senders`: UNIQUE + отдельный индекс, reason VARCHAR(255), 5 строк | подтверждено, запуск | `SR/db/changelog/001-create-tables.sql:5-12`; `002-insert-test-data.sql:5-10` |
| D-8 | `screening_checks`: колонки, индекс sender_id | подтверждено | `SR/db/changelog/001-create-tables.sql:14-22` |
| D-9 | `Transfer`: 9 полей, типы, порядок; `newTransfer`, `withDecision` | подтверждено | `CM:6-25` |
| D-10 | Статусы - строки, три набора значений | подтверждено | `CM:11,16`; `TransferDecisionService.java:9-10`; `Reservation.java:12-13` |
| D-11 | (проверить запуском) `createdAt` в Kafka - массив из-за собственного `ObjectMapper` spring-kafka; в REST - ISO-строка | подтверждено, запуск | Статически: сериализаторы заданы именем класса (`*R/application.yml`), `new JsonSerializer<>()` в DLT, `new JsonSerde<>(Transfer.class)` в топологии. R6: подтверждено - `"createdAt":[2026,10,5,15,13,32,938123000]` во всех трех топиках, в том числе в финале от Streams; в REST - `"2026-10-05T15:13:32.938123"`. Уточнение: нулевые секунды и наносекунды Jackson опускает, массив бывает короче (`[2026,10,5,15,16]`) |

### 2.8 Раздел 5. Kafka

| ID | Утверждение | Вердикт | Доказательство |
|---|---|---|---|
| K-1 | `transfers`, `limit-results`, `screening-results`: 3 партиции, compact, объявляют все три сервиса | подтверждено, запуск | `TS/config/TopicsConfig.java:11-24`, `LS/config/TopicsConfig.java:11-24`, `SS/config/TopicsConfig.java:11-24`; R12 |
| K-2 | `transfers.DLT`: 3 партиции, политика не задана (delete), объявляют все три | подтверждено, запуск | `*/config/KafkaErrorHandlingConfig.java` (`TopicBuilder.name("transfers.DLT").partitions(3)`); R12 |
| K-3 | `limit-results.DLT`, `screening-results.DLT` - только transfer-service | подтверждено, запуск | `TS/config/KafkaErrorHandlingConfig.java:32-40`; в limit/screening этих бинов нет; R12 |
| K-4 | Внутренние топики JOIN создает Kafka Streams | подтверждено, запуск | R12 |
| K-5 | Ключ - Long transferId, значение JSON; тип из `__TypeId__`, `value.default.type` не задан; формат сообщения не описан | подтверждено, запуск | `*R/application.yml:7-15`; поиск `value.default.type` - 0; R6 |
| K-6 | `modify-topic-configs: true`; репликация не задана | подтверждено | `*R/application.yml:3-4`; `TopicBuilder` без `.replicas()` |
| K-7 | Consumer: earliest, `ErrorHandlingDeserializer` + Long/Json, trusted `*` | подтверждено | `*R/application.yml:5-12` |
| K-8 | `DefaultErrorHandler` + `FixedBackOff(1000, 3)` -> DLPR в `<топик>.DLT`, та же партиция | подтверждено, запуск | `*/config/KafkaErrorHandlingConfig.java` (`record.topic() + ".DLT", record.partition()`); R11, R21 |
| K-9 | Не десериализуемые ключ/значение - в DLT сразу | подтверждено, запуск | R11 |
| K-10 | Успешная публикация в DLT - DEBUG; неудачная - пропуск с ERROR (`setFailIfSendResultIsError(false)`) | подтверждено, запуск | `KafkaErrorHandlingConfig.java` (`setFailIfSendResultIsError(false)`). R11/R20/R21: на уровне INFO и выше публикация в DLT не видна. Ветка "публикация в DLT не удалась" не проверялась |
| K-11 | `concurrency` не задан | подтверждено | поиск `concurrency` - 0 |
| K-12 | Streams: at-least-once, `LogAndContinueExceptionHandler`, в DLT не пишет | подтверждено, запуск | `TR/application.yml:20-26`; R20 |
| K-13 | Producer: `max.block.ms` 5000, `request.timeout.ms` 5000, `delivery.timeout.ms` 10000; `send(...).get(10, SECONDS)` | неточно, запуск | `*R/application.yml:13-19`; `TransferController.java:120`, `LimitService.java:38`, `ScreeningService.java:34`. Уточнение: это относится к `KafkaTemplate` и DLT-шаблонам; producer Kafka Streams берет настройки из `spring.kafka.streams.*` и работает с умолчаниями Kafka (в логе transfer-service: `max.block.ms = 60000`, `delivery.timeout.ms = 120000` у Streams-продюсера) |
| K-14 | Идемпотентность потребителей: limit, screening, трекер | подтверждено | L2, S3, T10 |
| K-15 | Топология: два KStream, KTable нет | подтверждено | `TS/config/DecisionTopologyConfig.java:29-38` |
| K-16 | Joiner `decide(limit, screening)` | подтверждено | `:34`; `TransferDecisionService.java:12` |
| K-17 | Окно по меткам времени записей, опоздавшие отбрасываются | подтверждено | `:35` |
| K-18 | `JsonSerde<>(Transfer.class)`, `peek` и `to("transfers")` с тем же ключом | подтверждено | `:28-38` |
| K-19 | Co-partitioning: оба входа по 3 партиции | подтверждено, запуск | K-1; R12 |

### 2.9 Раздел 6. REST API и O1–O9

| ID | Утверждение | Вердикт | Доказательство |
|---|---|---|---|
| H-1 | POST: 201/200/409/400/503/500 | подтверждено, запуск | `TS/controller/TransferController.java:53-71`; R1–R5, R15 |
| H-2 | GET `/transfers`: новые первыми, без пагинации | подтверждено, запуск | `:73-76`; R16 |
| H-3 | GET `/transfers/{id}`: `{transfer, history}`; 404 без тела; 400 при нечисловом id | подтверждено, запуск | `:78-93`; R3 |
| H-4 | Тело POST - `TransferRequest`, ответ - `TransferEntity` | подтверждено | `:54,70,106` |
| H-5 | 400/409/500/503 - `application/problem+json`; тексты 409/500/503 на русском; 500 без текста исключения | подтверждено, запуск | `:35-36,98-100`; `ApiExceptionHandler.java:15-21`; R2–R4 |
| O1 | 500 не описан | подтверждено | `TR/openapi/transfer-api.yaml:17-47,51-59,72-80` |
| O2 | 400 для GET `/{id}` не описан | подтверждено, запуск | `:72-80`; R3 |
| O3 | 405/406/415 не описаны | подтверждено | там же |
| O4 | Формат дат не указан | подтверждено | `:138-141,192-193` (`type: string` без `format`) |
| O5 | У схем ответа нет `required` | подтверждено | `:113-207` |
| O6 | `currency: null` принимается -> RUB; `nullable` нет | подтверждено, запуск | `:108-111`; `TransferRequest.java:19-21`; R5 |
| O7 | `updatedAt` без `nullable` при nullable-колонке | подтверждено | `:140-141`; `001-create-tables.sql:15` |
| O8 | (проверить запуском) `10.100` -> 400 из-за `@Digits(fraction=2)` при `multipleOf: 0.01` | подтверждено, запуск | `:107`; `TransferRequest.java:14`; R5: `10.100` -> 400 `Invalid request content.`, `10.10` -> 201. Пометку можно снять |
| O9 | Сумма строкой принимается | подтверждено, запуск | R5: `"amount":"10.00"` -> 201 (а `"10.100"` строкой -> 400) |
| O-x | "Выдуманные расхождения": `minimum/maximum/multipleOf` есть; 503 не превращается в 500; enum против String - значения совпадают | подтверждено, запуск | `:101-107,143-149`; T4/R4 |

### 2.10 Раздел 7. Аномалии

| ID | Утверждение | Вердикт | Доказательство |
|---|---|---|---|
| A1 | Окно 10 с без grace, inner join, нет таймаута/компенсации -> NEW, RESERVED, лимит занят до обнуления; решение коммитится до отправки; восстановление повторным POST | подтверждено, запуск | `DecisionTopologyConfig.java:32-36`; `LimitService.java:27-30`; планировщика нет (раздел 8); R14 |
| A2 | Нет outbox: коммит до публикации -> 503 и NEW | подтверждено, запуск | `TransferController.java:62,69`; R4 |
| A3 | `transfers` compact; NEW и финал с одним ключом -> после компакции остается финал | подтверждено | `TopicsConfig.java:13`; `TransferController.java:120` и `DecisionTopologyConfig.java:38` пишут с ключом transferId. Уточнение: компакция идет только по закрытым сегментам (по умолчанию `segment.ms` = 7 суток), поэтому на демо-стенде NEW обычно еще виден |
| A4 | Лимиты без учета валюты | подтверждено | `LimitLedger.java:61-64` (currency не используется) |
| A5 | Границы суток/месяца - TZ сервера, день - по моменту обработки; метки без зоны | подтверждено, запуск | `LimitLedger.java:45,91` (`LocalDate.now()`); `LocalDateTime` везде; R22 (TZ контейнеров) |
| B1 | DLT никто не читает; последствия по местам ухода; уход молчаливый; неудачная публикация - потеря | подтверждено, запуск | нет `@KafkaListener` на `*.DLT`; R11, R21 |
| B2 | Streams "молча" пропускает не десериализованную запись -> нет JOIN -> NEW | неточно, запуск | `TR/application.yml:25`. "Молча" неточно: R20 - два WARN (`LogAndContinueExceptionHandler: Exception caught during Deserialization …`, `RecordDeserializer: Skipping record …`), в DLT Streams не пишет; противоречит формулировке 5.2 "с логированием" |
| B3 | Только отправитель; причина не передается; изменение стоп-листа не влияет на проверенный id | подтверждено | `ScreeningJournal.java:30-45` |
| B4 | Нет sender ≠ recipient; id - любые Long | подтверждено | `TransferRequest.java:11-13` (только `@NotNull`) |
| B5 | Лимиты одни на всех, нет лимита на операцию | подтверждено | `LR/application.yml:35-37` |
| B6 | Повторный POST по NEW при недоступной Kafka -> 503 | подтверждено, запуск | `TransferController.java:102-104`; R4 |
| B7 | Повторный POST в NEW может дать несколько финалов; безопасно | неточно, запуск | Суть верна, масштаб занижен. R15: 10 POST с одним id, пока перевод NEW -> 10 публикаций NEW -> по 10 ответов limit и screening -> stream-stream JOIN соединил все пары в окне: **100** финалов COMPLETED в `transfers`. Применен 1, 99 отсечены трекером и limit-service (`Duplicate final status`, `nothing to settle`) |
| B8 | Сортировка по этапу, `changedAt` LIMIT/SCREENING может быть позже FINAL | подтверждено, запуск | `TransferController.java:84-87`. R15: SCREENING записан раньше LIMIT (`15:20:10.758` < `.771`), GET все равно отдает LIMIT первым. Случай "FINAL раньше LIMIT/SCREENING" на запуске не воспроизвелся (статически возможен: один поток трекера читает 9 партиций трех топиков без упорядочивания между ними) |
| B9 | GET `/transfers` без пагинации | подтверждено | `TransferController.java:73-76` |
| B10 | Нет аутентификации; `trusted.packages: "*"`; пароли в yml; `show-sql: true`; логирование сумм и причин блокировки | подтверждено | `*R/application.yml`; `LimitLedger.java:58-59`; `ScreeningJournal.java:39` |
| B11 | Статусы и decidedBy - строки; регистр -> UNEXPECTED_RESULT | подтверждено, запуск | `TransferDecisionService.java:13-16` (`equals`); R19 |
| B12 | Ключ - transferId -> переводы отправителя в разных партициях; корректность на `@Version` и повторах | подтверждено | `TransferController.java:120`; `SenderUsage.java:19-20` |
| B13 | Потерянное обновление в transferdb (латентно) | подтверждено | `TransferEntity` без `@Version`; `TransferTrackingService.java:65-66`; один поток (K-11) |
| B14 | Идемпотентность limit/screening только по transferId | подтверждено, запуск | `LimitLedger.java:39-43`; `ScreeningJournal.java:30-34`; R23 |
| B15 | После 503 клиент может создать дубль | подтверждено | T4 (таймаут `get` не отменяет отправку) |
| B16 | GET без транзакции | подтверждено | T12 |
| B17 | Финал в DLT у трекера -> резерв урегулирован, перевод NEW | подтверждено | следует из B1 и независимых групп `limit` / `transfer-tracker` |
| C1 | UNIQUE + отдельный индекс | подтверждено | `SR/db/changelog/001-create-tables.sql:7,12` |
| C2 | `VARCHAR(10)` при 3 символах; без ISO 4217 | подтверждено | `TR/.../001-create-tables.sql:10`; `TransferRequest.java:15` |
| C3 | `INVALID_AMOUNT` недостижим через REST; NEW в обход REST обрабатывается полностью | подтверждено, запуск | `@Positive` (`TransferRequest.java:14`); R10 |
| C4 | Репликация не задана | подтверждено | K-6 |
| C5 | Нет health-check / actuator | подтверждено | поиск `actuator` - 0; у сервисов нет `healthcheck` в `DC` |
| C6 | OpenAPI без описаний поведения, не подключен к коду | подтверждено | нет springdoc/генератора; файл лежит в `resources/openapi`, не в `static` |
| C7 | DLT-фабрики не бины; state-dir в `/tmp` без тома | подтверждено | `KafkaErrorHandlingConfig.java` (`new DefaultKafkaProducerFactory<>` внутри метода); `TR/application.yml:26` |
| C8 | Стоп-лист в миграции; ошибка валидации без указания поля | подтверждено, запуск | `002-insert-test-data.sql`; R3 |

### 2.11 Раздел 8. Чего в системе НЕТ

Поиск по `experiment/code.txt` (`grep -c`): `@PutMapping|@DeleteMapping|@PatchMapping` - 0; `/generate`, `cancel` - 0; `@Scheduled|EnableScheduling|TaskScheduler|ScheduledExecutor|Timer` - 0; `outbox|saga|XA|ChainedTransactionManager|KafkaTransactionManager|transaction-id-prefix|transactional.id|processing.guarantee|exactly` - 0; `KTable|GlobalKTable|QueryableStoreType|Materialized` - 0; `exchange|convert` - 0; `security|auth|role|RateLimit` - 0; `spring-boot-starter-test|junit|@Test` - 0, каталогов `src/test` нет; `actuator` - 0. Маркеры piomin (`Order`, `Customer`, `Product`, `payment`, `stock`, `productId`, `productCount`, `price`, `amountAvailable`, `amountReserved`, `PAYMENT`, `STOCK`, `/orders`, `Datafaker`, `OrderManageService`, `base-domain`, `ACCEPT`, `REJECT`, `CONFIRMED`, `ROLLBACK`, `REJECTED`) - 0; единственное `source` - `sourceEncoding` в `pom.xml`.

| ID | Утверждение | Вердикт | Доказательство |
|---|---|---|---|
| N-1 | Эндпоинтов только три | подтверждено | `TransferController.java:53,73,78` - единственные mapping-аннотации |
| N-2 | HTTP API у limit/screening нет | подтверждено | нет `spring-boot-starter-web` в их `pom.xml` |
| N-3 | Планировщика/таймаута нет | подтверждено | поиск - 0 |
| N-4 | Outbox, оркестратора, распределенных транзакций нет | подтверждено | поиск - 0 |
| N-5 | KTable/GlobalKTable/IQ нет | подтверждено | поиск - 0 |
| N-6 | Проверки получателя, конвертации, индивидуальных лимитов, лимита на операцию нет | подтверждено | `recipientId` только переносится; лимиты - два `@Value` |
| N-7 | Аутентификации, ролей, rate limiting нет | подтверждено | поиск - 0 |
| N-8 | Тестов нет | подтверждено | поиск - 0 |
| N-9 | Маркеры piomin отсутствуют в коде | подтверждено | поиск - 0 |
| N-10 | "Не маркеры" существуют: `LimitService.onNewTransfer/onFinalStatus`, `ScreeningService.onNewTransfer`, `TransferEventsListener.onEvent`, `LimitLedger.decide/settle`, `ScreeningJournal.check`, `SenderUsage.reserve/release/lazyReset` | подтверждено | `LimitService.java:27,32`; `ScreeningService.java:27`; `LS/listener/TransferEventsListener.java:22`, `SS/listener/TransferEventsListener.java:22`; `LimitLedger.java:38,75`; `ScreeningJournal.java:29`; `SenderUsage.java:33,44,49` |
| N-11 | Таблица "частых искажений" (14 строк): правая колонка "Правда" | подтверждено | каждая строка совпадает с S1, S2, D-10/T8, J-3/J-4, L11/L13, L6, L2, T4, K-1, P-12, K-3, K-9, L-st, N-10, P-17. Уточнение к строке про `onEvent`: в transfer-service класс называется `TransferEventListener` (без "s"), `onEvent` есть и у него (`TS/listener/TransferEventListener.java:26`) |

### 2.12 Раздел 9. Диаграммы

| ID | Утверждение | Вердикт | Доказательство |
|---|---|---|---|
| G-0 | "Все диаграммы проверены рендером mermaid-cli 11" | не проверялось | Рендер в этой проверке не запускался (вне задания) |
| G-1 | 9.1: состав пяти диаграмм (участники, топики, 3 БД, FK только `transfer_history → transfers`, состояния перевода) | подтверждено | P-12, D-2, D-6, J-1…J-8 |
| G-2 | 9.2 happy path: участники; INSERT и публикация до 201; `par`; `onNewTransfer → decide()`, `onNewTransfer → check()`, `onFinalStatus → settle()`; трекер пишет LIMIT/SCREENING и FINAL; screening только логирует | подтверждено | `TransferController.java:62-70`; `LimitService.java:27-34`; `LimitLedger.java:38,75`; `ScreeningService.java:27-29`; `ScreeningJournal.java:29`; `SS/listener/TransferEventsListener.java:24-29` |
| G-3 | 9.3 сценарий отказа: шаги и методы | неточно | Методы и связи верны (`LimitLedger.java:89-94`, `SenderUsage.java:49-57`, `TransferDecisionService.java:25-26`). Неточности: (1) нет стрелки публикации NEW (`TS->>K`) - `K->>L` появляется "из ниоткуда", хотя 9.1 требует публикацию до 201; (2) `Note`: "резерв RESERVED до обнуления периода" - состояние резерва остается RESERVED навсегда, до обнуления периода занята только сумма лимита (так верно сказано в A1 и сценарии 6) |
| G-4 | 9.4 ER transferdb: поля, типы, FK, UNIQUE, `\|\|--\|{` 1..4 | подтверждено | D-1, D-2 |
| G-5 | 9.4 ER limitdb: поля; `sender_usage \|o..\|{ reservations` (0..1 счетчиков на резерв, ≥ 1 резерв на счетчик) | подтверждено | D-4…D-6; `sender_usage` создается только в `decide` вместе с резервом (`LimitLedger.java:54-71`) |
| G-6 | 9.4 ER screeningdb: поля; `blocked_senders \|o..o{ screening_checks` | подтверждено | D-7, D-8 |
| G-7 | 9.4 ER сообщения и логические связи между БД | подтверждено | D-9; `LimitLedger.java:49,54`; `ScreeningJournal.java:40-44` |
| G-8 | 9.4 "Засчитывать как верное: `\|\|..\|{` … Это неточность (−0,5)" | неточно | Не факт о коде, а противоречие в правиле: "засчитывать как верное" и "неточность −0,5" одновременно. Нужна одна формулировка |
| G-9 | 9.5 состояния перевода и резерва; RESERVED -> RELEASED при DENIED недостижим | подтверждено | J-1…J-5, L-st, L13 |

### 2.13 Факты в разделах 10–11 (чек-листы)

| ID | Утверждение | Вердикт | Доказательство |
|---|---|---|---|
| X-1 | K5-M1(b): `reservations.reason VARCHAR(50)` | подтверждено | `LR/db/changelog/001-create-tables.sql:19` |
| X-2 | K5-M3(b), K5-S4: начальные 50 000 / 200 000; фактический максимум операции 50 000.00 | подтверждено | `LR/application.yml:35-37`; L7 |
| X-3 | K5-M4(b): у limit-service нет HTTP-слоя | подтверждено | N-2 |
| X-4 | K1-M3: 4 модуля; K3-M2: 6 таблиц в 3 БД; K3-M3: 9 полей | подтверждено | `pom.xml:17-22`; D-1…D-8; D-9 |
| X-5 | K4-S6: кардинальность 0..1 из-за INVALID_AMOUNT | подтверждено, запуск | L3; R10 |

---

## 3. Проверки на запущенной системе

Условные обозначения команд (полный текст - в приложении А):
- `post '<json>'` - `curl -X POST localhost:8080/transfers`, печатает код, время, `Content-Type` и тело;
- `get <путь>` - `curl localhost:8080/transfers<путь>`;
- `pt|pl|ps_ "<sql>"` - `psql` в контейнерах transfer-db, limit-db, screening-db;
- `kp <топик> <ключ Long> <заголовки> <значение>` - запись в Kafka с ключом `Long` и заголовками. Понадобилась потому, что `kafka-console-producer` из cp-kafka 7.6.0 игнорирует `key.serializer` и пишет ключ строкой: проверено, `LongDeserializer` падает с "Size of data … is not 8";
- чтение топиков - `kafka-console-consumer … --property print.headers=true --property print.key=true --property print.timestamp=true --property key.deserializer=…LongDeserializer` внутри контейнера брокера.

Время в логах и БД - UTC.

| # | Ожидание по эталону | Фактический результат | Вердикт | Команда и вывод (кратко) |
|---|---|---|---|---|
| R1 | 201, итог COMPLETED; история NEW -> LIMIT -> SCREENING -> FINAL (T3, T12, B8) | 201 за 0,26 с, в теле `status: NEW`. Через ~0,5 с - COMPLETED. История идет по этапам, `changedAt` монотонны: NEW `15:13:32.938123` -> LIMIT `33.218001` -> SCREENING `33.265434` -> FINAL `33.419706`; `updatedAt` = `changedAt` у FINAL. Метки с 6 знаками (T13). Резерв COMMITTED, `sender_usage` 1000.00/1000.00. Трекер пишет полный UPDATE (T8). screening только логирует финал | подтверждено | `post '{"id":1001,"senderId":101,"recipientId":201,"amount":1000.00,"currency":"RUB"}'` -> `HTTP 201 time=0.256225s`; `get /1001` -> `"history":[{"stage":"NEW"…},{"stage":"LIMIT","status":"APPROVED","decidedBy":"limit"…},{"stage":"SCREENING"…},{"stage":"FINAL","status":"COMPLETED"…}]`; лог: `Hibernate: update transfers set amount=?,created_at=?,currency=?,decided_by=?,message_info=?,recipient_id=?,sender_id=?,status=?,updated_at=? where id=?`; `Final status COMPLETED for transfer 1001: screening holds no reservations, nothing to do` |
| R2 | Повтор с теми же параметрами -> 200; с другими -> 409 problem+json (T5, T6) | Тот же перевод с `amount: 1000` и без `currency` -> **200**, вернулся существующий (COMPLETED; повторной публикации нет, перевод уже не NEW). `amount: 1500.00` -> **409**, `currency: "USD"` -> **409**, тип `application/problem+json`, текст по-русски. Счетчики не изменились | подтверждено | `HTTP 200`; `HTTP 409` + `Content-Type: application/problem+json` + `{"type":"about:blank","title":"Conflict","status":409,"detail":"Перевод с id=1001 уже существует с другими параметрами","instance":"/transfers"}`; `pl` -> `daily_used 1000.00` |
| R3 | Невалидный POST -> 400; GET `/transfers/abc` -> 400; несуществующий id -> 404 без тела (T2, O2) | Нет `amount`, `amount: -5`, `currency: "rub"` -> 400 `"detail":"Invalid request content."` (поле не указано, C8). Битый JSON -> 400 `"Failed to read request"`. `/transfers/abc` -> 400 problem+json `"Failed to convert 'id' with value: 'abc'"`. `/transfers/999999` -> 404, `Content-Length: 0`. Строки в transferdb не созданы | подтверждено | `post '{"id":1002,"senderId":101,"recipientId":201}'` -> `HTTP 400`, `application/problem+json`; `get /abc` -> `HTTP 400`; `get /999999` -> `HTTP 404`, `Content-Length: 0`; `pt "select id from transfers where id between 1002 and 1005"` -> `(0 rows)` |
| R4 | Kafka недоступна -> 503 (не 500) через 5–10 с, NEW в БД; после восстановления повтор -> 200 -> обработка (T4, A2, сц. 7) | `docker compose stop broker`. POST -> **503** за **10,05 с**, problem+json с русским текстом. В transferdb перевод NEW и история NEW. `handleUnexpected` не вызывался (нет `Unhandled exception`). Причина в логе - `TimeoutException: Expiring 1 record(s) … 10001 ms has passed since batch creation` (`delivery.timeout.ms`). Повтор при лежащем брокере -> 503 за 10,03 с (B6). После `start broker` повтор -> **200** (в теле `status: NEW`) -> COMPLETED, резерв COMMITTED. В `transfers` для 4001 только один NEW (от повтора) и финал: неудачные отправки до брокера не дошли | подтверждено | `post '{"id":4001,…,"amount":250.00}'` -> `HTTP 503 time=10.050430s` / `{"title":"Service Unavailable","status":503,"detail":"Kafka недоступна, перевод сохранён — повторите запрос с тем же id"}`; `pt` -> `4001 \| NEW`; лог `ERROR … LoggingProducerListener : Exception thrown when sending a message with key='4001' and payload='Transfer[id=4001, …, amount=250.00, …]'`; после старта: `HTTP 200`, `get /4001` -> `COMPLETED` |
| R4b | (доп.) нижняя граница "5 с" в T4 | Брокер на паузе (`docker compose pause broker`), transfer-service перезапущен, поэтому метаданных у продюсера нет. POST -> **503 за 5,12 с** (`max.block.ms`). Сервис при этом стартовал за 42 с: `KafkaAdmin: Could not configure topics` после 30 с ожидания. Если же контейнер брокера **остановлен** (имя `broker` не резолвится), transfer-service не стартует вовсе: `No resolvable bootstrap urls given in bootstrap.servers` -> `Could not start stream` -> цикл рестартов (раздел 5, F-8) | подтверждено | `post '{"id":4002,…}'` -> `HTTP 503 time=5.115488s`; после `unpause` повтор -> `HTTP 200` -> COMPLETED |
| R5 | `amount: 10.100` - 400 или 201 (O8); строка `"10.00"` (O9); `currency: null` -> RUB (O6) | `10.100` -> **400** `Invalid request content.`; контроль `10.10` -> 201. `"10.00"` строкой -> **201**, `amount: 10.00`. `currency: null` -> **201**, `currency: "RUB"`. Дополнительно: `"10.100"` строкой -> 400 | подтверждено | `post '{"id":1010,"senderId":102,"recipientId":201,"amount":10.100}'` -> `HTTP 400`; `post '{…"amount":"10.00"}'` -> `HTTP 201 … "amount":10.00`; `post '{…"currency":null}'` -> `HTTP 201 … "currency":"RUB"` |
| R6 | `createdAt` - массив или ISO-строка; есть ли `__TypeId__` (4.4, K3-S9) | **Массив** во всех трех топиках, включая финал от Kafka Streams. `__TypeId__:com.example.common.Transfer` у всех сообщений, в том числе у финала из Streams. Длина массива переменная: нулевые секунды и наносекунды опускаются (R10: `[2026,10,5,15,16]`). Метка времени финала совпала с меткой ответа screening, то есть с более поздней из пары | подтверждено (пометку снять) | `kafka-console-consumer --topic transfers …` -> `CreateTime:1791213212972 Partition:2 __TypeId__:com.example.common.Transfer 1001 {"id":1001,…,"status":"NEW",…,"createdAt":[2026,10,5,15,13,32,938123000]}` и `CreateTime:1791213213245 … "status":"COMPLETED" …`; `screening-results` -> `CreateTime:1791213213245 …` |
| R7 | Ровно 50 000.00 -> APPROVED; 50 000.01 -> DAILY; превышены оба -> DAILY (L6–L8) | 50 000.00 (новый отправитель 301) -> COMPLETED, резерв COMMITTED, `daily_used` 50000.00. 50 000.01 (302) -> CANCELLED / `limit` / `DAILY_LIMIT_EXCEEDED`, резерв DECLINED/NONE, **строка `sender_usage` 302 создана с нулями** (L6). 250 000.00 (303, превышены оба) -> `DAILY_LIMIT_EXCEEDED` | подтверждено | `pt` -> `1101 \| 50000.00 \| COMPLETED`, `1102 \| 50000.01 \| CANCELLED \| limit \| DAILY_LIMIT_EXCEEDED`, `1103 \| 250000.00 \| CANCELLED \| limit \| DAILY_LIMIT_EXCEEDED`; `pl` -> `302 \| 0.00 \| 0.00` |
| R8 | Отправитель 5, сумма в лимите -> CANCELLED / screening; резерв RELEASED, счетчики уменьшены (сц. 3, L13) | CANCELLED / `screening` / `SENDER_BLOCKED`. Резерв APPROVED -> **RELEASED**, `sender_usage` 5 = 0.00/0.00 (version 1). Перед решением в логе `daily=0, monthly=0` | подтверждено | `post '{"id":1201,"senderId":5,"recipientId":201,"amount":1000.00}'`; `pl` -> `1201 \| 1000.00 \| APPROVED \| \| RELEASED`; лог `Reservation for transfer 1201 settled: RELEASED` |
| R9 | Отправитель 5, > 50 000 -> DENIED, `DAILY_LIMIT_EXCEEDED; SENDER_BLOCKED`, резерв NONE (сц. 4, L13) | DENIED, `decided_by` NULL, `DAILY_LIMIT_EXCEEDED; SENDER_BLOCKED`. Резерв DECLINED/**NONE**, финализация не выполнялась | подтверждено | `pt` -> `1202 \| 60000.00 \| DENIED \| \| DAILY_LIMIT_EXCEEDED; SENDER_BLOCKED`; лог `Transfer 1202 is in state NONE, nothing to settle` |
| R10 | NEW с `amount: 0` в обход REST -> DECLINED `INVALID_AMOUNT`; резерв есть, `sender_usage` нет; трекер "Unknown transfer … skipped" (L3, C3) | Резерв: `5001 \| 501 \| 0.00 \| DECLINED \| INVALID_AMOUNT \| NONE`. Строк `sender_usage` для 501: **0**. screening одобрил, JOIN дал CANCELLED. Трекер трижды WARN: `Unknown transfer 5001 in LIMIT result, skipped`, `… in SCREENING result, skipped`, `… in final status, skipped`. В transferdb перевода нет | подтверждено | `kp transfers 5001 '__TypeId__=com.example.common.Transfer' '{"id":5001,"senderId":501,"recipientId":201,"amount":0,"currency":"RUB","status":"NEW","decidedBy":null,"messageInfo":null,"createdAt":[2026,10,5,15,16,0,0]}'`; `pl` -> `usage_rows_501 = 0` |
| R11 | Битое сообщение в `transfers`: в DLT сразу или после 3 повторов; сколько копий; есть ли ERROR/WARN (сц. 11, B1) | **Сразу, без повторов.** Отправлено в `…416.898`, в DLT `…417.504 / .505 / .522` (≈ 0,6 с). **Три копии** - по одной на группу (`kafka_dlt-original-consumer-group`: `limit`, `screening`, `transfer-tracker`). Партиция та же (2), ключ 6001, значение - **исходные байты** `not-a-json {`. Исключение `DeserializationException` <- `JsonParseException`. **WARN/ERROR в логах: 0.** При первом уходе в DLT каждый сервис пишет только INFO о ленивом создании DLT-продюсера (`ProducerConfig values`, `Instantiated an idempotent producer`); второе битое сообщение (6003) не оставило в логах ни одной строки уровня INFO и выше | подтверждено | `kp transfers 6001 '__TypeId__=com.example.common.Transfer' 'not-a-json {'`; `kafka-console-consumer --topic transfers.DLT …` -> `CreateTime:1791213417504 Partition:2 kafka_dlt-exception-fqcn:org.springframework.kafka.support.serializer.DeserializationException … kafka_dlt-original-consumer-group:limit 6001 not-a-json {` (и еще две строки - `screening`, `transfer-tracker`) |
| R12 | Партиции и `cleanup.policy` бизнес-топиков и всех `*.DLT` (5.1) | `transfers`, `limit-results`, `screening-results`: 3 партиции, RF 1, **compact** (DYNAMIC_TOPIC_CONFIG). `transfers.DLT`, `limit-results.DLT`, `screening-results.DLT`: 3 партиции, RF 1, своих настроек нет, `cleanup.policy=delete` из DEFAULT_CONFIG. Внутренние топики Streams: `transfer-service-KSTREAM-JOINTHIS-0000000004-store-changelog` и `…-JOINOTHER-0000000005-store-changelog` (3 партиции, `delete`, `retention.ms=86420000`). `segment.ms` у `transfers` = 604800000 (7 суток) | подтверждено | `kafka-topics --describe` -> `Topic: transfers PartitionCount: 3 ReplicationFactor: 1 Configs: cleanup.policy=compact`; `Topic: transfers.DLT PartitionCount: 3 ReplicationFactor: 1 Configs: `; `kafka-configs --describe --all` -> `transfers.DLT cleanup.policy=delete … {DEFAULT_CONFIG:log.cleanup.policy…}` |
| R13 | Группы `limit`, `screening`, `transfer-tracker`, `transfer-service`, и что читает `transfer-service` (1, 5.1) | `limit` и `screening` -> `transfers` 0–2 (по одному клиенту). `transfer-tracker` -> `transfers`, `limit-results`, `screening-results` (9 партиций, один клиент). **`transfer-service` -> только `limit-results` и `screening-results`** (клиент `…-StreamThread-1-consumer`). В логе `application.id = transfer-service`, `processing.guarantee = at_least_once`. Остальные группы в списке (`console-consumer-*`) - от самой проверки | подтверждено | `kafka-consumer-groups --list` -> `screening, limit, transfer-service, transfer-tracker, console-consumer-…`; `--describe --group transfer-service` -> `transfer-service limit-results 0 …`, `transfer-service screening-results 0 …` |
| R14 | Пауза screening ~20 с -> перевод NEW, резерв RESERVED; повторный POST -> финал (A1, сц. 6) | `pause screening-service` в 15:21:22, POST 1401 (и 1801 для R18). Через 20 с оба NEW, в истории NEW и LIMIT, резервы RESERVED. `unpause` в 15:21:43: ответы screening появились через **20,5 с** после ответов limit (`CreateTime` 1791213682635 -> 1791213703155), JOIN ничего не выдал (нет `Decision for transfer 1401`). Через 23 с перевод все еще NEW, в истории уже и SCREENING, резерв RESERVED. Повторный POST -> **200** (`status: NEW`) -> limit: `Duplicate NEW … returning stored decision`, трекер: `Duplicate LIMIT/SCREENING result … skipped` -> **COMPLETED**, резерв COMMITTED | подтверждено | `docker compose pause screening-service`; `post '{"id":1401,"senderId":1401,"recipientId":201,"amount":500.00}'`; `pt` -> `1401 \| NEW`; `pl` -> `1401 \| RESERVED`; после повтора: `pt` -> `1401 \| COMPLETED`, `pl` -> `1401 \| COMMITTED`. Воспроизвелось с первой попытки |
| R15 | (доп.) Гонка двух POST с одним id -> ветка дубликата (T7); последствия повторов в NEW (B7) | 10 параллельных POST с id 8001 -> **1 × 201, 9 × 200**, 500 нет. Все 9 "проигравших" прошли через нарушение PK: в логе 9 пар WARN+ERROR от Hibernate `duplicate key`. Так как перевод был еще NEW, **каждый** опубликовал NEW заново: 10 NEW -> 10 ответов limit и 10 ответов screening -> **100** решений JOIN -> **100 сообщений COMPLETED** в `transfers`. Применено 1, отсечено 99 (`Duplicate final status`), у limit 1 COMMITTED и 99 `nothing to settle`. `daily_used` = 11.00, не удвоился | подтверждено (B7 - неточно) | `for i in $(seq 1 10); do curl … -d '{"id":8001,…,"amount":11.00}' & done` -> `9 200`, `1 201`; подсчет: `Published NEW for transfer 8001: 10`, `Decision for transfer 8001: 100`, в `transfers` для ключа 8001 - `100 "status":"COMPLETED"`, `10 "status":"NEW"` |
| R16 | (доп.) GET `/transfers` - новые первыми, без пагинации | 10 записей, первая - 8001 (`15:20:10.664`), далее 1202, 1201, 1103… по убыванию `createdAt`. Параметров пагинации нет | подтверждено | `curl localhost:8080/transfers` |
| R17 | (доп.) MONTHLY, ленивое обнуление дня и месяца (L5, L6) | Состояние подготовлено через `UPDATE sender_usage`. 901: monthly 190 000, POST 20 000 -> CANCELLED / `limit` / `MONTHLY_LIMIT_EXCEEDED`. 902: daily 45 000, `last_daily_reset` = вчера, POST 10 000 -> в логе `daily=0, monthly=100.00` -> COMPLETED, итог 10000.00/10100.00 (месячный сохранен). 903: daily 49 000, monthly 199 000, оба сброса 2026-09-30, POST 5000 -> `daily=0, monthly=0` -> COMPLETED, итог 5000/5000 | подтверждено | `pl "update sender_usage set monthly_used=190000 where sender_id=901; …"`; `pt` -> `9911 \| CANCELLED \| limit \| MONTHLY_LIMIT_EXCEEDED`, `9912 \| COMPLETED`, `9913 \| COMPLETED` |
| R18 | (доп.) Возврат в другой день: дневной не уменьшается, месячный - если месяц тот же (L13, сц. 12) | Отправитель 13 (в стоп-листе) застрял, как в R14: RESERVED, `sender_usage` 700/700. `UPDATE reservations SET reserved_on = current_date-1`, повторный POST -> CANCELLED / `screening` / `SENDER_BLOCKED`, резерв RELEASED. Итог: **daily 700.00 (не уменьшен), monthly 0.00 (уменьшен)** | подтверждено | `pl "update reservations set reserved_on=current_date-1 where transfer_id=1801"`; `pl` -> `13 \| 700.00 \| 0.00` |
| R19 | (доп.) Ветка UNEXPECTED_RESULT (2.2, B11) | В `limit-results` записан `status: "approved"` (нижний регистр), в `screening-results` - `"APPROVED"` -> финал `CANCELLED`, `decidedBy: null`, `"UNEXPECTED_RESULT: limit=approved, screening=APPROVED"`. limit-service: WARN `No reservation for transfer 7001, final status CANCELLED skipped` (L11) | подтверждено | `kp limit-results 7001 … '{…"status":"approved"…}'`, `kp screening-results 7001 …`; `transfers` -> `7001 {…"status":"CANCELLED","decidedBy":null,"messageInfo":"UNEXPECTED_RESULT: limit=approved, screening=APPROVED"…}` |
| R20 | (доп.) Битая запись в `limit-results`: Streams пропускает (B2, 5.2), трекер - в `limit-results.DLT` (5.1) | Streams: **два WARN** (`LogAndContinueExceptionHandler : Exception caught during Deserialization, taskId: 0_0, topic: limit-results…` и `RecordDeserializer : … Skipping record due to deserialization error`), в DLT Streams не пишет. Трекер положил запись в `limit-results.DLT` (ключ 7002, значение `garbage`) без единого WARN/ERROR | подтверждено (B2 - неточно) | `kp limit-results 7002 '__TypeId__=…' 'garbage'`; `kafka-console-consumer --topic limit-results.DLT` -> `CreateTime:1791213560983 7002 garbage` |
| R21 | (доп.) Ошибка обработки -> 3 повтора по 1 с -> DLT (сц. 11, 5.2) | NEW с `senderId: null`. limit-service: 4 попытки (`Received NEW for transfer 6002` в 38.666, 39.704, 40.728, 41.767, между ними INFO `Seeking to offset 5`), запись в DLT в 42.287: `ListenerExecutionFailedException` <- `InvalidDataAccessApiUsageException: The given id must not be null`. screening - то же с `DataIntegrityViolationException` (NOT NULL `screening_checks.sender_id`); на каждой попытке Hibernate пишет WARN+ERROR. Сам spring-kafka не пишет WARN/ERROR ни о повторах, ни о DLT. В DLT в этом случае **заново сериализованный JSON** с `__TypeId__`, а не исходные байты | подтверждено | `kp transfers 6002 '__TypeId__=…' '{"id":6002,"senderId":null,…}'`; в DLT: `… kafka_dlt-original-consumer-group:limit,__TypeId__:com.example.common.Transfer 6002 {"id":6002,"senderId":null,…,"decidedBy":null,"messageInfo":null,"createdAt":[2026,10,5,15,18]}` |
| R22 | (доп.) Часовой пояс сервера (A5) | Во всех контейнерах UTC, переменная `TZ` не задана. Метки в логах - `…Z`, `reserved_on` и `last_daily_reset` - дата по UTC. Сутки лимита сменяются в 03:00 по Москве | подтверждено | `docker compose exec transfer-service sh -c 'date; echo TZ=$TZ'` -> `Mon Oct  5 03:13:10 PM UTC 2026`, `TZ=` |
| R23 | (доп.) Повторный NEW с тем же id и другими суммой/отправителем -> сохраненное решение без сверки (L2, B14); NEW в обход REST обрабатывается полностью (C3) | Повторный NEW 5001 с `amount: 100`, `senderId: 777` -> `Duplicate NEW for transfer 5001, returning stored decision` и `… returning stored check`; резерв не изменился. **Но ответ в `limit-results` собран из нового сообщения:** `{"id":5001,"senderId":777,"amount":100,…,"status":"DECLINED","messageInfo":"INVALID_AMOUNT"}`. C3: NEW 5002 (отправитель 502, 300) в обход REST -> резерв RESERVED -> COMMITTED, `daily_used` 300.00, при этом в transferdb перевода нет | подтверждено | `kp transfers 5001 … '{"id":5001,"senderId":777,…,"amount":100,…}'`; `kafka-console-consumer --topic limit-results` -> две записи 5001 с разными `senderId` и `amount` и одинаковым `INVALID_AMOUNT` |

**Что не проверялось на запуске:** ветка "публикация в DLT не удалась" (K-10); случай, когда FINAL попадает в историю раньше LIMIT или SCREENING (B8; наблюдался только перевернутый порядок LIMIT и SCREENING); компакция `transfers` (A3), потому что сегменты закрываются раз в 7 суток; поведение limit- и screening-service при старте без брокера.


---

## 4. Правки по итогам проверки (внесены в эталон)

Все правки из этой таблицы внесены в опубликованный эталон. Они отсортированы по влиянию на оценку.

| # | Раздел / ID | Как было | Как стало | Обоснование |
|---|---|---|---|---|
| E1 | 4.4 (пометка "проверить запуском"), K3-S9 | "`WRITE_DATES_AS_TIMESTAMPS` в нем, по всей видимости, не выключен… **Проверить запуском**, до этого не штрафовать ни за массив, ни за строку"; K3-S9 "(проверить запуском) Сериализация `createdAt` в Kafka массивом" | Снять пометку: "`createdAt` в Kafka - массив `[год, месяц, день, час, мин, сек, нано]`; нулевые секунды и наносекунды Jackson опускает, поэтому длина 5–7 элементов (`[2026,10,5,15,16]`). Подтверждено запуском". Утверждение "в Kafka ISO-строка" считать ошибкой по 0.3 (категорию выбирает автор) | R6: массив во всех трех топиках, включая финал от Streams; R10/R21: укороченные массивы |
| E2 | 6.1 O8 | "правдоподобно, проверить запуском" | "подтверждено запуском: `10.100` -> 400 `Invalid request content.`; строкой `"10.100"` тоже 400" | R5 |
| E3 | 7, B7 | "Повторный POST в NEW может дать несколько финалов; все одинаковы, безопасно…" | "Каждый повторный POST, пока перевод NEW, заново публикует NEW; limit и screening отвечают сохраненными решениями, а stream-stream JOIN соединяет **все** пары ответов в окне 10 с: N публикаций дают до N² финалов (10 POST -> 100 COMPLETED). Все одинаковы; применяется первый, остальные отсекает идемпотентность трекера и limit-service. Риск - квадратичный рост нагрузки на `transfers` и потребителей" | R15. Формулировка "несколько" занижает масштаб; модель, которая напишет "N×M финалов", не должна получить штраф за "выдумку" |
| E4 | 7, B2 | "Kafka Streams **молча** пропускает не десериализованную запись" | "Kafka Streams пропускает не десериализованную запись **с WARN в логе** (`LogAndContinueExceptionHandler`), без DLT и без ERROR -> JOIN не срабатывает -> перевод NEW" | R20: два WARN. Сейчас B2 противоречит самому эталону (5.2: "пропускается с логированием") |
| E5 | 2.3, L2 | "…сумма и отправитель не сравниваются **(B15)**" | "…не сравниваются **(B14)**. Ответ при этом собирается из нового сообщения (`transfer.withDecision(...)`), поэтому в `limit-results` уходит новая сумма или отправитель вместе со старым решением" | B15 - "дубль после 503"; про идемпотентность только по transferId говорит B14. Вторая часть - R23 |
| E6 | 5.2, "Producer" | "**Producer:** `max.block.ms` 5000, `request.timeout.ms` 5000, `delivery.timeout.ms` 10000; отправка синхронная…" | "**Producer `KafkaTemplate`** (REST, ответы limit и screening, DLT-шаблоны): … Продюсер Kafka Streams этих настроек не получает (берет `spring.kafka.streams.*`) и работает с умолчаниями Kafka: `max.block.ms` 60000, `delivery.timeout.ms` 120000; запись финала асинхронная" | `TR/application.yml:13-26`; лог StreamsConfig/ProducerConfig на запуске |
| E7 | 2.1 T4; 3, сц. 7 | "503 problem+json через 5–10 с" | Оставить "5–10 с" и добавить: "10 с при прогретом продюсере (`delivery.timeout.ms`, в логе `TimeoutException: Expiring 1 record(s)…`), 5 с - если метаданных топика еще нет (`max.block.ms`). В логе - ERROR `LoggingProducerListener` с телом сообщения, включая сумму" | R4 (10,05 с), R4b (5,12 с) |
| E8 | 3, сц. 11; 7, B1 | "сразу в `<топик>.DLT`… исходные байты сохраняются"; "Уход в DLT молчаливый" | Добавить: "Битое сообщение в `transfers` попадает в `transfers.DLT` **по копии на каждую группу** (limit, screening, transfer-tracker - 3 записи; заголовок `kafka_dlt-original-consumer-group`). При ошибке *обработки* в DLT уходит заново сериализованный JSON, а не исходные байты. "Молчаливый": spring-kafka не пишет WARN/ERROR ни о повторах, ни о публикации в DLT; видны только INFO `Seeking to offset` и ошибки самих компонентов (например, ERROR Hibernate)" | R11, R21 |
| E9 | 9.3, диаграмма отказа | Нет INSERT и публикации NEW: после `TS-->>C: 201` сразу идет `K->>L`; `Note over K,J: … резерв RESERVED до обнуления периода` | Перед `TS-->>C: 201` добавить `TS->>K: transfers: NEW (key = transferId)`. Note: "…финала нет, перевод NEW, резерв остается RESERVED; сумма занимает лимит до обнуления периода" | 9.1 сам требует "INSERT и публикация NEW до ответа 201"; состояние резерва после обнуления периода не меняется (`SenderUsage.lazyReset` не трогает `reservations`) |
| E10 | 9.4, правило после ER | "**Засчитывать как верное:** `sender_usage \|\|..\|{ reservations` (1 вместо 0..1). Это неточность (−0,5), а не ошибка" | Выбрать одно: "Неточность (−0,5), а не ошибка: `sender_usage \|\|..\|{ reservations` (1 вместо 0..1)" | Сейчас утверждение одновременно "верное" и штрафуется |
| E11 | 1, таблица модулей | limit- и screening-service: "spring-boot-starter (без web), spring-kafka, data-jpa, postgresql, liquibase, jackson-jsr310, transfer-common" | Добавить `jackson-databind` | `limit-service/pom.xml:43-46`, `screening-service/pom.xml:43-46`. Модель, назвавшая `jackson-databind`, права |
| E12 | 6, REST | "Тексты 409, 500 и 503 на русском" | Добавить: "400 - стандартные английские тексты Spring: `Invalid request content.` (валидация, поле не указывается), `Failed to read request` (битый JSON), `Failed to convert 'id' with value: 'abc'` (GET)" | R3. Чтобы не штрафовать модели за "400 на английском" и ловить "все ошибки на русском" |
| E13 | 1, инфраструктура (новая строка) | - | "transfer-service не стартует, если имя брокера не резолвится (контейнер `broker` остановлен): `No resolvable bootstrap urls` -> `Could not start stream` -> цикл рестартов (`restart: unless-stopped`). Если брокер резолвится, но не отвечает, сервис стартует примерно за 42 с (`KafkaAdmin: Could not configure topics`)" | R4b |
| E14 | 5.1, внутренние топики | "внутренние топики JOIN - создает Kafka Streams" | Добавить имена и настройки: `transfer-service-KSTREAM-JOINTHIS-0000000004-store-changelog`, `…-JOINOTHER-0000000005-store-changelog`, 3 партиции, `cleanup.policy=delete`, `retention.ms=86420000` | R12. Модели, назвавшие эти топики, не должны получить штраф за "выдуманный топик" (раздел 8) |
| E15 | 7, A3 (по желанию) | "После компакции в топике остается только финал" | Добавить: "компакция затрагивает только закрытые сегменты (`segment.ms` по умолчанию 7 суток), поэтому на демо-стенде NEW обычно еще виден" | R12: `segment.ms=604800000` |

**G-0.** Утверждение "все диаграммы проверены рендером mermaid-cli 11" в этой проверке не перепроверялось, поэтому из эталона оно убрано.

---

## 5. Факты, которых нет в эталоне

Все факты подтверждены запуском или кодом. Их можно добавить в эталон как утверждения "вне эталона, верные" (0.1, п. 4) - модель не должна получать за них штраф.

| # | Факт | Доказательство | Значение для оценки |
|---|---|---|---|
| F-1 | **Квадратичное размножение финалов.** N повторных POST, пока перевод NEW, дают N публикаций NEW и до N² сообщений-финалов в `transfers` | R15: 10 -> 100 | Подтверждает и усиливает B7 (E3) |
| F-2 | **Ответ на повторный NEW собирается из нового сообщения.** `LimitService.onNewTransfer` и `ScreeningService.onNewTransfer` вызывают `transfer.withDecision(...)` у входящего сообщения, поэтому сохраненное решение уходит с новыми суммой и отправителем | `LimitService.java:29`, `ScreeningService.java:29`; R23 | Усиливает B14: последствия видны в `limit-results` и истории трекера |
| F-3 | **Три копии битого сообщения в `transfers.DLT`** - по одной на группу (limit, screening, transfer-tracker), различаются заголовком `kafka_dlt-original-consumer-group` | R11 | Верное утверждение "по копии на группу" не штрафовать |
| F-4 | При ошибке **обработки** в DLT уходит **заново сериализованный JSON** (с `__TypeId__`), а не исходные байты; при ошибке десериализации - исходные байты без `__TypeId__` | `KafkaErrorHandlingConfig` (шаблоны `byte[]` и `Object`); R11, R21 | Уточняет сц. 11 |
| F-5 | **Повторы и уход в DLT не логируются spring-kafka на WARN/ERROR.** Видны только INFO `Seeking to offset N` и повторные `Received …`; при первом уходе в DLT - INFO о создании DLT-продюсера | R11, R21 | Подтверждает "молчаливость" B1 |
| F-6 | **Длина массива `createdAt` переменная** (5–7 элементов) | R10, R21 | Уточнение к E1 |
| F-7 | **Тексты 400 английские**, поле в ошибке валидации не указывается | R3 | Уточнение к E12 и C8 |
| F-8 | **transfer-service не стартует без резолвимого брокера** (цикл рестартов); с нерезолвимым именем REST тоже недоступен | R4b | Риск доступности. Утверждение "REST работает без Kafka" верно только для уже запущенного сервиса |
| F-9 | **503 приходит через 10 с при прогретом продюсере, через 5 с при холодном**; неудачные NEW в R4 до брокера не дошли | R4, R4b | Уточнение к T4 и B15: "сообщение все же может дойти" - теоретически возможно, на запуске не наблюдалось |
| F-10 | **Гонка POST создает шум в логах**: каждый "проигравший" запрос дает пару WARN+ERROR Hibernate `duplicate key value violates unique constraint`, хотя клиент получает 200 | R15 | Ложный ERROR в мониторинге |
| F-11 | **Часовой пояс - UTC** (TZ не задан в `docker-compose.yml` и образе): сутки лимита сменяются в 03:00 по Москве | R22 | Конкретизирует A5 |
| F-12 | **Порядок вставки LIMIT и SCREENING в историю произвольный** (SCREENING может оказаться раньше LIMIT); GET это скрывает сортировкой по этапу | R15 | Конкретизирует B8 |
| F-13 | **Метка времени финала = более поздняя метка из пары ответов** (`CreateTime` финала совпал с ответом screening) | R6 | Справочно, для вопросов про окно JOIN |
| F-14 | **Kafka Streams предупреждает о state-dir в `/tmp`**: WARN `Using an OS temp directory in the state.dir property can cause failures…` при каждом старте. После недоступности брокера Streams восстанавливается сам, без рестарта сервиса | лог transfer-service; R4 | Подтверждает C7 |

---

## Приложение А. Вспомогательные команды

Рабочий каталог - `transfer-limit-system/`. `KP_DIR` - каталог с `P.java` и jar-файлами kafka-clients и slf4j-api из образа transfer-service.

```bash
dc()  { docker compose "$@"; }
post(){ curl -s -o body -D hdr -w 'HTTP %{http_code} time=%{time_total}s\n' -H 'Content-Type: application/json' -X POST localhost:8080/transfers -d "$1"; grep -i '^content-type' hdr; cat body; echo; }
get() { curl -s -o body -D hdr -w 'HTTP %{http_code} time=%{time_total}s\n' "localhost:8080/transfers$1"; grep -i -E '^(content-type|content-length)' hdr; cat body; echo; }
pt()  { dc exec -T transfer-db  psql -U postgres -d transferdb  -c "$1"; }
pl()  { dc exec -T limit-db     psql -U postgres -d limitdb     -c "$1"; }
ps_() { dc exec -T screening-db psql -U postgres -d screeningdb -c "$1"; }
# kp: Java-продюсер с ключом Long и заголовками; kafka-clients-3.8.1.jar и slf4j-api взяты из BOOT-INF/lib образа transfer-service
kp()  { docker run --rm --network transfer-limit-system_default -v "$KP_DIR":/kp -w /kp maven:3.9.9-eclipse-temurin-21 java -cp '/kp/*' P.java "$@"; }
```

`P.java` (запуск: `kp <topic> <key|null> <k=v;k=v|-> <value>`):

```java
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class P {
    public static void main(String[] a) throws Exception {
        Properties p = new Properties();
        p.put("bootstrap.servers", "broker:29092");
        try (KafkaProducer<Long, byte[]> pr = new KafkaProducer<>(p, new LongSerializer(), new ByteArraySerializer())) {
            Long key = "null".equals(a[1]) ? null : Long.valueOf(a[1]);
            ProducerRecord<Long, byte[]> r = new ProducerRecord<>(a[0], key, a[3].getBytes(StandardCharsets.UTF_8));
            if (!"-".equals(a[2])) for (String h : a[2].split(";")) {
                String[] kv = h.split("=", 2);
                r.headers().add(kv[0], kv[1].getBytes(StandardCharsets.UTF_8));
            }
            var md = pr.send(r).get();
            System.out.println("sent " + md.topic() + "-" + md.partition() + "@" + md.offset() + " ts=" + md.timestamp());
        }
    }
}
```

Чтение топика с заголовками:

```bash
dc exec -T broker kafka-console-consumer --bootstrap-server broker:29092 --topic transfers --from-beginning --timeout-ms 5000 \
  --property print.headers=true --property print.key=true --property print.partition=true --property print.timestamp=true \
  --property key.deserializer=org.apache.kafka.common.serialization.LongDeserializer
```

Тестовые данные запуска: переводы 1001, 1011–1013, 1101–1103, 1201–1202, 1401, 1801, 4001–4002, 8001, 9901–9903, 9911–9913 через REST; 5001, 5002, 6001–6003, 7001–7002 - записью в Kafka в обход REST. Состояние для R17 и R18 подготовлено `UPDATE` в limitdb. Пробный топик `zz-probe`, созданный для проверки console-producer, удален до R12. В конце выполнен `docker compose down -v`: контейнеры, сеть и тома удалены, образы остались.
