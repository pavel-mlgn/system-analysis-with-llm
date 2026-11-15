# Сравнение верификаторов: claude и gemini

| Ответ | Оценка claude | Оценка gemini | Разница | Совпадение пунктов | Ошибки claude | Ошибки gemini |
|---|---|---|---|---|---|---|
| L | 1.0 | 1.0 | 0.0 | 34/35 (97%) | 11 | 6 |

**Общее совпадение по пунктам:** 34/35 (97%)

**Порядок claude:** L
**Порядок gemini:** L

## Расхождения по пунктам
- L · K2-M3b: claude = нет, gemini = есть

## Ошибки по категориям

**L**
- [claude] ложная_аномалия: «Нет блокировки при `amount == null` в `decide()` | Передача null суммы (невозможно при валидации, но если обойти) | Null»
- [claude] ложная_аномалия: «`SenderUsage.reserve()` вызывается до проверки лимитов | Нарушение порядка — счётчик увеличивается даже при отказе»
- [claude] ложная_аномалия: «`lazyReset` не вызывается при `release()` если `reservedOn` != today | Отпуск резерва в другой день — дневной лимит зани»
- [claude] ложная_аномалия: «Нет проверки на отрицательный `amount` при `release()` | Баг или ручная операция — отрицательное освобождение | dailyUse»
- [claude] ложная_аномалия: «`transferRepository.saveAndFlush()` + `historyRepository.save()` не атомарны | Crash после save, но до save history | Пе»
- [claude] ложная_аномалия: «Нет индекса на `reservations.state` | Поиск активных резервов (isReserved) | Full scan при большом объёме»
- [claude] ложная_аномалия: «`createdAt` в `TransferEntity` не уникален | Два перевода с одинаковым ID — разные даты | Нарушение контракта (ID — PK)»
- [claude] неточность: «`DUPLICATE` — решение уже принято.»
- [claude] неточность: «Читается: `transfers` (по ID), `transfer_history` (по ID + стадия).»
- [claude] нарушение_формата: «Вызвать `LimitLedger.decide(transfer)`. … Получить/создать `SenderUsage` для отправителя. … Вызвать `TransferDecisionSer»
- [claude] нарушение_формата: «ЗАМЕЧАНИЯ К АРХИТЕКТУРЕ (вне рамок, но важно) … 3 отдельных БД (transfer, limit, screening) — усложняет распределённые т»
- [gemini] ложная_аномалия: «Нет блокировки при amount == null в decide()»
- [gemini] ложная_аномалия: «SenderUsage.reserve() вызывается до проверки лимитов... счётчик увеличивается даже при отказе... внутри блока else»
- [gemini] ложная_аномалия: «lazyReset не вызывается при release() если reservedOn != today»
- [gemini] ложная_аномалия: «transferRepository.saveAndFlush() + historyRepository.save() не атомарны»
- [gemini] ложная_аномалия: «Нет индекса на reservations.state... Поиск активных резервов (isReserved) Full scan»
- [gemini] ложная_аномалия: «createdAt в TransferEntity не уникален. Два перевода с одинаковым ID — разные даты. Нарушение контракта (ID — PK)»
