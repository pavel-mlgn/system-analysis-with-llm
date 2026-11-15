Файл обрезался на transfer-service — дозаапрошу недостающую часть.
# Паспорт системы

## 1. Назначение

Система обработки денежных переводов: клиент создаёт перевод через REST API, а система решает, провести его или отклонить. Решение принимается двумя проверками — **лимитами отправителя** (дневной и месячный потолок трат) и **скринингом** (блок-листы отправителей) — после чего итоговый статус и вся история проверки сохраняются в базе.

## 2. Архитектура

Событийно-ориентированная микросервисная архитектура: сервисы обмениваются сообщениями только через Kafka, без прямых вызовов друг друга. Один запрос проходит конвейер: `transfer-service` сохраняет перевод и публикует событие `NEW` → `limit-service` и `screening-service` независимо проверяют его и публикуют результаты → `transfer-service` соединяет результаты stream-join'ом (Kafka Streams) в итоговое решение и публикует его в топик `transfers`, где его же трекер фиксирует финальный статус.

## 3. Стек технологий

Java 21 (`java.version` в pom.xml), Spring Boot 3.4.1 (spring-boot-starter-parent в pom.xml модулей), Spring Kafka и Kafka Streams (версии — из BOM Boot, явно не указаны), Spring Web/MVC, Spring Data JPA + Hibernate, Spring Validation, PostgreSQL JDBC driver, Liquibase, Jackson (databind + jsr310), Maven 3.9.9 (Dockerfile), Docker Compose.

## 4. Модули

| Модуль | Тип | Внешний API | Назначение |
|---|---|---|---|
| `transfer-common` | библиотека | нет | Общая модель события `Transfer` (record) для всех сервисов |
| `transfer-service` | сервис | **есть** (REST `/transfers`, см. `controller/TransferController`, OpenAPI-спека `openapi/transfer-api.yaml`) | Приём переводов, их хранение, склейка результатов проверок в финальное решение (Kafka Streams), ведение истории статусов |
| `limit-service` | сервис | нет | Проверка дневного и месячного лимитов отправителя, резервирование суммы, settling по финальным статусам |
| `screening-service` | сервис | нет | Проверка отправителя по блок-листу (`blocked_senders`) и публикация вердикта |

## 5. Связи между модулями

**Сборочные зависимости** (pom.xml модулей): `transfer-service`, `limit-service` и `screening-service` зависят от `transfer-common`.

**Асинхронное взаимодействие (Kafka):**

| Топик | Отправитель | Получатели |
|---|---|---|
| `transfers` | `transfer-service`: публикует `NEW` из `TransferController.publishNew`, а также итоговые решения из топологии `DecisionTopologyConfig` (`.to("transfers")`) | `limit-service` (группа `limit`), `screening-service` (группа `screening`), `transfer-service` (группа `transfer-tracker`) |
| `limit-results` | `limit-service` (`LimitService.sendAndWait`) | `transfer-service`: Kafka Streams join (`DecisionTopologyConfig`) и трекер (группа `transfer-tracker`) |
| `screening-results` | `screening-service` (`ScreeningService.sendAndWait`) | `transfer-service`: аналогично |
| `transfers.DLT`, `limit-results.DLT`, `screening-results.DLT` | DeadLetterPublishingRecoverer (3 копии `KafkaErrorHandlingConfig` — по одной в каждом сервисе) | никто — только накопление ошибок |

Важно: `transfer-service` одновременно и публикует в `transfers` (стартовые и финальные события), и потребляет из него — это внутренний цикл одного модуля, а не межсервисный синхронный вызов.

**Синхронные вызовы:** только внешний REST API `transfer-service` (`POST /transfers` и GET-эндпоинты истории/трекинга — точный список требует уточнения, контроллер в файле виден частично). HTTP/gRPC-вызовов между самими сервисами в коде не найдено — всё идёт через Kafka.

## 6. Инфраструктура (docker-compose.yml)

- **Kafka** — `confluentinc/cp-kafka:7.6.0`, KRaft-режим (без ZooKeeper), автосоздание топиков отключено, 3 топика создаются кодом (`TopicsConfig` каждого сервиса).
- **PostgreSQL** — `postgres:16-alpine`, три отдельных экземпляра, по одному на сервис: `transferdb` (порт 5434), `limitdb` (5432), `screeningdb` (5433); схема накатывается Liquibase.
- Кэш и другие внешние системы — в коде не найдены.

Замечание для быстрого старта: при локальной разработке без Docker база `transfer-service` по умолчанию ждёт порт `5434` (`application.yml` transfer-service), что совпадает с маппингом `transfer-db` из compose.