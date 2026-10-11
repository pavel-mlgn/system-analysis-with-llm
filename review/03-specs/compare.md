# Сравнение верификаторов: claude и gemini

| Ответ | Оценка claude | Оценка gemini | Разница | Совпадение пунктов | Ошибки claude | Ошибки gemini |
|---|---|---|---|---|---|---|
| A | 10.0 | 10.0 | 0.0 | 24/25 (96%) | 0 | 0 |
| B | 10.0 | 10.0 | 0.0 | 24/25 (96%) | 1 | 1 |
| C | 4.0 | 5.0 | -1.0 | 24/25 (96%) | 5 | 5 |
| D | 10.0 | 10.0 | 0.0 | 24/25 (96%) | 0 | 0 |
| E | 10.0 | 10.0 | 0.0 | 25/25 (100%) | 2 | 1 |

**Общее совпадение по пунктам:** 121/125 (96%)

**Порядок claude:** D > B > A > E > C
**Порядок gemini:** D > B > A > E > C

## Расхождения по пунктам
- A · K3-S2: claude = нет, gemini = есть
- B · K3-S2: claude = нет, gemini = есть
- C · K3-S3: claude = нет, gemini = есть
- D · K3-S2: claude = нет, gemini = есть

## Ошибки по категориям

**A**

**B**
- [claude] неточность: «createdAt | LocalDateTime | нет | Время создания перевода, строка ISO-8601 | "2026-10-02T14:30:15.123456"»
- [gemini] неточность: «Время создания перевода, строка ISO-8601»

**C**
- [claude] нарушение_формата: «| id | body | Long | Да | ID перевода | … | amount | body | BigDecimal | Да | … | createdAt | String (DateTime) | Нет |»
- [claude] неточность: «| createdAt | String(ISO) / LocalDateTime | Нет | Дата-время создания | "2024-01-01T12:00:00" |»
- [claude] мелкая_ошибка: «Канал: transfers.DLT, limit-results.DLT, screening-results.DLT … | Отправители | Все сервисы (через DeadLetterPublishing»
- [claude] ложная_аномалия: «Для поля `amount` не указан параметр формата `minimum: 0` … что в OpenAPI не отражено в `multipleOf` и `maximum`»
- [claude] ложная_аномалия: «Тип `string` со строгим `enum` (NEW, COMPLETED, CANCELLED, DENIED) | Поле `TransferEntity.status` имеет тип `String` … П»
- [gemini] нарушение_формата: «id | body | Long | Да | ID перевода»
- [gemini] ложная_аномалия: «Для поля amount не указан параметр формата minimum: 0»
- [gemini] ложная_аномалия: «Поле TransferEntity.status имеет тип String и не привязано к перечислению в Java»
- [gemini] мелкая_ошибка: «Канал: transfers.DLT, limit-results.DLT, screening-results.DLT ... Отправители | Все сервисы»
- [gemini] неточность: «createdAt | String(ISO) / LocalDateTime»

**D**

**E**
- [claude] ложная_аномалия: «`TransferController.publishNew` выбрасывает `ResponseStatusException` со статусом 503, но `ApiExceptionHandler.handleUne»
- [claude] неточность: «| createdAt | LocalDateTime | нет | Дата и время создания | "2025-01-01T12:00:00" |»
- [gemini] ложная_аномалия: «TransferController.publishNew выбрасывает ResponseStatusException со статусом 503, но ApiExceptionHandler.handleUnexpect»
