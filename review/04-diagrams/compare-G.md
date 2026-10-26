# Сравнение верификаторов: claude и gemini

| Ответ | Оценка claude | Оценка gemini | Разница | Совпадение пунктов | Ошибки claude | Ошибки gemini |
|---|---|---|---|---|---|---|
| G | 2.0 | 4.0 | -2.0 | 25/26 (96%) | 6 | 3 |

**Общее совпадение по пунктам:** 25/26 (96%)

**Порядок claude:** G
**Порядок gemini:** G

## Расхождения по пунктам
- G · K4-M3b: claude = есть, gemini = нет

## Ошибки по категориям

**G**
- [claude] мелкая_ошибка: «T -->|"Kafka Streams source"| TDL»
- [claude] искажение: «и уже выполненная лимитная резервация компенсируется (освобождение `SenderUsage`, `RESERVED → RELEASED`). Показан полный»
- [claude] мелкая_ошибка: «TRANSFERS ||--|| TRANSFER_HISTORY : "fk: transfer_history.transfer_id → transfers.id"»
- [claude] неточность: «SENDER_USAGE ||--o{ RESERVATIONS»
- [claude] мелкая_ошибка: «BLOCKED_SENDERS ||--o{ SCREENING_CHECKS»
- [claude] нарушение_формата: «SENDER_USAGE ||--o{ RESERVATIONS : "логически: reservations.sender_id (без FK)"»
- [gemini] искажение: «LIMIT_LEDGER.settle(): CASE "CANCELLED" → usage.release(amount, ...) reservation.markReleased() (компенсация резерва)»
- [gemini] мелкая_ошибка: «TRANSFERS ||--|| TRANSFER_HISTORY»
- [gemini] неточность: «SENDER_USAGE ||--o{ RESERVATIONS»
