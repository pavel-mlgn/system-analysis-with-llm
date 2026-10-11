# Паспорт системы `transfer-limit-system`

## 1. Назначение

Система принимает и проверяет денежные переводы между клиентами. Каждый перевод параллельно проходит две проверки. Первая — лимиты отправителя: дневной 50 000 и месячный 200 000, значения заданы в `limit-service/.../application.yml`. Вторая — скрининг по списку заблокированных отправителей. По итогам обеих проверок перевод получает финальный статус `COMPLETED`, `CANCELLED` или `DENIED`. Зарезервированный лимит при этом фиксируется или освобождается, а клиент может запросить статус перевода и историю этапов. Фактическое списание и зачисление средств в коде не найдено.

## 2. Архитектура

Используются событийно-ориентированные микросервисы с хореографией через Kafka; у каждого сервиса своя БД.

Обработка одного запроса:
1. `POST /transfers` → `transfer-service` сохраняет перевод и публикует событие `NEW` в топик `transfers`.
2. `limit-service` и `screening-service` независимо публикуют свои решения в `limit-results` и `screening-results`.
3. Kafka Streams в `transfer-service` (`DecisionTopologyConfig`) соединяет оба результата по id перевода в окне 10 с. Финальный статус вычисляет `TransferDecisionService.decide` и публикует обратно в `transfers`.
4. По финальному статусу `transfer-service` обновляет перевод, а `limit-service` фиксирует или освобождает резерв.

## 3. Стек технологий

Java 21, Spring Boot 3.4.1 (`spring-boot-starter-parent`), Spring Web, Bean Validation, Spring Data JPA (Hibernate), Spring for Apache Kafka, Kafka Streams, Liquibase, PostgreSQL JDBC, Jackson (databind, jsr310), SLF4J, Maven (сборка в образе `maven:3.9.9-eclipse-temurin-21`), Docker (runtime `eclipse-temurin:21-jre`), Docker Compose, OpenAPI 3.0.3 (`transfer-service/src/main/resources/openapi/transfer-api.yaml`).

Версии библиотек явно не указаны, ими управляет BOM Spring Boot 3.4.1. Плагин генерации кода по OpenAPI в `pom.xml` не найден, контроллер написан вручную.

## 4. Модули

| Модуль | Тип | Внешний API | Назначение |
|---|---|---|---|
| `transfer-limit-system` (корневой `pom.xml`) | агрегатор сборки (`packaging pom`) | нет | Объединяет модули в один reactor; родителем для них не является. |
| `transfer-common` | библиотека | нет | Общий контракт сообщений Kafka — record `Transfer`. |
| `transfer-service` | сервис | есть: REST `/transfers`, порт 8080 (`TransferController`) | Приём переводов, вычисление финального решения (Kafka Streams), хранение статуса и истории этапов. |
| `limit-service` | сервис | нет | Проверка и резервирование дневного и месячного лимита отправителя (`LimitLedger`). |
| `screening-service` | сервис | нет | Проверка отправителя по таблице `blocked_senders` (`ScreeningJournal`). |

## 5. Связи между модулями

**Зависимости сборки.** `transfer-service`, `limit-service` и `screening-service` зависят от `transfer-common`. Других межмодульных зависимостей нет.

**Асинхронное взаимодействие (Kafka).** Ключ сообщения — id перевода (`Long`), значение — JSON `Transfer`.

| Топик | Отправитель | Получатели (группа) |
|---|---|---|
| `transfers` | `transfer-service`: событие `NEW` (`TransferController.publishNew`) и финальные статусы (`DecisionTopologyConfig`) | `limit-service` (`limit`): `NEW` → решение, финал → settle; `screening-service` (`screening`): только `NEW`; `transfer-service` (`transfer-tracker`): только финальные статусы |
| `limit-results` | `limit-service` (`LimitService.sendAndWait`) | `transfer-service`: Kafka Streams-join и `TransferEventListener` (`transfer-tracker`) |
| `screening-results` | `screening-service` (`ScreeningService.sendAndWait`) | `transfer-service`: Kafka Streams-join и `TransferEventListener` (`transfer-tracker`) |
| `transfers.DLT`, `limit-results.DLT`, `screening-results.DLT` | `DeadLetterPublishingRecoverer` в `KafkaErrorHandlingConfig` каждого сервиса, после 3 неудачных повторов | в коде не найдено |

`application.id` Kafka Streams явно не задан. По умолчанию Spring Boot берёт `spring.application.name`, то есть `transfer-service`. Топики `transfers`, `limit-results` и `screening-results` (3 партиции, compact) объявлены одинаково в `TopicsConfig` всех трёх сервисов. Автосоздание топиков на брокере отключено.

**Синхронные вызовы между сервисами** (HTTP, gRPC и т. п.) в коде не найдены. Синхронный только внешний REST API `transfer-service`.

## 6. Инфраструктура

Все компоненты описаны в `docker-compose.yml`:

- **Apache Kafka** — образ `confluentinc/cp-kafka:7.6.0`, один узел в режиме KRaft (broker + controller). Внутренний адрес `broker:29092`, с хоста — `localhost:9092`.
- **PostgreSQL 16** (`postgres:16-alpine`), три отдельных экземпляра со схемами через Liquibase:
  - `transfer-db`: БД `transferdb`, порт хоста 5434;
  - `limit-db`: `limitdb`, порт 5432;
  - `screening-db`: `screeningdb`, порт 5433.
- **Локальное состояние Kafka Streams** — каталог `/tmp/transfer-service-streams`.

Кэши, аутентификация, мониторинг (actuator, метрики) и внешние HTTP-системы в коде не найдены.