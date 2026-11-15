# Сравнение верификаторов: claude и gemini

| Ответ | Оценка claude | Оценка gemini | Разница | Совпадение пунктов | Ошибки claude | Ошибки gemini |
|---|---|---|---|---|---|---|
| K | 4.5 | 6.5 | -2.0 | 25/26 (96%) | 4 | 2 |

**Общее совпадение по пунктам:** 25/26 (96%)

**Порядок claude:** K
**Порядок gemini:** K

## Расхождения по пунктам
- K · K4-M3c: claude = нет, gemini = есть

## Ошибки по категориям

**K**
- [claude] искажение: «резерв уже создан и учтён в дневном расходе, поэтому после финального статуса CANCELLED LimitLedger.settle снимает резер»
- [claude] мелкая_ошибка: «TransferRequest ||..|| Transfer : "создаётся в TransferTrackingService.createTransfer"»
- [claude] мелкая_ошибка: «blocked_senders ||..o{ screening_checks : "sender_id (логическая связь)"»
- [claude] неточность: «reservations }o..|| sender_usage : "sender_id (логическая связь)"»
- [gemini] искажение: «проверка лимита отклоняет перевод... резерв уже создан и учтён в дневном расходе, поэтому после финального статуса CANCE»
- [gemini] ложная_аномалия: «Обработка сбоев Kafka Streams внутри decisionStream (конфигурация exception handler для Streams) в коде не найдена»
