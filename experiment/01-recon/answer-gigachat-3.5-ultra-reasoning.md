**ПАСПОРТ СИСТЕМЫ: Transfer Limit System**

---

### 1. НАЗНАЧЕНИЕ
Система управляет жизненным циклом денежных переводов между отправителями и получателями. Перед исполнением перевода она проверяет соблюдение персональных лимитов отправителя (дневных и месячных) и проводит скоринговую проверку на предмет блокировки. Решение принимается на основе результатов обеих проверок, финальный статус фиксируется в истории. Бизнес-цель — обеспечить контролируемые, безопасные переводы с соблюдением внутренних политик и регуляторных ограничений.

### 2. АРХИТЕКТУРА
**Event-driven микросервисная архитектура** на базе Apache Kafka. Каждый сервис — независимый Spring Boot-процесс. Оркестратором выступает `transfer-service`: он принимает REST-запрос, публикует событие `transfers`, собирает асинхронные ответы из `limit-results` и `screening-results`, через **Kafka Streams** (топик `transfers`) принимает финальное решение и сохраняет историю. Синхронные взаимодействия между сервисами отсутствуют.

### 3. СТЕК ТЕХНОЛОГИЙ
- **Java 21**, **Maven**
- **Spring Boot 3.4.1** (родительский `pom.xml`)
- **Spring Kafka** (включая Kafka Streams)
- **Spring Data JPA** + **Liquibase**
- **PostgreSQL 16-alpine** (три отдельных БД)
- **Apache Kafka 7.6.0** (образ Confluent)
- **Jackson** (сериализация JSON)

### 4. МОДУЛИ

| Модуль | Тип | Внешний API | Назначение |
|--------|-----|-------------|------------|
| `transfer-service` | Микросервис | REST (POST/GET `/transfers`) | Приём заявок, оркестрация проверок, хранение истории и финальных статусов |
| `limit-service` | Микросервис | Kafka (топик `transfers`) | Проверка дневных/месячных лимитов отправителя, публикация решения в `limit-results` |
| `screening-service` | Микросервис | Kafka (топик `transfers`) | Проверка отправителя на блокировку, публикация результата в `screening-results` |
| `transfer-common` | Библиотека | Нет | Общие модели (`Transfer record`), константы, переиспользуется всеми сервисами |

### 5. СВЯЗИ МЕЖДУ МОДУЛЯМИ

**Сборка** (pom.xml):
- Родительский `transfer-limit-system/pom.xml` объявляет модули: `transfer-service`, `limit-service`, `transfer-common`, `screening-service`.
- `transfer-service`, `limit-service`, `screening-service` зависят от `transfer-common` (общая модель `Transfer`).
- Прямых зависимостей `transfer-service` → `limit-service`/`screening-service` **в сборочных файлах не найдено** — связь только через события.

**Асинхронные каналы (Kafka):**

| Топик | Отправитель | Получатель (groupId) | Назначение |
|-------|-------------|----------------------|------------|
| `transfers` | `transfer-service` (TransferController) | `limit-service` (groupId=`limit`), `screening-service` (groupId=`screening`) | Новый перевод |
| `limit-results` | `limit-service` (LimitService) | `transfer-service` (KStream `DecisionTopologyConfig`, `TransferEventListener`) | Решение лимитов |
| `screening-results` | `screening-service` (ScreeningService) | `transfer-service` (KStream, `TransferEventListener`) | Решение скоринга |
| `transfers.DLT` / `limit-results.DLT` / `screening-results.DLT` | DeadLetterPublishingRecoverer (все сервисы) | — | Ошибочная запись (не обрабатывается) |

**Синхронные вызовы:**
- **REST** только у `transfer-service` (эндпоинты `/transfers`). Остальные сервисы REST не предоставляют.
- **gRPC / HTTP-клиенты** между сервисами — **в коде не найдено**.

### 6. ИНФРАСТРУКТУРА

| Компонент | Версия / Образ | Назначение |
|-----------|----------------|------------|
| **Apache Kafka** | `confluentinc/cp-kafka:7.6.0` | Брокер сообщений, топики, KStream state store |
| **PostgreSQL (transfer)** | `postgres:16-alpine` (port 5434) | БД `transferdb` — переводы, история |
| **PostgreSQL (limit)** | `postgres:16-alpine` (port 5432) | БД `limitdb` — лимиты, резервы |
| **PostgreSQL (screening)** | `postgres:16-alpine` (port 5433) | БД `screeningdb` — заблокированные отправители |
| **Liquibase** | (в classpath) | Миграции схем (файлы `db/changelog/`) |

**Внешние системы / кэш / мониторинг:** в конфигурации развёртывания не указаны. В коде не найдено подключения Redis, Prometheus, Zipkin и т. п.

---

*Источники: родительский `pom.xml`, `docker-compose.yml`, `Dockerfile`, конфигурационные `application.yml` каждого сервиса, классы `TransferController`, `LimitService`, `ScreeningService`, `DecisionTopologyConfig`, `TransferEventListener`, `KafkaErrorHandlingConfig`.*