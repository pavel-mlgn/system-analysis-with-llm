# Паспорт системы `transfer-limit-system`

## 1. Назначение

Система принимает заявки на денежные переводы и по каждой выносит итоговое решение: перевод исполнен, отменён или отклонён (статусы COMPLETED / CANCELLED / DENIED). Решение строится на двух независимых проверках: дневного и месячного лимитов отправителя и наличия отправителя в списке заблокированных. Клиент создаёт перевод через API и затем может посмотреть его статус и историю прохождения этапов; фактического движения денег (счета, балансы, проводки) в коде не найдено.

## 2. Архитектура

Событийно-ориентированные микросервисы: у каждого сервиса своя БД, между собой они общаются только через топики Kafka (хореография, без оркестратора). `transfer-service` принимает `POST /transfers`, сохраняет перевод и публикует событие NEW в `transfers` → `limit-service` и `screening-service` независимо публикуют вердикты в `limit-results` и `screening-results` → `transfer-service` сводит их (Kafka Streams join с окном 10 с, `DecisionTopologyConfig.decisionStream`) и публикует итоговый статус снова в `transfers`, где его обрабатывают он сам и `limit-service`; клиент узнаёт итог запросом `GET /transfers/{id}`.

## 3. Стек технологий

Java 21, Spring Boot 3.4.1 (Data JPA — во всех сервисах; Web и Validation — только в `transfer-service`), Spring for Apache Kafka, Kafka Streams (только `transfer-service`), JPA/Hibernate, Liquibase, Jackson, JDBC-драйвер PostgreSQL, PostgreSQL 16 (`postgres:16-alpine`), Apache Kafka (образ `confluentinc/cp-kafka:7.6.0`, режим KRaft), OpenAPI 3.0.3 (спецификация `transfer-api.yaml`; генерация кода по ней в сборке не подключена), Maven 3.9.9, Eclipse Temurin 21 (JDK для сборки, JRE для запуска), Docker и Docker Compose.

Версии Spring Kafka, Kafka Streams, Hibernate, Liquibase, Jackson и JDBC-драйвера в pom-файлах не указаны: они наследуются от `spring-boot-starter-parent` 3.4.1.

## 4. Модули

| Модуль | Тип | Внешний API | Назначение |
|---|---|---|---|
| `transfer-common` | библиотека | нет | Общий формат сообщения о переводе (`com.example.common.Transfer`), которым сервисы обмениваются через Kafka. |
| `transfer-service` | сервис | есть: REST `/transfers` (`TransferController`), порт 8080 | Принимает заявки на перевод, хранит переводы и историю статусов, сводит вердикты проверок в итоговое решение. |
| `limit-service` | сервис | нет | Проверяет дневной и месячный лимиты отправителя (`limits.daily-max` = 50000.00, `limits.monthly-max` = 200000.00 в `application.yml`) и ведёт учёт резервов лимита. |
| `screening-service` | сервис | нет | Проверяет отправителя по списку заблокированных и сохраняет результат проверки. |

Порт 8080 взят из `docker-compose.yml`; `server.port` в `application.yml` не задан. Аутентификация и авторизация API, а также автотесты в приложенных файлах не найдены.

## 5. Связи между модулями

**На уровне сборки**
- `transfer-service`, `limit-service` и `screening-service` зависят от `transfer-common` (`com.example:transfer-common:1.0-SNAPSHOT` в `pom.xml` каждого сервиса). Друг от друга сервисы не зависят.
- Корневой `pom.xml` только перечисляет модули (агрегатор), но не является их родителем: сервисы наследуются от `spring-boot-starter-parent`, у `transfer-common` родителя нет.

**Асинхронно (Kafka)**

Сообщения передаются в JSON: ключ — id перевода (Long), значение — `Transfer`. Три рабочих топика имеют по 3 партиции и политику compact; они объявлены в `TopicsConfig` каждого сервиса.

| Топик | Кто отправляет | Кто получает (группа потребителей) |
|---|---|---|
| `transfers` | `transfer-service`: событие NEW — `TransferController.publishNew`; итоговый статус — `DecisionTopologyConfig.decisionStream` | `limit-service` — `TransferEventsListener.onEvent` (`limit`); `screening-service` — `TransferEventsListener.onEvent` (`screening`); `transfer-service` — `TransferEventListener.onEvent` (`transfer-tracker`) |
| `limit-results` | `limit-service` — `LimitService.sendAndWait` | `transfer-service` — `DecisionTopologyConfig.decisionStream` (Kafka Streams, группа явно не задана¹) и `TransferEventListener.onEvent` (`transfer-tracker`) |
| `screening-results` | `screening-service` — `ScreeningService.sendAndWait` | те же два потребителя, что у `limit-results` |
| `transfers.DLT`, `limit-results.DLT`, `screening-results.DLT` | обработчик ошибок слушателей `KafkaErrorHandlingConfig.kafkaErrorHandler`: в `transfers.DLT` — все три сервиса, в остальные два — только `transfer-service` | в коде не найдено |

¹ Свойства `spring.kafka.streams.application-id` в `application.yml` нет. По умолчанию Spring Boot подставляет `spring.application.name`, то есть `transfer-service`; требует уточнения на стенде.

**Синхронные вызовы между сервисами** в коде не найдены (HTTP-клиентов, gRPC и подобного нет). Синхронно работают только внешний REST API `transfer-service` и обращения каждого сервиса к своей БД.

## 6. Инфраструктура (по `docker-compose.yml`)

- **Apache Kafka** — `confluentinc/cp-kafka:7.6.0`, один брокер в режиме KRaft (без ZooKeeper). Порт 9092 открыт с хоста, 29092 используется внутри сети compose. Автосоздание топиков отключено, топики создают сами сервисы.
- **PostgreSQL** — `postgres:16-alpine`, три отдельных экземпляра: `transfer-db` (БД `transferdb`, порт хоста 5434), `limit-db` (`limitdb`, 5432), `screening-db` (`screeningdb`, 5433). Схемы описаны в Liquibase-скриптах каждого сервиса; в `screening-db` дополнительно загружаются тестовые заблокированные отправители (`002-insert-test-data.sql`).
- **Локальный диск для состояния Kafka Streams** — каталог `/tmp/transfer-service-streams` (`spring.kafka.streams.state-dir` в `application.yml` сервиса `transfer-service`); том под него в compose не подключён.
- Кэши, реестр схем, service discovery, API-шлюз и мониторинг в коде не найдены.