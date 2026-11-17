# Сравнение верификаторов: claude и gemini

| Ответ | Оценка claude | Оценка gemini | Разница | Совпадение пунктов | Ошибки claude | Ошибки gemini |
|---|---|---|---|---|---|---|
| M | 10.0 | 10.0 | 0.0 | 34/35 (97%) | 3 | 1 |

**Общее совпадение по пунктам:** 34/35 (97%)

**Порядок claude:** M
**Порядок gemini:** M

## Расхождения по пунктам
- M · K5-S7: claude = нет, gemini = есть

## Ошибки по категориям

**M**
- [claude] ложная_аномалия: «Копировать подход `transfer-service` нежелательно: `ApiExceptionHandler` перехватывает `Exception` и всегда отдаёт 500»
- [claude] ложная_аномалия: «тексты `INTERNAL_ERROR` и `KAFKA_UNAVAILABLE` (`TransferController`) сохранены с нарушенной кодировкой (mojibake)»
- [claude] неточность: «`single_transfer_max` | DECIMAL(15,2) | NOT NULL, CHECK (`> 0`) … минимум строго больше нуля?»
- [gemini] ложная_аномалия: «тексты INTERNAL_ERROR и KAFKA_UNAVAILABLE (TransferController) сохранены с нарушенной кодировкой (mojibake)»
