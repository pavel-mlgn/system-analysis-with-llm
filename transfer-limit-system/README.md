# transfer-limit-system

Учебная система обработки денежных переводов из трех микросервисов на Java 21 / Spring Boot 3.4, Kafka и PostgreSQL.

## Требования

- Docker с Compose v2.
- Для сборки без Docker - JDK 21 (Maven скачается автоматически через `./mvnw`).

## Запуск

```bash
docker compose up -d --build
```

Первый запуск собирает образы и загружает зависимости - обычно 3–5 минут; последующие запуски занимают около минуты.
Сервисы готовы, когда `docker compose ps` показывает все семь контейнеров в состоянии `running`,
а `curl http://localhost:8080/transfers` возвращает `[]`.

## Пример

Создать перевод:

```bash
curl -s -X POST http://localhost:8080/transfers \
  -H 'Content-Type: application/json' \
  -d '{"id":1001,"senderId":7,"recipientId":8,"amount":1500.00,"currency":"RUB"}'
```

Получить перевод и историю его обработки (через несколько секунд статус станет финальным):

```bash
curl -s http://localhost:8080/transfers/1001
```

Описание API - `transfer-service/src/main/resources/openapi/transfer-api.yaml`.

## Порты

| Сервис | Порт на хосте |
|---|---|
| transfer-service (REST) | 8080 |
| Kafka | 9092 |
| PostgreSQL transfer-db | 5434 |
| PostgreSQL limit-db | 5432 |
| PostgreSQL screening-db | 5433 |

## Остановка

```bash
docker compose down
```

Данные Kafka и PostgreSQL хранятся в томах Docker и переживают остановку. Чтобы удалить и их:

```bash
docker compose down -v
```

Внимание: `down -v` безвозвратно удаляет все переводы, лимиты и топики.

## Сборка без Docker

```bash
./mvnw package
```

Собранные приложения - `*/target/*.jar`; по умолчанию они ожидают Kafka на `localhost:9092` и PostgreSQL на портах из таблицы выше.

## Лицензия

MIT, см. файл `LICENSE`. Исключение - скрипты Maven Wrapper (`mvnw` и `mvnw.cmd`): они распространяются по лицензии Apache 2.0, заголовки лицензии в них сохранены.
