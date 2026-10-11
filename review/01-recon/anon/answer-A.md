**ПАСПОРТ СИСТЕМЫ**

**1. НАЗНАЧЕНИЕ**
Система предназначена для обработки финансовых переводов с автоматической многофакторной проверкой. Она осуществляет асинхронный контроль каждой транзакции: проверяет суточные и месячные лимиты отправителя, а также выполняет скрининг участников перевода по спискам блокировки (мошенничество, санкции и т.д.).

**2. АРХИТЕКТУРА**
Использована микросервисная архитектура с событийно-ориентированным взаимодействием (Event-Driven Architecture). При поступлении HTTP-запроса инициирующий сервис публикует событие в брокер сообщений, профильные сервисы параллельно и независимо обрабатывают его и возвращают результаты, которые затем агрегируются (через потоковую обработку Kafka Streams) для принятия итогового решения.

**3. СТЕК ТЕХНОЛОГИЙ**
Java 21, Spring Boot 3.4.1 (Spring Web, Spring Data JPA, Spring Kafka), Apache Kafka Streams, Liquibase, PostgreSQL (16-alpine), Maven 3.9.9, Docker / Docker Compose.

**4. МОДУЛИ**
*   **transfer-common** | Библиотека | Внешний API: нет | Содержит общие классы передачи данных (DTO), такие как рекорд `Transfer`, используемые всеми остальными модулями.
*   **transfer-service** | Сервис | Внешний API: есть (REST, описан в `transfer-api.yaml`) | Является точкой входа для создания переводов, агрегирует результаты проверок и хранит историю жизненного цикла транзакции.
*   **limit-service** | Сервис | Внешний API: нет | Проверяет переводы на превышение суточных и месячных лимитов и управляет резервированием средств на стороне отправителя.
*   **screening-service** | Сервис | Внешний API: нет | Проверяет отправителей по спискам блокировки на основе предопределенных причин (мошенничество, судебные ордера и т.п.).

**5. СВЯЗИ МЕЖДУ МОДУЛЯМИ**
*   **Зависимости на уровне сборки:** 
    Сервисы `transfer-service`, `limit-service` и `screening-service` жестко зависят от библиотеки `transfer-common` (указано в их `pom.xml`).
*   **Асинхронные взаимодействия (брокер сообщений):**
    *   *Топик `transfers`:* Отправляет `transfer-service` (классы `TransferController`, `DecisionTopologyConfig`). Получают `limit-service` (группа `limit` в `TransferEventsListener`), `screening-service` (группа `screening` в `TransferEventsListener`) и `transfer-service` (группа `transfer-tracker` в `TransferEventListener`).
    *   *Топик `limit-results`:* Отправляет `limit-service` (класс `LimitService`). Получает `transfer-service` (через Kafka Streams в `DecisionTopologyConfig` и консьюмер с группой `transfer-tracker` в `TransferEventListener`).
    *   *Топик `screening-results`:* Отправляет `screening-service` (класс `ScreeningService`). Получает `transfer-service` (через Kafka Streams в `DecisionTopologyConfig` и консьюмер с группой `transfer-tracker` в `TransferEventListener`).
*   **Синхронные вызовы:** 
    В коде не найдено (прямые вызовы между микросервисами по HTTP/gRPC отсутствуют, маршрутизация данных происходит исключительно через Kafka).

**6. ИНФРАСТРУКТУРА**
Исходя из конфигурации `docker-compose.yml`, для работы системы требуются:
*   Брокер сообщений: **Apache Kafka** (используется образ `confluentinc/cp-kafka:7.6.0`, настроен в режиме KRaft без Zookeeper).
*   Базы данных: **PostgreSQL 16** (используется образ `postgres:16-alpine`). Развернуто три независимых контейнера с отдельными БД: `transferdb`, `limitdb`, `screeningdb`.