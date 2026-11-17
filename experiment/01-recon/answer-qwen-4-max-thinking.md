# ПАСПОРТ СИСТЕМЫ

## 1. НАЗНАЧЕНИЕ
Система приёма и автоматического решения по денежным переводам: клиент создаёт перевод, система проверяет отправителя по списку блокировок и по дневным/месячным лимитам суммы и выносит итог — COMPLETED, CANCELLED или DENIED. По каждому переводу хранится текущий статус и история прохождения этапов (NEW → LIMIT / SCREENING → FINAL), доступные через REST.

## 2. АРХИТЕКТУРА
Микросервисы (3 приложения + общая библиотека), событийно-ориентированное взаимодействие через Kafka, «БД на сервис» (3 отдельных PostgreSQL); синхронных вызовов между сервисами нет.
Обработка запроса: `TransferController.create` сохраняет перевод и публикует событие NEW в топик `transfers` → limit-service и screening-service независимо публикуют решения в `limit-results` / `screening-results` → Kafka Streams join (`DecisionTopologyConfig.decisionStream` + `TransferDecisionService.decide`, окно 10 сек) публикует итог обратно в `transfers` → listener `TransferEventListener` (группа `transfer-tracker`) обновляет статус и историю в БД.

## 3. СТЕК ТЕХНОЛОГИЙ
Java 21, Spring Boot 3.4.1, Spring Web MVC, Spring Kafka (версия — из BOM Spring Boot 3.4.1, явно не указана), Kafka Streams (версия из BOM), Spring Data JPA / Hibernate (из BOM), Bean Validation (spring-boot-starter-validation), PostgreSQL JDBC (из BOM), Liquibase (из BOM), Jackson databind + jackson-datatype-jsr310, Maven (multi-module; образ сборки maven:3.9.9-eclipse-temurin-21), Docker / Docker Compose, runtime eclipse-temurin:21-jre, OpenAPI 3.0.3 (только описание `transfer-service/src/main/resources/openapi/transfer-api.yaml`, кодогенерации в коде не найдено).

## 4. МОДУЛИ

| Модуль | Тип | Внешний API | Назначение |
|---|---|---|---|
| transfer-common | библиотека | нет | Общая модель события `Transfer` (record) для обмена между сервисами через Kafka. |
| transfer-service | сервис | есть — REST `/transfers` (POST, GET, GET /{id}), порт 8080 | Приём переводов, хранение переводов и истории, агрегация решений двух проверок через Kafka Streams и публикация финального статуса. |
| limit-service | сервис | нет (web-стартер отсутствует) | Проверка дневного/месячного лимита отправителя, резервирование и освобождение суммы (`LimitLedger`). |
| screening-service | сервис | нет (web-стартер отсутствует) | Проверка отправителя по списку заблокированных (`ScreeningJournal`, таблица `blocked_senders`). |

Корневой `pom.xml` — агрегатор (packaging pom), не родительский POM: сервисы наследуют `spring-boot-starter-parent`.

## 5. СВЯЗИ МЕЖДУ МОДУЛЯМИ

**Сборка:** transfer-service, limit-service, screening-service → `com.example:transfer-common:1.0-SNAPSHOT`. Обратных зависимостей и зависимостей сервисов друг от друга нет; transfer-common внешних зависимостей не имеет.

**Асинхронные каналы (Kafka):** все бизнес-топики — 3 партиции, cleanup.policy=compact (`TopicsConfig` в каждом сервисе).

| Топик | Отправитель | Получатели |
|---|---|---|
| `transfers` | transfer-service: `TransferController.publishNew` (NEW) и Streams-топология `DecisionTopologyConfig` (COMPLETED/CANCELLED/DENIED) | limit-service — группа `limit` (`TransferEventsListener`); screening-service — группа `screening` (`TransferEventsListener`); transfer-service — группа `transfer-tracker` (`TransferEventListener`, обрабатывает только не-NEW) |
| `limit-results` | limit-service (`LimitService.sendAndWait`) | transfer-service: Streams join (`DecisionTopologyConfig`) и группа `transfer-tracker` |
| `screening-results` | screening-service (`ScreeningService.sendAndWait`) | transfer-service: Streams join (`DecisionTopologyConfig`) и группа `transfer-tracker` |
| `transfers.DLT` | DeadLetterPublishingRecoverer всех трёх сервисов (`KafkaErrorHandlingConfig`) | потребителей в коде не найдено |
| `limit-results.DLT`, `screening-results.DLT` | объявлены только в transfer-service (`KafkaErrorHandlingConfig.limitResultsDlt/screeningResultsDlt`); фактических отправителей и потребителей в коде не найдено | — |

Consumer group Streams-приложения явно не задана (`application.id` в `application.yml` отсутствует) — требует уточнения.

**Синхронные вызовы между сервисами:** в коде не найдено (нет RestTemplate/WebClient/Feign/gRPC). Синхронно доступны только REST API transfer-service для внешнего клиента и JDBC-подключения сервисов к своим БД.

## 6. ИНФРАСТРУКТУРА
- **Apache Kafka** — confluentinc/cp-kafka:7.6.0, один узел в KRaft (роли broker+controller), `KAFKA_AUTO_CREATE_TOPICS_ENABLE=false` (топики создаются бинами `NewTopic`); хост-порт 9092, внутри сети `broker:29092`; том kafka-data.
- **PostgreSQL 16-alpine ×3** (docker-compose.yml): `transfer-db` → transferdb (хост 5434), `limit-db` → limitdb (5432), `screening-db` → screeningdb (5433); схемы — Liquibase (`db/changelog/changelog-master.yaml`), `ddl-auto: none`; в screening есть тестовые данные блокировок (`002-insert-test-data.sql`).
- **Docker / Docker Compose** — сборка одного Dockerfile с `ARG MODULE`, профиль `docker` для всех сервисов, healthcheck и `depends_on: service_healthy`.
- Кэши, внешний брокер очередей помимо Kafka, API-gateway, service discovery, системы аутентификации, мониторинг — в коде не найдено.