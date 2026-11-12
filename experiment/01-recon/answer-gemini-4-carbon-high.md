# ПАСПОРТ СИСТЕМЫ (`transfer-limit-system`)

### 1. НАЗНАЧЕНИЕ
Система предназначена для приёма, автоматической проверки и учёта денежных переводов между отправителями и получателями. Перед выполнением перевода система параллельно проверяет соблюдение дневных и месячных лимитов отправителя (с резервированием суммы), а также отсутствие отправителя в списке заблокированных лиц. По итогам проверок переводу присваивается финальный статус и сохраняется полная история прохождения этапов, доступная для запроса.

### 2. АРХИТЕКТУРА
Построена на базе событийно-ориентированной микросервисной архитектуры с изолированной БД у каждого сервиса и асинхронным обменом событиями через брокер сообщений. При получении HTTP-запроса `transfer-service` (`transfer-service/.../TransferController.create`) сохраняет перевод в БД и публикует событие `NEW` в Kafka, которое параллельно обрабатывают `limit-service` и `screening-service`, а топология Kafka Streams в `transfer-service` (`transfer-service/.../DecisionTopologyConfig.decisionStream`) объединяет их ответы из результирующих топиков и публикует финальное решение обратно в топик переводов для фиксации статуса и подтверждения либо освобождения зарезервированного лимита.

### 3. СТЕК ТЕХНОЛОГИЙ
Java 21, Maven 3.9.9 (`Dockerfile`, `pom.xml`), Spring Boot 3.4.1 (Spring Web, Spring Validation, Spring Data JPA / Hibernate, Spring for Apache Kafka), Apache Kafka / Confluent Platform 7.6.0 (`docker-compose.yml`), Kafka Streams (`transfer-service/pom.xml`), PostgreSQL 16 (`docker-compose.yml`), Liquibase, Jackson (`jackson-databind`, `jackson-datatype-jsr310`), OpenAPI 3.0.3 (`transfer-service/src/main/resources/openapi/transfer-api.yaml`), Docker / Docker Compose (микроверсии библиотек внутри BOM Spring Boot 3.4.1 явно в `pom.xml` не указаны).

### 4. МОДУЛИ
* `transfer-common` | библиотека | нет | Содержит общую DTO-модель перевода (`transfer-common/.../Transfer.java`) для обмена сообщениями между сервисами.
* `transfer-service` | сервис | есть (REST API `/transfers`, порт `8080`) | Принимает запросы на создание и получение переводов, сводит результаты проверок в финальное решение и ведёт историю этапов обработки (`TransferController`, `DecisionTopologyConfig`, `TransferTrackingService`).
* `limit-service` | сервис | нет | Проверяет дневные и месячные лимиты отправителя, резервируя сумму перевода и списывая или возвращая резерв по финальному статусу (`limit-service/.../LimitService`, `LimitLedger`).
* `screening-service` | сервис | нет | Проверяет отправителя перевода на наличие в реестре заблокированных отправителей (`screening-service/.../ScreeningService`, `ScreeningJournal`).

### 5. СВЯЗИ МЕЖДУ МОДУЛЯМИ

**Зависимости на уровне сборки:**
* Модули `transfer-service`, `limit-service` и `screening-service` зависят от библиотеки `transfer-common` (`transfer-service/pom.xml`, `limit-service/pom.xml`, `screening-service/pom.xml`). Прямых зависимостей сервисов друг от друга нет.

**Асинхронное взаимодействие (Apache Kafka):**
* Топик **`transfers`** (`TopicsConfig.transfersTopic`):
  * *Отправляет:* `transfer-service` — статус `NEW` (`TransferController.publishNew`) и финальный статус (`DecisionTopologyConfig.decisionStream`).
  * *Получают:*
    * `limit-service` (`limit-service/.../TransferEventsListener.onEvent`, группа `limit`);
    * `screening-service` (`screening-service/.../TransferEventsListener.onEvent`, группа `screening`);
    * `transfer-service` (`transfer-service/.../TransferEventListener.onEvent`, группа `transfer-tracker`).
* Топик **`limit-results`** (`TopicsConfig.limitResultsTopic`):
  * *Отправляет:* `limit-service` (`LimitService.sendAndWait`).
  * *Получают:* `transfer-service` — слушатель истории (`TransferEventListener.onEvent`, группа `transfer-tracker`) и поток Kafka Streams (`DecisionTopologyConfig.decisionStream`; явный `application-id` в `application.yml` не задан, указано `spring.application.name: transfer-service`).
* Топик **`screening-results`** (`TopicsConfig.screeningResultsTopic`):
  * *Отправляет:* `screening-service` (`ScreeningService.sendAndWait`).
  * *Получают:* `transfer-service` — слушатель истории (`TransferEventListener.onEvent`, группа `transfer-tracker`) и поток Kafka Streams (`DecisionTopologyConfig.decisionStream`; явный `application-id` в `application.yml` не задан, указано `spring.application.name: transfer-service`).
* Dead Letter Topics (**`transfers.DLT`**, **`limit-results.DLT`**, **`screening-results.DLT`**):
  * *Отправляют (при ошибках обработки после ретраев):* в `transfers.DLT` — `transfer-service`, `limit-service`, `screening-service`; в `limit-results.DLT` и `screening-results.DLT` — `transfer-service` (`KafkaErrorHandlingConfig.kafkaErrorHandler` в каждом сервисе).
  * *Получают:* подписчики в коде не найдены.

**Синхронные вызовы между сервисами:**
* В коде не найдено (HTTP REST API в `transfer-service/.../TransferController` предназначен только для внешних клиентов).

### 6. ИНФРАСТРУКТУРА
Согласно `docker-compose.yml`, для работы системы требуются:
* **Apache Kafka** — образ `confluentinc/cp-kafka:7.6.0` (сервис `broker`, режим KRaft).
* **PostgreSQL 16** — образ `postgres:16-alpine`, три отдельных экземпляра БД:
  * `transfer-db` (БД `transferdb`) — для `transfer-service`;
  * `limit-db` (БД `limitdb`) — для `limit-service`;
  * `screening-db` (БД `screeningdb`) — для `screening-service`.
* Иные внешние системы (кэши, внешние API и т. п.) в коде не найдены.