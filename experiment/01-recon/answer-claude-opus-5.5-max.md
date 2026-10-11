## Паспорт системы transfer-limit-system

### 1. Назначение
Система принимает заявки на денежные переводы (отправитель, получатель, сумма, валюта) и автоматически решает, можно ли их провести. Для этого она проверяет, укладывается ли перевод в дневной и месячный лимиты отправителя и не заблокирован ли отправитель. По итогам обеих проверок перевод получает финальный статус COMPLETED, CANCELLED или DENIED, а клиент может запросить статус и историю проверок. Фактического списания и зачисления средств в коде не найдено: система только решает, допустить ли перевод, и учитывает израсходованные лимиты.

### 2. Архитектура
Событийно-ориентированные микросервисы с собственной БД у каждого сервиса (database per service). Между собой сервисы взаимодействуют только через Kafka по схеме scatter-gather с компенсацией резерва лимита. Обработка одного перевода выглядит так: по REST-запросу transfer-service сохраняет перевод и публикует событие NEW в `transfers` → limit-service и screening-service параллельно проверяют его и пишут результаты в `limit-results` и `screening-results` → transfer-service объединяет их через Kafka Streams (join в окне 10 с) и публикует итоговый статус в `transfers`, по которому transfer-service обновляет перевод, а limit-service фиксирует или освобождает резерв (клиент сразу получает 201 со статусом NEW, а итог видит через `GET /transfers/{id}`).

### 3. Стек технологий
Java 21, Spring Boot 3.4.1, Spring Web MVC и Bean Validation (только transfer-service), Spring for Apache Kafka, Kafka Streams (только transfer-service), Spring Data JPA / Hibernate, Liquibase (formatted SQL), драйвер PostgreSQL, Jackson (+ jackson-datatype-jsr310), Maven 3.9.9 (из образа сборки), Docker (multi-stage, runtime `eclipse-temurin:21-jre`), Docker Compose, Apache Kafka (Confluent `cp-kafka` 7.6.0), PostgreSQL 16, OpenAPI 3.0.3.

Версии Spring Kafka, Kafka Streams, Hibernate, Liquibase, драйвера PostgreSQL и Jackson в pom явно не указаны: их задаёт BOM Spring Boot 3.4.1. Спецификация OpenAPI лежит файлом в ресурсах, а её подключения к сборке (генератор кода, springdoc) в коде не найдено.

### 4. Модули

| Модуль | Тип | Внешний API | Назначение |
|---|---|---|---|
| transfer-common | библиотека | нет | Общий контракт сообщений Kafka: record `com.example.common.Transfer`. |
| transfer-service | сервис | есть: REST, порт 8080 (`POST /transfers`, `GET /transfers`, `GET /transfers/{id}`) | Принимает и хранит переводы, публикует события, выносит итоговое решение по результатам проверок и ведёт историю статусов. |
| limit-service | сервис | нет (только Kafka) | Проверяет дневной и месячный лимиты отправителя (50 000 / 200 000, `limits.*` в `application.yml`), резервирует сумму и по итогу фиксирует или освобождает резерв. |
| screening-service | сервис | нет (только Kafka) | Проверяет отправителя по списку заблокированных (таблица `blocked_senders`). |

Спецификация API находится в `transfer-service/src/main/resources/openapi/transfer-api.yaml`, ошибки возвращаются в формате Problem Details (`ApiExceptionHandler`). API для ведения списка заблокированных в коде не найдено. Список заполняется миграцией `002-insert-test-data.sql` с тестовыми данными, а порядок его ведения в эксплуатации требует уточнения.

### 5. Связи между модулями

**Сборка.** transfer-service, limit-service и screening-service зависят от transfer-common (1.0-SNAPSHOT), других зависимостей между модулями нет. Корневой `pom.xml` служит только агрегатором (`<modules>`), а не родителем: сервисы наследуются от `spring-boot-starter-parent` 3.4.1, а у transfer-common родителя нет.

**Асинхронно (Kafka).** Ключ сообщения — id перевода (Long), значение — `Transfer` в JSON.

| Топик | Отправитель | Получатель (consumer group) |
|---|---|---|
| `transfers` | transfer-service: `TransferController.publishNew` (NEW) и `DecisionTopologyConfig.decisionStream` (COMPLETED / CANCELLED / DENIED) | limit-service (`limit`), screening-service (`screening`, итоговые статусы игнорирует), transfer-service (`transfer-tracker`) |
| `limit-results` | limit-service: `LimitService.onNewTransfer` (APPROVED / DECLINED) | transfer-service: Kafka Streams¹ и `transfer-tracker` |
| `screening-results` | screening-service: `ScreeningService.onNewTransfer` (APPROVED / DECLINED) | transfer-service: Kafka Streams¹ и `transfer-tracker` |
| `transfers.DLT` | `KafkaErrorHandlingConfig` любого из трёх сервисов: сообщения, не обработанные после 3 повторов | в коде не найдено |
| `limit-results.DLT`, `screening-results.DLT` | `KafkaErrorHandlingConfig` transfer-service | в коде не найдено |

¹ `application.id` Kafka Streams явно не задан. По умолчанию Spring Boot берёт `spring.application.name`, то есть `transfer-service`.

Сообщения принимают `TransferEventsListener` в limit-service и screening-service и `TransferEventListener` в transfer-service. Основные топики имеют 3 партиции и `cleanup.policy=compact`, DLT — 3 партиции. Топики создают сами сервисы через бины `NewTopic` (объявления продублированы в `TopicsConfig` каждого сервиса), автосоздание в брокере отключено. Kafka Streams дополнительно создаёт служебные changelog-топики для состояния join.

**Синхронно.** Вызовов между сервисами (HTTP, gRPC и т. п.) в коде не найдено. Единственный синхронный интерфейс — внешний REST API transfer-service.

### 6. Инфраструктура
- Apache Kafka: `confluentinc/cp-kafka:7.6.0`, один узел в режиме KRaft (broker + controller, без ZooKeeper). Внутри сети compose брокер доступен по `broker:29092`, с хоста — по `localhost:9092`.
- PostgreSQL 16 (`postgres:16-alpine`): три независимые БД — `transferdb` (порт хоста 5434), `limitdb` (5432) и `screeningdb` (5433). Схемы создаёт Liquibase при старте сервиса.
- Кэши, Schema Registry и внешние HTTP-системы в коде не найдены.

Развёртывание описано в `docker-compose.yml`. Образы трёх сервисов собираются одним `Dockerfile` (аргумент `MODULE`), а профиль Spring `docker` переключает адреса Kafka и БД на имена контейнеров. Состояние Kafka Streams хранится в `/tmp/transfer-service-streams` внутри контейнера transfer-service, volume для него не подключён.