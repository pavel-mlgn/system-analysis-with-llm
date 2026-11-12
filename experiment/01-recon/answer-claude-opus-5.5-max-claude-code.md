# Паспорт системы `transfer-limit-system`

## 1. Назначение

Система приёма и автоматической проверки денежных переводов: клиент передаёт заявку на перевод (отправитель, получатель, сумма, валюта), а система решает, можно ли его провести. Каждый перевод параллельно проходит две проверки — соблюдение дневного и месячного лимитов отправителя (сумма резервируется) и скрининг отправителя по стоп-листу (мошенничество, санкции и т. п.); по совокупности результатов перевод получает итоговый статус `COMPLETED`, `CANCELLED` или `DENIED`, а статус и история этапов доступны через REST API. Фактическое движение средств по счетам (списание/зачисление) в коде не найдено.

## 2. Архитектура

**Стиль:** событийно-ориентированные микросервисы — сервисы взаимодействуют между собой только асинхронно через Apache Kafka по принципу хореографии (сага без оркестратора, с компенсацией — освобождением резерва лимита); у каждого сервиса собственная БД PostgreSQL (database per service).

**Обработка одного запроса:** `transfer-service` принимает `POST /transfers`, сохраняет перевод в статусе `NEW`, публикует событие в Kafka и отвечает клиенту; `limit-service` и `screening-service` параллельно проверяют перевод и публикуют свои решения, которые `transfer-service` объединяет потоковым join (Kafka Streams, окно 10 с) в итоговый статус и снова публикует в Kafka — по нему `limit-service` фиксирует или освобождает резерв, а `transfer-service` обновляет перевод и историю; клиент узнаёт результат через `GET /transfers/{id}`.

## 3. Стек технологий

Java 21, Spring Boot 3.4.1 (`spring-boot-starter-parent`), Spring Web MVC (`spring-boot-starter-web`, только `transfer-service`), Jakarta Bean Validation (`spring-boot-starter-validation`, только `transfer-service`), Spring for Apache Kafka (`spring-kafka`), Apache Kafka Streams (`kafka-streams`, только `transfer-service`), Spring Data JPA / Hibernate (`spring-boot-starter-data-jpa`), PostgreSQL JDBC-драйвер (`org.postgresql:postgresql`), Liquibase (`liquibase-core`), Jackson (`jackson-databind`, `jackson-datatype-jsr310`; JSON-формат сообщений Kafka), SLF4J, OpenAPI 3.0.3 (спецификация `transfer-service/src/main/resources/openapi/transfer-api.yaml`; генерация кода и Swagger UI в сборке не подключены), Maven (многомодульный проект, `spring-boot-maven-plugin`; в Docker-сборке — Maven 3.9.9), Docker (multi-stage: `maven:3.9.9-eclipse-temurin-21` → `eclipse-temurin:21-jre`), Docker Compose, Apache Kafka (`confluentinc/cp-kafka:7.6.0`, режим KRaft), PostgreSQL 16 (`postgres:16-alpine`).

Версии `spring-kafka`, `kafka-streams`, Hibernate, Liquibase, драйвера PostgreSQL и Jackson в файлах явно не указаны — ими управляет `spring-boot-starter-parent` 3.4.1. Тестовые зависимости и тесты в коде не найдены.

## 4. Модули

| Модуль | Тип | Внешний API | Назначение |
|---|---|---|---|
| `transfer-common` | библиотека | нет | Общий контракт сообщений Kafka — record `Transfer` (`transfer-common/src/main/java/com/example/common/Transfer.java`), которым обмениваются все сервисы; внешних зависимостей у модуля нет. |
| `transfer-service` | сервис | есть — REST, порт 8080 (`TransferController`): `POST /transfers`, `GET /transfers`, `GET /transfers/{id}`; аутентификация в коде не найдена | Приём и хранение переводов, публикация их в Kafka, вычисление итогового решения по результатам проверок (`DecisionTopologyConfig`, `TransferDecisionService`) и ведение истории этапов (`TransferTrackingService`). |
| `limit-service` | сервис | нет (только Kafka) | Проверка дневного и месячного лимитов отправителя (`limits.daily-max`, `limits.monthly-max` в `application.yml`) с резервированием суммы и его фиксацией или освобождением по итоговому статусу (`LimitService`, `LimitLedger`). |
| `screening-service` | сервис | нет (только Kafka) | Проверка отправителя по стоп-листу (таблица `blocked_senders`) и публикация результата (`ScreeningService`, `ScreeningJournal`). |

Корневой `pom.xml` (`transfer-limit-system`, packaging `pom`) — только агрегатор модулей, родительским POM для них не является.

## 5. Связи между модулями

**Зависимости сборки (Maven)**
- `transfer-service`, `limit-service` и `screening-service` зависят от `transfer-common` (`com.example:transfer-common:1.0-SNAPSHOT`); сервисы друг от друга на уровне сборки не зависят.
- Сервисы наследуются от `spring-boot-starter-parent` 3.4.1, `transfer-common` родителя не имеет. `Dockerfile` собирает каждый сервис командой `mvn -pl ${MODULE} -am`, т. е. вместе с `transfer-common`.

**Асинхронное взаимодействие — Apache Kafka.** Все сообщения: ключ `Long` (id перевода), значение — `Transfer` из `transfer-common` в JSON. Топики создают сами сервисы при старте (бины `NewTopic` в `TopicsConfig` и `KafkaErrorHandlingConfig`; основные топики объявлены одинаково во всех трёх сервисах): по 3 партиции, у `transfers`, `limit-results`, `screening-results` — `cleanup.policy=compact`.

| Топик | Отправитель | Получатели (группа потребителей) |
|---|---|---|
| `transfers` | `transfer-service`: `TransferController.publishNew` — новый перевод (`NEW`); Kafka Streams-топология `DecisionTopologyConfig.decisionStream` — итоговый статус (`COMPLETED` / `CANCELLED` / `DENIED`) | `limit-service` (группа `limit`) — `TransferEventsListener.onEvent`, обрабатывает и `NEW`, и итоговые статусы; `screening-service` (группа `screening`) — `TransferEventsListener.onEvent`, реагирует только на `NEW` (итоговые статусы только логирует); `transfer-service` (группа `transfer-tracker`) — `TransferEventListener.onEvent`, реагирует только на итоговые статусы |
| `limit-results` | `limit-service`: `LimitService.sendAndWait` — решение по лимиту (`APPROVED` / `DECLINED`) | `transfer-service` (группа `transfer-tracker`) — `TransferEventListener.onEvent`; `transfer-service`, Kafka Streams-join в `DecisionTopologyConfig.decisionStream` (группа явно не задана¹) |
| `screening-results` | `screening-service`: `ScreeningService.sendAndWait` — результат скрининга (`APPROVED` / `DECLINED`) | `transfer-service` (группа `transfer-tracker`) — `TransferEventListener.onEvent`; `transfer-service`, Kafka Streams-join в `DecisionTopologyConfig.decisionStream` (группа явно не задана¹) |
| `transfers.DLT` | все три сервиса: `KafkaErrorHandlingConfig.kafkaErrorHandler` (`DeadLetterPublishingRecoverer`) — сообщения из `transfers`, не обработанные слушателем (`FixedBackOff`: 3 повтора через 1 с) | в коде не найдено |
| `limit-results.DLT`, `screening-results.DLT` | `transfer-service`: `KafkaErrorHandlingConfig.kafkaErrorHandler` — то же для `TransferEventListener` | в коде не найдено |

¹ `spring.kafka.streams.application-id` в `transfer-service/src/main/resources/application.yml` не задан; в этом случае Spring Boot использует в качестве `application.id` (он же группа потребителей Kafka Streams) значение `spring.application.name` = `transfer-service`.

**Синхронные вызовы между сервисами** (HTTP, gRPC и т. п.) в коде не найдены: HTTP-клиентов нет, у `limit-service` и `screening-service` нет веб-зависимостей. Синхронный интерфейс есть только у `transfer-service` для внешних клиентов (REST). Публикация событий через `KafkaTemplate` (`TransferController.publishNew`, `LimitService.sendAndWait`, `ScreeningService.sendAndWait`) синхронно ждёт подтверждения брокера до 10 с; при недоступности Kafka `POST /transfers` возвращает 503.

## 6. Инфраструктура

По `docker-compose.yml` — единственной найденной конфигурации развёртывания (Kubernetes, Helm и т. п. в коде не найдены):

- **Apache Kafka** — `confluentinc/cp-kafka:7.6.0`, один узел в режиме KRaft (broker + controller, без ZooKeeper); адрес для сервисов — `broker:29092`, с хоста — `localhost:9092`; автосоздание топиков отключено (`KAFKA_AUTO_CREATE_TOPICS_ENABLE: "false"`); том `kafka-data`.
- **PostgreSQL 16** (`postgres:16-alpine`) — три отдельных экземпляра, по одному на сервис; схемы создаёт Liquibase при старте сервиса (`db/changelog/changelog-master.yaml`, `spring.jpa.hibernate.ddl-auto: none`):
  - `transfer-db` — БД `transferdb`, порт хоста 5434, для `transfer-service` (таблицы `transfers`, `transfer_history`);
  - `limit-db` — БД `limitdb`, порт хоста 5432, для `limit-service` (`sender_usage`, `reservations`);
  - `screening-db` — БД `screeningdb`, порт хоста 5433, для `screening-service` (`blocked_senders`, `screening_checks`; тестовое наполнение стоп-листа — `002-insert-test-data.sql`);
  - учётные данные `postgres`/`postgres` заданы открытым текстом в `docker-compose.yml` и `application.yml`.
- **Сервисы** — три контейнера из общего `Dockerfile` (аргумент `MODULE`) с профилем `docker` (`application-docker.yml` переключает адреса Kafka и БД на имена контейнеров); стартуют после успешного healthcheck брокера и своей БД; из сервисов наружу опубликован только порт 8080 `transfer-service`. Локальное состояние Kafka Streams — каталог `/tmp/transfer-service-streams` в контейнере `transfer-service`.
- Кэши, service discovery, API-шлюз, config-сервер, мониторинг и метрики — в коде не найдены.
