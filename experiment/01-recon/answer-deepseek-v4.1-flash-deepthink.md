# ПАСПОРТ СИСТЕМЫ

## 1. НАЗНАЧЕНИЕ
Система обрабатывает денежные переводы: принимает заявку на перевод, проверяет её по лимитам отправителя (дневной/месячный) и по стоп-листам (заблокированные отправители), после чего выносит финальное решение — выполнить, отклонить или отменить перевод. Ведётся журнал истории изменения статусов перевода по этапам (NEW → LIMIT → SCREENING → FINAL).

## 2. АРХИТЕКТУРА
Событийно-ориентированная (event-driven) микросервисная архитектура на Kafka: REST-вызов создаёт перевод в `transfer-service`, тот публикует событие в топик `transfers`, его параллельно читают `limit-service` и `screening-service`, публикуя решения в `limit-results` и `screening-results`; `transfer-service` через Kafka Streams джойнит оба потока и выносит финальный статус, а отдельный listener фиксирует историю в БД.

## 3. СТЕК ТЕХНОЛОГИЙ
Java 21, Maven 3.9.9, Spring Boot 3.4.1, Spring Web, Spring Kafka, Kafka Streams, Spring Data JPA, Liquibase, PostgreSQL 16, Jackson (databind, jsr310), OpenAPI 3.0.3, Docker / Docker Compose, Confluent cp-kafka 7.6.0.

## 4. МОДУЛИ
- **transfer-common** | библиотека | внешнего API нет | Общий record `Transfer` — DTO-контракт для событий Kafka.
- **transfer-service** | сервис | внешний API есть (`/transfers`, REST) | Принимает переводы, хранит их и историю, финально решает по результатам двух сервисов.
- **limit-service** | сервис | внешнего API нет | Проверяет дневной и месячный лимиты отправителя, ведёт резервы.
- **screening-service** | сервис | внешнего API нет | Проверяет отправителя по стоп-листу.

## 5. СВЯЗИ МЕЖДУ МОДУЛЯМИ

**Зависимости на уровне сборки:**
- `transfer-service`, `limit-service`, `screening-service` зависят от `transfer-common` (зависимость `com.example:transfer-common:1.0-SNAPSHOT` в их `pom.xml`).
- Родительский `pom.xml` агрегирует все 4 модуля.

**Асинхронные взаимодействия (Kafka):**

| Топик | Отправитель | Получатель (groupId) |
|---|---|---|
| `transfers` | transfer-service (`TransferController.publishNew`), также transfer-service Kafka Streams (`DecisionTopologyConfig` — финальный статус) | limit-service (`limit`), screening-service (`screening`), transfer-service (`transfer-tracker`) |
| `limit-results` | limit-service (`LimitService.sendAndWait`) | transfer-service Kafka Streams (`DecisionTopologyConfig`), transfer-service (`transfer-tracker`) |
| `screening-results` | screening-service (`ScreeningService.sendAndWait`) | transfer-service Kafka Streams (`DecisionTopologyConfig`), transfer-service (`transfer-tracker`) |
| `transfers.DLT`, `limit-results.DLT`, `screening-results.DLT` | dead-letter recoverer каждого сервиса | в коде не найдено потребителей |

**Синхронные вызовы между сервисами:** в коде не найдено — межсервисное взаимодействие только через Kafka.

## 6. ИНФРАСТРУКТУРА
- **Kafka**: `confluentinc/cp-kafka:7.6.0` (KRaft-режим, один узел `broker`, топики `transfers`, `limit-results`, `screening-results` с 3 партициями, compact; DLT-топики). Автосоздание топиков отключено (`KAFKA_AUTO_CREATE_TOPICS_ENABLE: "false"`).
- **PostgreSQL**: три инстанса `postgres:16-alpine`:
  - `transfer-db` → `transferdb` (порт 5434),
  - `limit-db` → `limitdb` (порт 5432),
  - `screening-db` → `screeningdb` (порт 5433).
- **Миграции БД**: Liquibase (SQL-чейнджлоги в каждом сервисе).
- **Docker**: сборка многомодульного образа через `Dockerfile` (multi-stage: poms → build → jre).
- Кэши, брокеры иных типов, сервис-дискавери, API-gateway — в коде не найдены.