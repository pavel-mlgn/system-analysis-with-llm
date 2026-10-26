# ПАСПОРТ СИСТЕМЫ

## 1. НАЗНАЧЕНИЕ

Система принимает заявки на денежные переводы через REST, проверяет дневные и месячные лимиты отправителя, а также его наличие в списке заблокированных. По результатам двух проверок формируется итоговый статус перевода; сохраняются текущее состояние и история обработки. Фактическое проведение платежа во внешней платёжной системе в коде не найдено (`TransferDecisionService.java` — `decide()`, `TransferTrackingService.java` — `applyFinalStatus()`).

## 2. АРХИТЕКТУРА

Использована **микросервисная событийная архитектура**: три Spring Boot-сервиса взаимодействуют через Kafka, каждый имеет собственную PostgreSQL-БД, а общий формат сообщений вынесен в библиотеку `transfer-common`. При `POST /transfers` сервис сохраняет перевод и публикует `NEW` в `transfers`; сервисы лимитов и скрининга независимо возвращают решения, после чего Kafka Streams в `transfer-service` объединяет их по ID в 10-секундном окне и публикует итог в `transfers`, а слушатели обновляют статус, историю и состояние резерва (`TransferController.java` — `create()`, `publishNew()`; `DecisionTopologyConfig.java` — `decisionStream()`).

## 3. СТЕК ТЕХНОЛОГИЙ

Java 21, Eclipse Temurin 21, Spring Boot 3.4.1, Spring Web MVC, Bean Validation, Spring Kafka, Kafka Streams, Spring Data JPA/Hibernate, PostgreSQL JDBC, Liquibase, Jackson, Maven 3.9.9, Docker, Docker Compose, OpenAPI 3.0.3. Версии зависимостей, управляемых Spring Boot, отдельно в коде не указаны (`pom.xml`, `*/pom.xml`, `Dockerfile`, `transfer-api.yaml`).

## 4. МОДУЛИ

| Модуль | Тип | Внешний API | Назначение |
|---|---|---|---|
| `transfer-common` | Библиотека | Нет | Общая модель сообщения `Transfer` и операции создания сообщения и добавления решения (`Transfer.java`). |
| `transfer-service` | Сервис | **Есть:** REST `/transfers`, порт 8080 | Приём и выдача переводов, ведение истории, объединение результатов проверок и публикация итогового статуса (`TransferController.java`, `DecisionTopologyConfig.java`, `TransferTrackingService.java`). |
| `limit-service` | Сервис | Нет | Проверка лимитов отправителя, учёт резервирования и его завершение по итоговому статусу (`LimitLedger.java`, `LimitService.java`). |
| `screening-service` | Сервис | Нет | Проверка отправителя по списку блокировок и сохранение результатов скрининга (`ScreeningJournal.java`, `ScreeningService.java`). |

Все Maven-модули имеют версию `1.0-SNAPSHOT`.

## 5. СВЯЗИ МЕЖДУ МОДУЛЯМИ

**Зависимости сборки.** Корневой `pom.xml` агрегирует все четыре модуля. `transfer-service`, `limit-service` и `screening-service` зависят от `transfer-common:1.0-SNAPSHOT`; зависимостей сборки между самими сервисами нет (`*/pom.xml`).

**Асинхронное взаимодействие — Kafka:**

| Топик | Отправитель | Получатели |
|---|---|---|
| `transfers` | `transfer-service`: `TransferController.publishNew()` — новые переводы; `DecisionTopologyConfig.decisionStream()` — итоговые решения | `limit-service`, группа `limit`; `screening-service`, группа `screening`; `transfer-service`, группа `transfer-tracker` — обрабатывает только итоговые статусы |
| `limit-results` | `limit-service`: `LimitService.sendAndWait()` | `transfer-service`: Kafka Streams `DecisionTopologyConfig.decisionStream()` — группа явно не задана; `TransferEventListener.onEvent()`, группа `transfer-tracker` |
| `screening-results` | `screening-service`: `ScreeningService.sendAndWait()` | `transfer-service`: Kafka Streams `DecisionTopologyConfig.decisionStream()` — группа явно не задана; `TransferEventListener.onEvent()`, группа `transfer-tracker` |

Для ошибок слушателей настроены DLT: `transfers.DLT` во всех сервисах, а также `limit-results.DLT` и `screening-results.DLT` в `transfer-service`; получатели DLT в коде не найдены (`KafkaErrorHandlingConfig.java` — `kafkaErrorHandler()`).

**Синхронные вызовы.** HTTP-, gRPC- и иных синхронных вызовов между сервисами в коде не найдено. Ожидание через `send(...).get(...)` относится к подтверждению публикации в Kafka, а не к межсервисному RPC.

## 6. ИНФРАСТРУКТУРА

| Система | Версия / конфигурация | Назначение |
|---|---|---|
| Kafka | Образ `confluentinc/cp-kafka:7.6.0`; один broker/controller в режиме KRaft | Обмен сообщениями между всеми сервисами; внутренний адрес `broker:29092`. |
| PostgreSQL | Образ `postgres:16-alpine` | Три отдельных контейнера и БД: `transferdb` (`transfer-db`, порт хоста 5434), `limitdb` (`limit-db`, 5432), `screeningdb` (`screening-db`, 5433). |
| Java runtime | `eclipse-temurin:21-jre` | Запуск каждого сервиса в отдельном контейнере. |
| Сборка | `maven:3.9.9-eclipse-temurin-21` | Сборка выбранного модуля вместе с `transfer-common`. |

Для состояния Kafka Streams задан каталог `/tmp/transfer-service-streams` (`transfer-service/src/main/resources/application.yml`). Кэши и иные внешние системы в коде не найдены. Основной источник инфраструктурной конфигурации — `docker-compose.yml` и `application-docker.yml` каждого сервиса.