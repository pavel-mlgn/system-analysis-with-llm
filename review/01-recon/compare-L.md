# Сравнение верификаторов: claude и gemini

| Ответ | Оценка claude | Оценка gemini | Разница | Совпадение пунктов | Ошибки claude | Ошибки gemini |
|---|---|---|---|---|---|---|
| L | 5.5 | 4.5 | 1.0 | 23/24 (95%) | 2 | 2 |

**Общее совпадение по пунктам:** 23/24 (95%)

**Порядок claude:** L
**Порядок gemini:** L

## Расхождения по пунктам
- L · K1-S6: claude = есть, gemini = нет

## Ошибки по категориям

**L**
- [claude] мелкая_ошибка: «`transfers.DLT` / `limit-results.DLT` / `screening-results.DLT` | DeadLetterPublishingRecoverer (все сервисы)»
- [claude] неточность: «проводит скоринговую проверку на предмет блокировки»
- [gemini] мелкая_ошибка: «transfers.DLT / limit-results.DLT / screening-results.DLT | DeadLetterPublishingRecoverer (все сервисы)»
- [gemini] неточность: «Оркестратором выступает transfer-service: он принимает REST-запрос, публикует событие transfers, собирает асинхронные от»
