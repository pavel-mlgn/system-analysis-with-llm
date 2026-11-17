# Сравнение верификаторов: claude и gemini

| Ответ | Оценка claude | Оценка gemini | Разница | Совпадение пунктов | Ошибки claude | Ошибки gemini |
|---|---|---|---|---|---|---|
| M | 7.5 | 10.0 | -2.5 | 24/26 (92%) | 4 | 0 |

**Общее совпадение по пунктам:** 24/26 (92%)

**Порядок claude:** M
**Порядок gemini:** M

## Расхождения по пунктам
- M · K4-S2: claude = нет, gemini = есть
- M · K4-S4: claude = нет, gemini = есть

## Ошибки по категориям

**M**
- [claude] мелкая_ошибка: «`state`: NONE → RESERVED (approve) → COMMITTED (markCommitted при COMPLETED) или RELEASED (markReleased при CANCELLED/DE»
- [claude] неточность: «sender_usage ||..o{ reservations : "логически по sender_id, FK нет"»
- [claude] мелкая_ошибка: «TransferRequest ||..|| Transfer : "Transfer.newTransfer в TransferController.publishNew"»
- [claude] ложная_аномалия: «String detail "русские тексты в файле в повреждённой кодировке"»
