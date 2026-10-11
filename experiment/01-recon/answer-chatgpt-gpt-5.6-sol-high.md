Изучены все 57 файлов из приложенного исходника. Ниже — краткий паспорт, без предположений за пределами кода.

# ПАСПОРТ СИСТЕМЫ

## 1. НАЗНАЧЕНИЕ

Система принимает и отслеживает денежные переводы, проверяя их по двум независимым направлениям: лимитам отправителя и наличию отправителя в списке заблокированных. Результаты проверок объединяются, после чего переводу присваивается финальный статус и сохраняется история его обработки.

Основание: `transfer-service/.../TransferController.java — TransferController#create`, `limit-service/.../LimitLedger.java — decide`, `screening-service/.../ScreeningJournal.java — check`, `transfer-service/.../TransferDecisionService.java — decide`. :chatgpt-content-reference{index="0"}

## 2. АРХИТЕКТУРА

Микросервисная событийно-ориентированная архитектура с отдельной БД у каждого сервиса и Kafka как транспортом. `transfer-service` принимает HTTP-запрос, сохраняет перевод и публикует событие `NEW`; `limit-service` и `screening-service` независимо выполняют проверки и публикуют результаты, после чего Kafka Streams в `transfer-service` объединяет их и возвращает финальное событие в `transfers`. (`TransferController#publishNew`, оба `TransferEventsListener#onEvent`, `DecisionTopologyConfig#decisionStream`, `TransferEventListener#onEvent`). :chatgpt-content-reference{index="1"}

## 3. СТЕК ТЕХНОЛОГИЙ

Java 21, Spring Boot 3.4.1, Spring MVC, Jakarta Validation, Spring Kafka, Apache Kafka Streams, Spring Data JPA, PostgreSQL JDBC, Liquibase, Jackson, Maven 3.9.9, Eclipse Temurin 21, Confluent `cp-kafka` 7.6.0, PostgreSQL 16 (`postgres:16-alpine`), Docker/Docker Compose, OpenAPI 3.0.3.

Версии Spring Kafka, Kafka Streams, JPA/Hibernate, Liquibase и Jackson явно в исходных файлах не зафиксированы — управляются зависимостями Spring Boot. :chatgpt-content-reference{index="2"}

## 4. МОДУЛИ

| Модуль | Тип | Внешний API | Назначение |
|---|---|---|---|
| `transfer-service` | сервис | есть | REST API создания/чтения переводов, хранение состояния и истории, публикация событий и агрегация результатов проверок. |
| `limit-service` | сервис | нет | Проверяет и резервирует дневные/месячные лимиты отправителя и публикует результат проверки. |
| `screening-service` | сервис | нет | Проверяет отправителя по локальному списку блокировок и публикует результат проверки. |
| `transfer-common` | библиотека | нет | Общая модель Kafka-сообщения `com.example.common.Transfer`. |

HTTP API найден только в `transfer-service`: `POST /transfers`, `GET /transfers`, `GET /transfers/{id}` (`TransferController`; `openapi/transfer-api.yaml`). :chatgpt-content-reference{index="3"}

## 5. СВЯЗИ МЕЖДУ МОДУЛЯМИ

**Зависимости сборки.** Корневой `pom.xml` объединяет четыре Maven-модуля. `transfer-service`, `limit-service` и `screening-service` зависят от `transfer-common:1.0-SNAPSHOT`; зависимостей сервисов непосредственно друг от друга в `pom.xml` не найдено. (`pom.xml`, соответствующие `*/pom.xml`). :chatgpt-content-reference{index="4"}

**Асинхронное взаимодействие:**

| Kafka-топик | Отправитель | Получатель |
|---|---|---|
| `transfers` | `transfer-service`: `TransferController#publishNew`; также `DecisionTopologyConfig#decisionStream` публикует финальное решение | `limit-service` — `TransferEventsListener#onEvent`, group `limit`; `screening-service` — `TransferEventsListener#onEvent`, group `screening`; `transfer-service` — `TransferEventListener#onEvent`, group `transfer-tracker` |
| `limit-results` | `limit-service` — `LimitService#sendAndWait` | Kafka Streams `DecisionTopologyConfig#decisionStream` — group/application id явно в коде не задан; также `TransferEventListener#onEvent`, group `transfer-tracker` |
| `screening-results` | `screening-service` — `ScreeningService#sendAndWait` | Kafka Streams `DecisionTopologyConfig#decisionStream` — group/application id явно в коде не задан; также `TransferEventListener#onEvent`, group `transfer-tracker` |
| `transfers.DLT` | настроен `DeadLetterPublishingRecoverer` в сервисах, потребляющих `transfers` | потребитель в коде не найден |
| `limit-results.DLT` | DLT-направление настроено в `transfer-service/KafkaErrorHandlingConfig` | потребитель в коде не найден |
| `screening-results.DLT` | DLT-направление настроено в `transfer-service/KafkaErrorHandlingConfig` | потребитель в коде не найден |

Основные consumer groups подтверждаются `@KafkaListener`: `limit`, `screening`, `transfer-tracker`. :chatgpt-content-reference{index="5"} :chatgpt-content-reference{index="6"}

**Синхронные межсервисные вызовы.** HTTP, gRPC, Feign, WebClient/RestClient или другие синхронные вызовы между сервисами в коде не найдены. Ожидание `.get(...)` после отправки Kafka-сообщения используется для подтверждения публикации, но взаимодействие сервисов остаётся через Kafka (`TransferController#publishNew`, `LimitService#sendAndWait`, `ScreeningService#sendAndWait`).

## 6. ИНФРАСТРУКТУРА

Нужны Kafka-брокер `confluentinc/cp-kafka:7.6.0` в одновузловой конфигурации broker/controller и три отдельные PostgreSQL 16 (`postgres:16-alpine`): `transferdb` для `transfer-service`, `limitdb` для `limit-service`, `screeningdb` для `screening-service`. Для Kafka и каждой БД определены persistent Docker volumes; миграции схем выполняет Liquibase. :chatgpt-content-reference{index="7"}

Кэши, Redis, service discovery, API Gateway, внешние HTTP-системы и иные инфраструктурные зависимости **в коде не найдены**.