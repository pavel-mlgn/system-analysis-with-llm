# ПАСПОРТ СИСТЕМЫ: `transfer-limit-system`

### 1. НАЗНАЧЕНИЕ
Система предназначена для приёма, проверки и проведения денежных переводов между отправителями и получателями с сохранением полной истории этапов обработки. Каждый новый перевод проходит две автоматические проверки: контроль дневных и месячных лимитов отправителя, а также скрининг отправителя по списку заблокированных лиц (фрод, санкции и др.). По результатам обеих проверок перевод либо успешно исполняется с фиксацией израсходованного лимита, либо отклоняется/отменяется с возвратом зарезервированного лимита.

---

### 2. АРХИТЕКТУРА
Использована **событийно-ориентированная микросервисная архитектура** (Event-Driven Microservices) с отдельной базой данных на каждый сервис (Database-per-Service) и координацией через брокер сообщений и потоковую обработку (Kafka Streams).
При обработке запроса `transfer-service` сохраняет перевод в БД со статусом `NEW` и публикует событие в Kafka, которое параллельно обрабатывают `limit-service` и `screening-service`; их ответы объединяются (join) в `transfer-service` через Kafka Streams для вынесения финального решения, которое снова публикуется в Kafka для обновления статуса перевода и фиксации или освобождения зарезервированного лимита.

---

### 3. СТЕК ТЕХНОЛОГИЙ
Java 21 (Eclipse Temurin 21), Maven 3.9.9, Spring Boot 3.4.1 (Spring Web, Spring Data JPA / Hibernate, Spring Validation), Spring for Apache Kafka, Apache Kafka Streams, Apache Kafka (Confluent Platform 7.6.0, режим KRaft), PostgreSQL 16 (`postgres:16-alpine`), Liquibase, Jackson (`jackson-databind`, `jackson-datatype-jsr310`), OpenAPI 3.0.3, Docker, Docker Compose.

---

### 4. МОДУЛИ

| Модуль | Тип | Внешний API | Назначение |
| :--- | :--- | :--- | :--- |
| `transfer-common` | библиотека | нет | Содержит общую DTO-модель перевода (`com.example.common.Transfer`) для обмена событиями между сервисами. |
| `transfer-service` | сервис | есть (REST: `/transfers`, `/transfers/{id}`) | Принимает запросы на создание и получение переводов (`TransferController`), сводит результаты проверок (`DecisionTopologyConfig`) и ведёт историю статусов (`TransferTrackingService`). |
| `limit-service` | сервис | нет | Контролирует дневные и месячные лимиты отправителя, управляя резервированием, списанием и возвратом лимитов (`LimitLedger`). |
| `screening-service` | сервис | нет | Проверяет отправителя перевода на наличие в реестре заблокированных лиц (`ScreeningJournal`). |

---

### 5. СВЯЗИ МЕЖДУ МОДУЛЯМИ

**2. Зависимости на уровне сборки (`pom.xml`):**
* Сервисы `transfer-service`, `limit-service` и `screening-service` зависят от библиотеки `transfer-common`. Прямых зависимостей между самими сервисами нет.

**3. Асинхронное взаимодействие (Apache Kafka):**
* **Топик `transfers`:**
  * *Отправляют:*
    * `transfer-service` — `TransferController.publishNew` (публикация нового перевода в статусе `NEW`).
    * `transfer-service` — `DecisionTopologyConfig.decisionStream` (публикация итогового решения из топологии Kafka Streams).
  * *Получают:*
    * `limit-service` — `TransferEventsListener.onEvent` (группа `limit`).
    * `screening-service` — `TransferEventsListener.onEvent` (группа `screening`).
    * `transfer-service` — `TransferEventListener.onEvent` (группа `transfer-tracker`).
* **Топик `limit-results`:**
  * *Отправляет:* `limit-service` — `LimitService.sendAndWait`.
  * *Получают:*
    * `transfer-service` — `DecisionTopologyConfig.decisionStream` (Kafka Streams; `application.id` явно в коде не задан, по умолчанию используется `spring.application.name`: `transfer-service`).
    * `transfer-service` — `TransferEventListener.onEvent` (группа `transfer-tracker`).
* **Топик `screening-results`:**
  * *Отправляет:* `screening-service` — `ScreeningService.sendAndWait`.
  * *Получают:*
    * `transfer-service` — `DecisionTopologyConfig.decisionStream` (Kafka Streams).
    * `transfer-service` — `TransferEventListener.onEvent` (группа `transfer-tracker`).
* **Топики недоставленных сообщений (`transfers.DLT`, `limit-results.DLT`, `screening-results.DLT`):**
  * *Отправляют:* обработчики ошибок `KafkaErrorHandlingConfig.kafkaErrorHandler` (`DeadLetterPublishingRecoverer`) во всех трёх сервисах при сбоях чтения/обработки.
  * *Получают:* в коде не найдено.

**4. Синхронные вызовы между сервисами:**
* В коде не найдено (межсервисное взаимодействие полностью асинхронное; HTTP REST API в `transfer-service` используется только для внешних вызовов).

---

### 6. ИНФРАСТРУКТУРА
Согласно `docker-compose.yml` и файлам `application*.yml`, для работы требуются:
* **Брокер сообщений Apache Kafka:** образ `confluentinc/cp-kafka:7.6.0` (1 узел в роли `broker,controller`).
* **СУБД PostgreSQL:** образ `postgres:16-alpine` в виде трёх изолированных инстансов:
  * `transfer-db` (база `transferdb`) — для `transfer-service`;
  * `limit-db` (база `limitdb`) — для `limit-service`;
  * `screening-db` (база `screeningdb`) — для `screening-service`.
* **Кэши и другие внешние системы:** в коде не найдено.