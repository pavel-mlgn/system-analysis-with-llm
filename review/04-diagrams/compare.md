# Сравнение верификаторов: claude и gemini

| Ответ | Оценка claude | Оценка gemini | Разница | Совпадение пунктов | Ошибки claude | Ошибки gemini |
|---|---|---|---|---|---|---|
| A | 5.0 | 9.0 | -4.0 | 26/26 (100%) | 5 | 0 |
| B | 10.0 | 10.0 | 0.0 | 26/26 (100%) | 0 | 0 |
| C | 10.0 | 10.0 | 0.0 | 26/26 (100%) | 2 | 0 |
| D | 10.0 | 10.0 | 0.0 | 25/26 (96%) | 0 | 0 |
| E | 5.5 | 7.0 | -1.5 | 25/26 (96%) | 4 | 2 |

**Общее совпадение по пунктам:** 128/130 (98%)

**Порядок claude:** B > C > D > E > A
**Порядок gemini:** B > C > D > A > E

## Расхождения по пунктам
- D · K4-S2: claude = нет, gemini = есть
- E · K4-M3a: claude = есть, gemini = нет

## Ошибки по категориям

**A**
- [claude] мелкая_ошибка: «TopicTransfers->>LimitService: "onEvent(Transfer(NEW))" … TopicTransfers->>ScreeningService: "onEvent(Transfer(NEW))"»
- [claude] неточность: «а сервис лимитов возвращает зарезервированные средства»
- [claude] мелкая_ошибка: «TransferRequest ||..|| Transfer : "Maps to via TransferTrackingService"»
- [claude] мелкая_ошибка: «blocked_senders ||..o{ screening_checks : "logical relation (sender_id)"»
- [claude] неточность: «sender_usage ||..o{ reservations : "logical relation (sender_id)"»

**B**

**C**
- [claude] неточность: «на один перевод приходится NEW (возможны повторы), по одному результату от limit и screening и финальное сообщение»
- [claude] нарушение_формата: «Выполненный резерв компенсируется в LimitLedger.settle (SenderUsage.release, Reservation переходит в RELEASED), а перево»

**D**

**E**
- [claude] мелкая_ошибка: «Дополнительно у `Reservation` есть жизненный цикл: `NONE` → `RESERVED` при `approve()`»
- [claude] мелкая_ошибка: «blocked_senders ||..o{ screening_checks : "sender_id логическая"»
- [claude] неточность: «sender_usage ||..o{ reservations : "sender_id логическая"»
- [claude] мелкая_ошибка: «TransferRequest ||..|| Transfer : "REST-запрос создаёт сообщение"»
- [gemini] мелкая_ошибка: «NONE → RESERVED при approve()»
- [gemini] неточность: «sender_usage ||..o{ reservations : "sender_id логическая"»
