### АНАЛИЗ ТОЧЕК ВХОДА ПО СЕРВИСАМ

*(Примечание: задачи по расписанию во всех сервисах проекта в коде не найдены).*

---

#### СЕРВИС УПРАВЛЕНИЯ ПЕРЕВОДАМИ (`transfer-service`)

**ТОЧКА ВХОДА: `TransferController.create()`** (`transfer-service/src/main/java/com/example/transfer/controller/TransferController.java`)
* **Триггер:** Входящий HTTP-запрос `POST /transfers` на создание нового перевода.
* **Алгоритм:**
  1. Система проверяет корректность входных данных заявки: наличие идентификатора перевода, идентификатора отправителя, идентификатора получателя и суммы, положительность суммы и её разрядность (не более 13 цифр в целой части и 2 знаков после запятой), а также формат кода валюты (если он передан, то должен состоять из 3 заглавных латинских букв) (`transfer-service/src/main/java/com/example/transfer/controller/TransferRequest.java`, `TransferRequest`).
  2. Если валюта в заявке не указана, переводу по умолчанию назначается валюта `RUB` (`transfer-service/src/main/java/com/example/transfer/controller/TransferRequest.java`, `TransferRequest.currencyOrDefault()`).
  3. Система проверяет, зарегистрирован ли уже перевод с таким идентификатором (`transfer-service/src/main/java/com/example/transfer/controller/TransferController.java`, `TransferController.create()`).
  4. Если перевод с указанным идентификатором уже существует (или был создан параллельным запросом в момент сохранения):
     * Система сверяет параметры существующего перевода и поступившей заявки: отправителя, получателя, числовое значение суммы и валюту (`transfer-service/src/main/java/com/example/transfer/controller/TransferController.java`, `TransferController.sameParameters()`).
     * Если хотя бы один из параметров не совпадает, система отклоняет запрос с ошибкой конфликта данных (`transfer-service/src/main/java/com/example/transfer/controller/TransferController.java`, `TransferController.duplicate()`).
     * Если все параметры совпадают, а перевод всё ещё находится в начальном статусе `NEW`, система повторно отправляет уведомление о новом переводе на проверку и возвращает данные существующего перевода.
     * Если все параметры совпадают, а статус перевода уже изменился (отличен от `NEW`), система не отправляет повторное уведомление и возвращает актуальные данные существующего перевода.
  5. Если перевод с таким идентификатором ранее не регистрировался:
     * Система сохраняет новый перевод в статусе `NEW` с фиксацией текущего времени создания и обновления (`transfer-service/src/main/java/com/example/transfer/service/TransferTrackingService.java`, `TransferTrackingService.createTransfer()`).
     * В журнале истории перевода фиксируется начальный этап `NEW` со статусом `NEW`.
     * Система отправляет уведомление о новом переводе для проведения проверок и ожидает подтверждения отправки не более 10 секунд (`transfer-service/src/main/java/com/example/transfer/controller/TransferController.java`, `TransferController.publishNew()`).
     * Клиенту возвращается созданный перевод.
* **Бизнес-правила:**
  * БП-1: Если в запросе на создание перевода не указан идентификатор перевода, отправитель, получатель или сумма, либо сумма меньше или равна нулю, либо в сумме более 13 цифр до запятой или более 2 после запятой, либо указанный код валюты не состоит ровно из 3 заглавных латинских букв, то запрос отклоняется как некорректный (`transfer-service/src/main/java/com/example/transfer/controller/TransferRequest.java`, `TransferRequest`).
  * БП-2: Если в запросе на создание перевода не указана валюта, то переводу по умолчанию устанавливается валюта `RUB` (`transfer-service/src/main/java/com/example/transfer/controller/TransferRequest.java`, `TransferRequest.currencyOrDefault()`).
  * БП-3: Если перевод с указанным идентификатором уже существует, но хотя бы один из его параметров (отправитель, получатель, числовое значение суммы, валюта) отличается от параметров нового запроса, то возвращается отказ из-за конфликта параметров (`transfer-service/src/main/java/com/example/transfer/controller/TransferController.java`, `TransferController.duplicate()`, `TransferController.sameParameters()`).
  * БП-4: Если поступает повторный запрос на создание перевода с полностью совпадающими параметрами и существующий перевод находится в статусе `NEW`, то уведомление о новом переводе повторно отправляется на проверку, а клиенту возвращаются данные существующего перевода (`transfer-service/src/main/java/com/example/transfer/controller/TransferController.java`, `TransferController.duplicate()`).
  * БП-5: Если поступает повторный запрос на создание перевода с полностью совпадающими параметрами и существующий перевод уже находится в статусе, отличном от `NEW`, то повторное уведомление на проверку не отправляется, а клиенту возвращаются актуальные данные существующего перевода (`transfer-service/src/main/java/com/example/transfer/controller/TransferController.java`, `TransferController.duplicate()`).
  * БП-6: Если перевод сохранён в статусе `NEW`, но отправить уведомление на проверку не удалось (в том числе в течение 10 секунд ожидания), то перевод остаётся сохранённым в базе данных, а клиенту возвращается ошибка временной недоступности с рекомендацией повторить запрос с тем же идентификатором (`transfer-service/src/main/java/com/example/transfer/controller/TransferController.java`, `TransferController.publishNew()`).
* **Данные:**
  * Читается:
    * таблица `transfers`: поля `id`, `sender_id`, `recipient_id`, `amount`, `currency`, `status`, `decided_by`, `message_info`, `created_at`, `updated_at`.
  * Изменяется:
    * таблица `transfers`: создаётся запись (поля `id`, `sender_id`, `recipient_id`, `amount`, `currency`, `status`, `decided_by`, `message_info`, `created_at`, `updated_at`);
    * таблица `transfer_history`: создаётся запись (поля `id`, `transfer_id`, `stage`, `status`, `decided_by`, `message_info`, `changed_at`).
* **Исходы:**
  * `201 Created` — новый перевод успешно зарегистрирован и отправлен на проверку.
  * `200 OK` — перевод с таким идентификатором и теми же параметрами уже существовал (для статуса `NEW` уведомление переотправлено).
  * `400 Bad Request` — входные данные запроса не прошли валидацию.
  * `409 Conflict` — перевод с таким идентификатором уже существует с другими параметрами.
  * `503 Service Unavailable` — перевод сохранён, но не удалось доставить уведомление о новом переводе в очередь проверок.
  * `500 Internal Server Error` — непредвиденная внутренняя ошибка сервера (`transfer-service/src/main/java/com/example/transfer/controller/ApiExceptionHandler.java`, `ApiExceptionHandler.handleUnexpected()`).

---

**ТОЧКА ВХОДА: `TransferController.all()`** (`transfer-service/src/main/java/com/example/transfer/controller/TransferController.java`)
* **Триггер:** Входящий HTTP-запрос `GET /transfers` на получение списка всех переводов.
* **Алгоритм:**
  1. Система извлекает все зарегистрированные переводы.
  2. Список упорядочивается по дате и времени создания перевода от самых новых к самым старым (`transfer-service/src/main/java/com/example/transfer/repository/TransferRepository.java`, `TransferRepository.findAllByOrderByCreatedAtDesc()`).
  3. Сформированный список возвращается клиенту.
* **Бизнес-правила:**
  * БП-7: Если запрашивается список всех переводов, то записи возвращаются отсортированными по времени создания в порядке убывания (сначала самые новые) (`transfer-service/src/main/java/com/example/transfer/controller/TransferController.java`, `TransferController.all()`).
* **Данные:**
  * Читается:
    * таблица `transfers`: поля `id`, `sender_id`, `recipient_id`, `amount`, `currency`, `status`, `decided_by`, `message_info`, `created_at`, `updated_at`.
  * Изменяется: ничего.
* **Исходы:**
  * `200 OK` — возвращён список всех переводов (либо пустой список, если переводов нет).
  * `500 Internal Server Error` — непредвиденная внутренняя ошибка сервера (`transfer-service/src/main/java/com/example/transfer/controller/ApiExceptionHandler.java`, `ApiExceptionHandler.handleUnexpected()`).

---

**ТОЧКА ВХОДА: `TransferController.getById()`** (`transfer-service/src/main/java/com/example/transfer/controller/TransferController.java`)
* **Триггер:** Входящий HTTP-запрос `GET /transfers/{id}` на получение данных конкретного перевода и истории его обработки.
* **Алгоритм:**
  1. Система ищет перевод по переданному идентификатору.
  2. Если перевод не найден, клиенту возвращается ответ об отсутствии запрашиваемого перевода.
  3. Если перевод найден, система извлекает все записи истории прохождения этапов для данного перевода.
  4. Записи истории сортируются по порядку следования этапов (`NEW` → `LIMIT` → `SCREENING` → `FINAL`), а при совпадении этапа — по времени фиксации изменения по возрастанию (`transfer-service/src/main/java/com/example/transfer/controller/TransferController.java`, `TransferController.getById()`, `transfer-service/src/main/java/com/example/transfer/domain/HistoryStage.java`, `HistoryStage`).
  5. Клиенту возвращаются данные перевода вместе с упорядоченным списком записей истории.
* **Бизнес-правила:**
  * БП-8: Если перевод с запрошенным идентификатором отсутствует в системе, то возвращается ответ «Не найдено» (`transfer-service/src/main/java/com/example/transfer/controller/TransferController.java`, `TransferController.getById()`).
  * БП-9: Если перевод найден, то его история возвращается отсортированной по логическому порядку этапов (`NEW`, `LIMIT`, `SCREENING`, `FINAL`), а внутри одного этапа — по времени изменения по возрастанию (`transfer-service/src/main/java/com/example/transfer/controller/TransferController.java`, `TransferController.getById()`).
* **Данные:**
  * Читается:
    * таблица `transfers`: поля `id`, `sender_id`, `recipient_id`, `amount`, `currency`, `status`, `decided_by`, `message_info`, `created_at`, `updated_at`;
    * таблица `transfer_history`: поля `id`, `transfer_id`, `stage`, `status`, `decided_by`, `message_info`, `changed_at`.
  * Изменяется: ничего.
* **Исходы:**
  * `200 OK` — возвращены данные перевода и список этапов его истории.
  * `404 Not Found` — перевод с указанным идентификатором не найден.
  * `400 Bad Request` — передан некорректный формат идентификатора перевода.
  * `500 Internal Server Error` — непредвиденная внутренняя ошибка сервера (`transfer-service/src/main/java/com/example/transfer/controller/ApiExceptionHandler.java`, `ApiExceptionHandler.handleUnexpected()`).

---

**ТОЧКА ВХОДА: `DecisionTopologyConfig.decisionStream()`** (`transfer-service/src/main/java/com/example/transfer/config/DecisionTopologyConfig.java`)
* **Триггер:** Потоковая обработка пар входящих сообщений с одинаковым идентификатором перевода из потоков результатов проверки лимитов (`limit-results`) и результатов проверки скрининга (`screening-results`), поступивших с разницей по времени не более 10 секунд (без дополнительного времени ожидания опоздавших событий).
* **Алгоритм:**
  1. Система сопоставляет результат проверки лимитов и результат проверки скрининга по одному и тому же идентификатору перевода, если разница во времени между ними не превышает 10 секунд (`transfer-service/src/main/java/com/example/transfer/config/DecisionTopologyConfig.java`, `DecisionTopologyConfig.decisionStream()`).
  2. На основании комбинации полученных статусов выносится итоговое решение (`transfer-service/src/main/java/com/example/transfer/service/TransferDecisionService.java`, `TransferDecisionService.decide()`):
     * Если обе проверки одобрили перевод (`APPROVED` и `APPROVED`), переводу присваивается итоговый статус `COMPLETED`, поля принявшей решение стороны и дополнительной информации очищаются (`null`).
     * Если обе проверки отклонили перевод (`DECLINED` и `DECLINED`), переводу присваивается итоговый статус `DENIED`, поле принявшей решение стороны не заполняется (`null`), а причины отказа обеих служб объединяются в одну строку через разделитель `"; "`.
     * Если проверка лимитов отклонила перевод (`DECLINED`), а проверка скрининга одобрила (`APPROVED`), переводу присваивается итоговый статус `CANCELLED`, принявшей решение стороной указывается `limit`, а в качестве причины сохраняется причина отказа службы лимитов.
     * Если проверка лимитов одобрила перевод (`APPROVED`), а проверка скрининга отклонила (`DECLINED`), переводу присваивается итоговый статус `CANCELLED`, принявшей решение стороной указывается `screening`, а в качестве причины сохраняется причина отказа службы скрининга.
     * При получении любых других (неожиданных) статусов от одной или обеих служб переводу присваивается итоговый статус `CANCELLED`, принявшая решение сторона не указывается (`null`), а в причину записывается текст `UNEXPECTED_RESULT: limit=<статус лимитов>, screening=<статус скрининга>`.
  3. Сформированное итоговое решение публикуется в общий поток переводов (`transfers`).
* **Бизнес-правила:**
  * БП-10: Если результаты проверки лимитов и проверки скрининга по одному переводу поступили с разницей во времени не более 10 секунд, то по ним формируется единое итоговое решение (`transfer-service/src/main/java/com/example/transfer/config/DecisionTopologyConfig.java`, `DecisionTopologyConfig.decisionStream()`).
  * БП-11: Если и служба лимитов, и служба скрининга вернули статус `APPROVED`, то перевод получает итоговый статус `COMPLETED` без указания инициатора и причины (`transfer-service/src/main/java/com/example/transfer/service/TransferDecisionService.java`, `TransferDecisionService.decide()`).
  * БП-12: Если и служба лимитов, и служба скрининга вернули статус `DECLINED`, то перевод получает итоговый статус `DENIED`, инициатор не указывается (`null`), а причины обоих отказов объединяются через `"; "` (`transfer-service/src/main/java/com/example/transfer/service/TransferDecisionService.java`, `TransferDecisionService.decide()`).
  * БП-13: Если служба лимитов вернула `DECLINED`, а служба скрининга вернула `APPROVED`, то перевод получает итоговый статус `CANCELLED` с инициатором `limit` и причиной отказа от службы лимитов (`transfer-service/src/main/java/com/example/transfer/service/TransferDecisionService.java`, `TransferDecisionService.decide()`).
  * БП-14: Если служба лимитов вернула `APPROVED`, а служба скрининга вернула `DECLINED`, то перевод получает итоговый статус `CANCELLED` с инициатором `screening` и причиной отказа от службы скрининга (`transfer-service/src/main/java/com/example/transfer/service/TransferDecisionService.java`, `TransferDecisionService.decide()`).
  * БП-15: Если хотя бы одна из служб вернула статус, отличный от `APPROVED` и `DECLINED`, то перевод получает итоговый статус `CANCELLED` без указания инициатора (`null`) и с описанием `UNEXPECTED_RESULT: limit=<статус>, screening=<статус>` (`transfer-service/src/main/java/com/example/transfer/service/TransferDecisionService.java`, `TransferDecisionService.decide()`).
* **Данные:**
  * Читается: таблицы БД не читаются (используется внутреннее хранилище состояний временного окна потока).
  * Изменяется: таблицы БД не изменяются (обновляется внутреннее хранилище состояний временного окна и публикуется итоговое сообщение).
* **Исходы:**
  * Сформировано и опубликовано итоговое решение по переводу (`COMPLETED`, `DENIED` или `CANCELLED`).
  * Итоговое решение не сформировано (если второй ответ не поступил или поступил с разницей более 10 секунд относительно первого).
  * Некорректное (нечитаемое) входящее сообщение пропущено с записью в системный журнал (`transfer-service/src/main/resources/application.yml`, `LogAndContinueExceptionHandler`).

---

**ТОЧКА ВХОДА: `TransferEventListener.onEvent()`** (`transfer-service/src/main/java/com/example/transfer/listener/TransferEventListener.java`)
* **Триггер:** Входящее сообщение из топиков `limit-results`, `screening-results` или `transfers` (для топика `transfers` обрабатываются только сообщения со статусом, отличным от `NEW`).
* **Алгоритм:**
  1. Система определяет источник поступившего события (`transfer-service/src/main/java/com/example/transfer/listener/TransferEventListener.java`, `TransferEventListener.onEvent()`).
  2. **Если поступило сообщение с результатом проверки лимитов (`limit-results`) или скрининга (`screening-results`)** (`transfer-service/src/main/java/com/example/transfer/service/TransferTrackingService.java`, `TransferTrackingService.recordServiceResult()`):
     * Проверяется наличие перевода в базе данных. Если перевод не найден, сообщение игнорируется.
     * Проверяется, записан ли уже в истории перевода соответствующий этап (`LIMIT` или `SCREENING`). Если этап уже зафиксирован, дублирующее сообщение игнорируется.
     * В историю перевода сохраняется запись с этапом (`LIMIT` или `SCREENING`), результатом проверки (`APPROVED`/`DECLINED`), названием проверившей службы и причиной отказа (если есть).
     * В карточке самого перевода обновляется время последнего изменения.
  3. **Если поступило сообщение из потока переводов (`transfers`)**:
     * Если статус в сообщении равен `NEW`, сообщение игнорируется.
     * Если статус отличен от `NEW` (финальное решение) (`transfer-service/src/main/java/com/example/transfer/service/TransferTrackingService.java`, `TransferTrackingService.applyFinalStatus()`):
       * Проверяется наличие перевода в базе данных. Если перевод не найден, сообщение игнорируется.
       * Проверяется, есть ли уже в истории перевода запись для этапа `FINAL`. Если запись уже существует, дублирующее сообщение игнорируется.
       * В карточке перевода обновляются статус, принявшая решение сторона, текст причины/сообщения и время последнего изменения.
       * В историю перевода добавляется запись этапа `FINAL` с финальным статусом, принявшей решение стороной, причиной и временем изменения.
* **Бизнес-правила:**
  * БП-16: Если поступает результат промежуточной проверки (`LIMIT`, `SCREENING`) или финального решения по переводу, идентификатор которого отсутствует в базе данных, то событие пропускается без изменений (`transfer-service/src/main/java/com/example/transfer/service/TransferTrackingService.java`, `TransferTrackingService.recordServiceResult()`, `TransferTrackingService.applyFinalStatus()`).
  * БП-17: Если по переводу повторно поступает событие этапа (`LIMIT`, `SCREENING` или `FINAL`), который уже зафиксирован в истории этого перевода, то повторное событие игнорируется (`transfer-service/src/main/java/com/example/transfer/service/TransferTrackingService.java`, `TransferTrackingService.recordServiceResult()`, `TransferTrackingService.applyFinalStatus()`).
  * БП-18: Если впервые поступает результат проверки лимитов или скрининга по существующему переводу, то в истории создаётся запись соответствующего этапа (`LIMIT` или `SCREENING`), а в самом переводе обновляется только время последнего изменения (`transfer-service/src/main/java/com/example/transfer/service/TransferTrackingService.java`, `TransferTrackingService.recordServiceResult()`).
  * БП-19: Если из основного потока переводов поступает сообщение со статусом `NEW`, то оно игнорируется службой отслеживания (`transfer-service/src/main/java/com/example/transfer/listener/TransferEventListener.java`, `TransferEventListener.onEvent()`).
  * БП-20: Если из основного потока переводов впервые поступает сообщение со статусом, отличным от `NEW`, то перевод переводится в этот финальный статус (с сохранением инициатора и причины), а в истории фиксируется этап `FINAL` (`transfer-service/src/main/java/com/example/transfer/service/TransferTrackingService.java`, `TransferTrackingService.applyFinalStatus()`).
* **Данные:**
  * Читается:
    * таблица `transfers`: поля `id`, `sender_id`, `recipient_id`, `amount`, `currency`, `status`, `decided_by`, `message_info`, `created_at`, `updated_at`;
    * таблица `transfer_history`: проверка наличия записи по полям `transfer_id` и `stage`.
  * Изменяется:
    * таблица `transfers`: при промежуточных результатах обновляется поле `updated_at`; при финальном результате обновляются поля `status`, `decided_by`, `message_info`, `updated_at`;
    * таблица `transfer_history`: добавляется запись с полями `id`, `transfer_id`, `stage` (`LIMIT`, `SCREENING` или `FINAL`), `status`, `decided_by`, `message_info`, `changed_at`.
* **Исходы:**
  * Записан этап `LIMIT` или `SCREENING` в историю и обновлено время изменения перевода.
  * Применён финальный статус к переводу и записан этап `FINAL` в историю.
  * Событие пропущено (статус `NEW`, перевод не найден в БД, либо этап уже был записан ранее).
  * При ошибке обработки (например, сбой БД) после 3 повторных попыток с интервалом 1 секунда сообщение перенаправляется в соответствующую очередь недоставленных сообщений (`transfers.DLT`, `limit-results.DLT` или `screening-results.DLT`) (`transfer-service/src/main/java/com/example/transfer/config/KafkaErrorHandlingConfig.java`, `KafkaErrorHandlingConfig.kafkaErrorHandler()`).

---

#### СЕРВИС КОНТРОЛЯ ЛИМИТОВ (`limit-service`)

**ТОЧКА ВХОДА: `TransferEventsListener.onEvent()`** (`limit-service/src/main/java/com/example/limit/listener/TransferEventsListener.java`)
* **Триггер:** Входящее сообщение о переводе из потока `transfers` (ветвление обработки по условию: статус равен `NEW` или статус отличен от `NEW`).
* **Алгоритм:**
  1. **Если поступил перевод в статусе `NEW` (проверка лимитов и резервирование средств)** (`limit-service/src/main/java/com/example/limit/service/LimitService.java`, `LimitService.onNewTransfer()`, `limit-service/src/main/java/com/example/limit/service/LimitLedger.java`, `LimitLedger.decide()`):
     * Проверяется, есть ли уже сохранённое решение о резервировании для данного идентификатора перевода.
     * Если решение уже есть, повторная проверка не выполняется — сохранённое решение повторно отправляется в поток результатов проверки лимитов.
     * Если перевод проверяется впервые, проверяется корректность его суммы: если сумма не указана или меньше либо равна нулю, сохраняется отказ (`DECLINED`) с причиной `INVALID_AMOUNT`, нулевой суммой и состоянием резерва `NONE`, после чего результат отправляется в поток результатов.
     * Если сумма положительная, система получает текущие показатели использования лимитов отправителем (или создаёт новую карточку учёта с нулевым расходом на текущую дату).
     * Выполняется актуализация периодов учёта на текущую дату (`limit-service/src/main/java/com/example/limit/domain/SenderUsage.java`, `SenderUsage.lazyReset()`):
       * если дата последнего суточного сброса не равна текущей дате, суточный расход обнуляется, а датой последнего суточного сброса становится текущая дата;
       * если год и месяц последнего месячного сброса не совпадают с текущими годом и месяцем, месячный расход обнуляется, а датой последнего месячного сброса становится текущая дата.
     * Сумма перевода сопоставляется с установленными порогами (`limit-service/src/main/resources/application.yml`, суточный максимум — `50 000.00`, месячный максимум — `200 000.00`):
       * если текущий суточный расход плюс сумма перевода превышают `50 000.00`, перевод отклоняется (`DECLINED`) с причиной `DAILY_LIMIT_EXCEEDED`, состояние резерва устанавливается в `NONE`;
       * иначе если текущий месячный расход плюс сумма перевода превышают `200 000.00`, перевод отклоняется (`DECLINED`) с причиной `MONTHLY_LIMIT_EXCEEDED`, состояние резерва устанавливается в `NONE`;
       * иначе сумма перевода прибавляется к суточному и месячному расходу отправителя, переводу выносится решение `APPROVED` (причина `null`), а состояние резерва устанавливается в `RESERVED`.
     * Данные об использовании лимитов и созданном резервировании сохраняются в базе данных.
     * Решение от имени службы `limit` публикуется в поток `limit-results` с ожиданием подтверждения до 10 секунд.
  2. **Если поступил перевод со статусом, отличным от `NEW` (завершение расчётов по резерву)** (`limit-service/src/main/java/com/example/limit/service/LimitService.java`, `LimitService.onFinalStatus()`, `limit-service/src/main/java/com/example/limit/service/LimitLedger.java`, `LimitLedger.settle()`):
     * Система ищет запись о резервировании по идентификатору перевода. Если запись не найдена или её состояние отлично от `RESERVED`, обработка завершается без изменений.
     * Если запись найдена и находится в состоянии `RESERVED`:
       * при статусе перевода `COMPLETED` резерв переводится в состояние `COMMITTED` (средства окончательно засчитаны в использованный лимит) с обновлением времени изменения;
       * при статусе перевода `CANCELLED` или `DENIED` загружаются показатели использования лимитов отправителя, выполняется актуализация суточного и месячного периодов на текущую дату, и если резерв создавался в текущие сутки — сумма возвращается (вычитается, но не ниже нуля) из суточного расхода, а если в текущем месяце — возвращается (не ниже нуля) из месячного расхода (`limit-service/src/main/java/com/example/limit/domain/SenderUsage.java`, `SenderUsage.release()`). Сам резерв переводится в состояние `RELEASED` с обновлением времени изменения;
       * при любом ином статусе перевода выводится предупреждение в журнал и изменения не вносятся.
* **Бизнес-правила:**
  * БП-21: Если в службу лимитов повторно поступает сообщение со статусом `NEW` по переводу, для которого уже сохранено решение о резервировании, то пересчёт лимитов не производится, а ранее принятое решение повторно публикуется в результаты проверки (`limit-service/src/main/java/com/example/limit/service/LimitLedger.java`, `LimitLedger.decide()`, `limit-service/src/main/java/com/example/limit/service/LimitService.java`, `LimitService.onNewTransfer()`).
  * БП-22: Если в службу лимитов поступает новый перевод с отсутствующей, нулевой или отрицательной суммой, то формируется отказ (`DECLINED`) с причиной `INVALID_AMOUNT` и состоянием резерва `NONE` без изменения лимитов отправителя (`limit-service/src/main/java/com/example/limit/service/LimitLedger.java`, `LimitLedger.decide()`).
  * БП-23: Если при обращении к лимитам отправителя текущая календарная дата отличается от даты последнего суточного сброса, то накопленный суточный расход отправителя обнуляется, а дата последнего суточного сброса обновляется на текущую (`limit-service/src/main/java/com/example/limit/domain/SenderUsage.java`, `SenderUsage.lazyReset()`).
  * БП-24: Если при обращении к лимитам отправителя текущий календарный месяц (год и месяц) отличается от месяца последнего месячного сброса, то накопленный месячный расход отправителя обнуляется, а дата последнего месячного сброса обновляется на текущую (`limit-service/src/main/java/com/example/limit/domain/SenderUsage.java`, `SenderUsage.lazyReset()`).
  * БП-25: Если сумма нового перевода вместе с использованным за текущие сутки лимитом отправителя превышает `50 000.00`, то перевод отклоняется службой `limit` (`DECLINED`) с причиной `DAILY_LIMIT_EXCEEDED` и состоянием резерва `NONE` (`limit-service/src/main/java/com/example/limit/service/LimitLedger.java`, `LimitLedger.decide()`).
  * БП-26: Если суточный лимит не превышен, но сумма нового перевода вместе с использованным за текущий месяц лимитом отправителя превышает `200 000.00`, то перевод отклоняется службой `limit` (`DECLINED`) с причиной `MONTHLY_LIMIT_EXCEEDED` и состоянием резерва `NONE` (`limit-service/src/main/java/com/example/limit/service/LimitLedger.java`, `LimitLedger.decide()`).
  * БП-27: Если сумма нового перевода не приводит к превышению ни суточного (`50 000.00`), ни месячного (`200 000.00`) лимитов, то сумма перевода резервируется (увеличивает суточный и месячный расход отправителя), резерв получает состояние `RESERVED`, а перевод одобряется службой `limit` (`APPROVED`) (`limit-service/src/main/java/com/example/limit/service/LimitLedger.java`, `LimitLedger.decide()`).
  * БП-28: Если поступает финальный статус по переводу, для которого нет резерва или резерв находится в состоянии, отличном от `RESERVED`, то урегулирование резерва пропускается (`limit-service/src/main/java/com/example/limit/service/LimitLedger.java`, `LimitLedger.settle()`).
  * БП-29: Если по переводу с резервом в состоянии `RESERVED` поступает финальный статус `COMPLETED`, то состояние резерва меняется на `COMMITTED` (`limit-service/src/main/java/com/example/limit/service/LimitLedger.java`, `LimitLedger.settle()`).
  * БП-30: Если по переводу с резервом в состоянии `RESERVED` поступает финальный статус `CANCELLED` или `DENIED`, то резерв переводится в состояние `RELEASED`, а зарезервированная сумма вычитается из суточного расхода (если дата резерва совпадает с текущей датой) и из месячного расхода (если месяц и год резерва совпадают с текущими), но не ниже `0` (`limit-service/src/main/java/com/example/limit/service/LimitLedger.java`, `LimitLedger.settle()`, `limit-service/src/main/java/com/example/limit/domain/SenderUsage.java`, `SenderUsage.release()`).
  * БП-31: Если по переводу с резервом в состоянии `RESERVED` поступает статус, отличный от `NEW`, `COMPLETED`, `CANCELLED` и `DENIED`, то состояние резерва и расход лимитов не изменяются (`limit-service/src/main/java/com/example/limit/service/LimitLedger.java`, `LimitLedger.settle()`).
* **Данные:**
  * Читается:
    * таблица `reservations`: поля `transfer_id`, `sender_id`, `amount`, `decision`, `reason`, `state`, `reserved_on`, `created_at`, `updated_at`;
    * таблица `sender_usage`: поля `sender_id`, `daily_used`, `monthly_used`, `last_daily_reset`, `last_monthly_reset`, `version`.
  * Изменяется:
    * таблица `reservations`: при `NEW` создаётся запись; при финальном статусе (`COMPLETED`, `CANCELLED`, `DENIED`) обновляются поля `state` и `updated_at`;
    * таблица `sender_usage`: при `NEW` (с положительной суммой) создаётся или обновляется запись (поля `sender_id`, `daily_used`, `monthly_used`, `last_daily_reset`, `last_monthly_reset`, `version`); при `CANCELLED` или `DENIED` обновляются поля `daily_used`, `monthly_used`, `last_daily_reset`, `last_monthly_reset`, `version`.
* **Исходы:**
  * Для `NEW`: решение (`APPROVED` или `DECLINED`) сохранено в БД и опубликовано в `limit-results`.
  * Для `COMPLETED`: резерв переведён в `COMMITTED`.
  * Для `CANCELLED` / `DENIED`: резерв переведён в `RELEASED`, зарезервированная сумма возвращена в доступный лимит.
  * Финальный статус пропущен (нет резерва, резерв не в состоянии `RESERVED` или неизвестный статус).
  * При ошибке обработки (недоступность БД, конфликт версий при параллельном обновлении лимитов, ошибка/таймаут отправки в `limit-results` более 10 секунд) после 3 повторных попыток с интервалом 1 секунда сообщение перенаправляется в `transfers.DLT` (`limit-service/src/main/java/com/example/limit/config/KafkaErrorHandlingConfig.java`, `KafkaErrorHandlingConfig.kafkaErrorHandler()`).

---

#### СЕРВИС СКРИНИНГА (`screening-service`)

**ТОЧКА ВХОДА: `TransferEventsListener.onEvent()`** (`screening-service/src/main/java/com/example/screening/listener/TransferEventsListener.java`)
* **Триггер:** Входящее сообщение о переводе из потока `transfers` (обработка выполняется при статусе `NEW`, остальные статусы игнорируются).
* **Алгоритм:**
  1. Если статус перевода в сообщении отличен от `NEW`, система фиксирует информационное сообщение в журнале и завершает обработку (`screening-service/src/main/java/com/example/screening/listener/TransferEventsListener.java`, `TransferEventsListener.onEvent()`).
  2. Если статус перевода равен `NEW` (`screening-service/src/main/java/com/example/screening/service/ScreeningService.java`, `ScreeningService.onNewTransfer()`, `screening-service/src/main/java/com/example/screening/service/ScreeningJournal.java`, `ScreeningJournal.check()`):
     * Проверяется, выполнялась ли уже проверка по данному идентификатору перевода.
     * Если запись о проверке уже существует в базе, повторная проверка по списку заблокированных не производится — ранее сохранённый результат повторно публикуется в поток результатов скрининга.
     * Если перевод проверяется впервые, система ищет отправителя перевода в списке заблокированных отправителей (`blocked_senders`).
     * Если отправитель найден в списке заблокированных, формируется решение об отказе (`DECLINED`) с причиной `SENDER_BLOCKED`.
     * Если отправитель не найден в списке заблокированных, формируется решение об одобрении (`APPROVED`) без указания причины (`null`).
     * Результат проверки сохраняется в журнале проверок с текущим временем.
     * Результат проверки от имени службы `screening` публикуется в поток `screening-results` с ожиданием подтверждения до 10 секунд.
* **Бизнес-правила:**
  * БП-32: Если в службу скрининга поступает сообщение о переводе со статусом, отличным от `NEW`, то никаких проверок и изменений данных не выполняется (`screening-service/src/main/java/com/example/screening/listener/TransferEventsListener.java`, `TransferEventsListener.onEvent()`).
  * БП-33: Если в службу скрининга повторно поступает сообщение со статусом `NEW` по уже проверенному переводу, то повторный поиск в списке заблокированных не выполняется, а в поток результатов повторно отправляется ранее сохранённый результат (`screening-service/src/main/java/com/example/screening/service/ScreeningJournal.java`, `ScreeningJournal.check()`, `screening-service/src/main/java/com/example/screening/service/ScreeningService.java`, `ScreeningService.onNewTransfer()`).
  * БП-34: Если отправитель нового перевода присутствует в списке заблокированных отправителей, то служба `screening` отклоняет перевод (`DECLINED`) с причиной `SENDER_BLOCKED` (`screening-service/src/main/java/com/example/screening/service/ScreeningJournal.java`, `ScreeningJournal.check()`).
  * БП-35: Если отправитель нового перевода отсутствует в списке заблокированных отправителей, то служба `screening` одобряет перевод (`APPROVED`, причина `null`) (`screening-service/src/main/java/com/example/screening/service/ScreeningJournal.java`, `ScreeningJournal.check()`).
* **Данные:**
  * Читается:
    * таблица `screening_checks`: поля `transfer_id`, `sender_id`, `result`, `reason`, `checked_at`;
    * таблица `blocked_senders`: поля `id`, `sender_id`, `reason`, `blocked_at`.
  * Изменяется:
    * таблица `screening_checks`: при первой проверке перевода создаётся запись с полями `transfer_id`, `sender_id`, `result`, `reason`, `checked_at`.
* **Исходы:**
  * Для `NEW`: результат проверки (`APPROVED` или `DECLINED`) сохранён в БД и опубликован в `screening-results`.
  * Для статуса, отличного от `NEW`: сообщение проигнорировано.
  * При ошибке обработки (сбой БД или ошибка/таймаут отправки в `screening-results` более 10 секунд) после 3 повторных попыток с интервалом 1 секунда сообщение перенаправляется в `transfers.DLT` (`screening-service/src/main/java/com/example/screening/config/KafkaErrorHandlingConfig.java`, `KafkaErrorHandlingConfig.kafkaErrorHandler()`).

---

### СВОДНЫЙ РЕЕСТР

#### 1. ПОЛНЫЙ СПИСОК БИЗНЕС-ПРАВИЛ

* **БП-1:** Если в запросе на создание перевода не указан идентификатор перевода, отправитель, получатель или сумма, либо сумма меньше или равна нулю, либо в сумме более 13 цифр до запятой или более 2 после запятой, либо указанный код валюты не состоит ровно из 3 заглавных латинских букв, то запрос отклоняется как некорректный (`transfer-service/src/main/java/com/example/transfer/controller/TransferRequest.java`, `TransferRequest`).
* **БП-2:** Если в запросе на создание перевода не указана валюта, то переводу по умолчанию устанавливается валюта `RUB` (`transfer-service/src/main/java/com/example/transfer/controller/TransferRequest.java`, `TransferRequest.currencyOrDefault()`).
* **БП-3:** Если перевод с указанным идентификатором уже существует, но хотя бы один из его параметров (отправитель, получатель, числовое значение суммы, валюта) отличается от параметров нового запроса, то возвращается отказ из-за конфликта параметров (`transfer-service/src/main/java/com/example/transfer/controller/TransferController.java`, `TransferController.duplicate()`, `TransferController.sameParameters()`).
* **БП-4:** Если поступает повторный запрос на создание перевода с полностью совпадающими параметрами и существующий перевод находится в статусе `NEW`, то уведомление о новом переводе повторно отправляется на проверку, а клиенту возвращаются данные существующего перевода (`transfer-service/src/main/java/com/example/transfer/controller/TransferController.java`, `TransferController.duplicate()`).
* **БП-5:** Если поступает повторный запрос на создание перевода с полностью совпадающими параметрами и существующий перевод уже находится в статусе, отличном от `NEW`, то повторное уведомление на проверку не отправляется, а клиенту возвращаются актуальные данные существующего перевода (`transfer-service/src/main/java/com/example/transfer/controller/TransferController.java`, `TransferController.duplicate()`).
* **БП-6:** Если перевод сохранён в статусе `NEW`, но отправить уведомление на проверку не удалось (в том числе в течение 10 секунд ожидания), то перевод остаётся сохранённым в базе данных, а клиенту возвращается ошибка временной недоступности с рекомендацией повторить запрос с тем же идентификатором (`transfer-service/src/main/java/com/example/transfer/controller/TransferController.java`, `TransferController.publishNew()`).
* **БП-7:** Если запрашивается список всех переводов, то записи возвращаются отсортированными по времени создания в порядке убывания (сначала самые новые) (`transfer-service/src/main/java/com/example/transfer/controller/TransferController.java`, `TransferController.all()`).
* **БП-8:** Если перевод с запрошенным идентификатором отсутствует в системе, то возвращается ответ «Не найдено» (`transfer-service/src/main/java/com/example/transfer/controller/TransferController.java`, `TransferController.getById()`).
* **БП-9:** Если перевод найден, то его история возвращается отсортированной по логическому порядку этапов (`NEW`, `LIMIT`, `SCREENING`, `FINAL`), а внутри одного этапа — по времени изменения по возрастанию (`transfer-service/src/main/java/com/example/transfer/controller/TransferController.java`, `TransferController.getById()`).
* **БП-10:** Если результаты проверки лимитов и проверки скрининга по одному переводу поступили с разницей во времени не более 10 секунд, то по ним формируется единое итоговое решение (`transfer-service/src/main/java/com/example/transfer/config/DecisionTopologyConfig.java`, `DecisionTopologyConfig.decisionStream()`).
* **БП-11:** Если и служба лимитов, и служба скрининга вернули статус `APPROVED`, то перевод получает итоговый статус `COMPLETED` без указания инициатора и причины (`transfer-service/src/main/java/com/example/transfer/service/TransferDecisionService.java`, `TransferDecisionService.decide()`).
* **БП-12:** Если и служба лимитов, и служба скрининга вернули статус `DECLINED`, то перевод получает итоговый статус `DENIED`, инициатор не указывается (`null`), а причины обоих отказов объединяются через `"; "` (`transfer-service/src/main/java/com/example/transfer/service/TransferDecisionService.java`, `TransferDecisionService.decide()`).
* **БП-13:** Если служба лимитов вернула `DECLINED`, а служба скрининга вернула `APPROVED`, то перевод получает итоговый статус `CANCELLED` с инициатором `limit` и причиной отказа от службы лимитов (`transfer-service/src/main/java/com/example/transfer/service/TransferDecisionService.java`, `TransferDecisionService.decide()`).
* **БП-14:** Если служба лимитов вернула `APPROVED`, а служба скрининга вернула `DECLINED`, то перевод получает итоговый статус `CANCELLED` с инициатором `screening` и причиной отказа от службы скрининга (`transfer-service/src/main/java/com/example/transfer/service/TransferDecisionService.java`, `TransferDecisionService.decide()`).
* **БП-15:** Если хотя бы одна из служб вернула статус, отличный от `APPROVED` и `DECLINED`, то перевод получает итоговый статус `CANCELLED` без указания инициатора (`null`) и с описанием `UNEXPECTED_RESULT: limit=<статус>, screening=<статус>` (`transfer-service/src/main/java/com/example/transfer/service/TransferDecisionService.java`, `TransferDecisionService.decide()`).
* **БП-16:** Если поступает результат промежуточной проверки (`LIMIT`, `SCREENING`) или финального решения по переводу, идентификатор которого отсутствует в базе данных, то событие пропускается без изменений (`transfer-service/src/main/java/com/example/transfer/service/TransferTrackingService.java`, `TransferTrackingService.recordServiceResult()`, `TransferTrackingService.applyFinalStatus()`).
* **БП-17:** Если по переводу повторно поступает событие этапа (`LIMIT`, `SCREENING` или `FINAL`), который уже зафиксирован в истории этого перевода, то повторное событие игнорируется (`transfer-service/src/main/java/com/example/transfer/service/TransferTrackingService.java`, `TransferTrackingService.recordServiceResult()`, `TransferTrackingService.applyFinalStatus()`).
* **БП-18:** Если впервые поступает результат проверки лимитов или скрининга по существующему переводу, то в истории создаётся запись соответствующего этапа (`LIMIT` или `SCREENING`), а в самом переводе обновляется только время последнего изменения (`transfer-service/src/main/java/com/example/transfer/service/TransferTrackingService.java`, `TransferTrackingService.recordServiceResult()`).
* **БП-19:** Если из основного потока переводов поступает сообщение со статусом `NEW`, то оно игнорируется службой отслеживания (`transfer-service/src/main/java/com/example/transfer/listener/TransferEventListener.java`, `TransferEventListener.onEvent()`).
* **БП-20:** Если из основного потока переводов впервые поступает сообщение со статусом, отличным от `NEW`, то перевод переводится в этот финальный статус (с сохранением инициатора и причины), а в истории фиксируется этап `FINAL` (`transfer-service/src/main/java/com/example/transfer/service/TransferTrackingService.java`, `TransferTrackingService.applyFinalStatus()`).
* **БП-21:** Если в службу лимитов повторно поступает сообщение со статусом `NEW` по переводу, для которого уже сохранено решение о резервировании, то пересчёт лимитов не производится, а ранее принятое решение повторно публикуется в результаты проверки (`limit-service/src/main/java/com/example/limit/service/LimitLedger.java`, `LimitLedger.decide()`, `limit-service/src/main/java/com/example/limit/service/LimitService.java`, `LimitService.onNewTransfer()`).
* **БП-22:** Если в службу лимитов поступает новый перевод с отсутствующей, нулевой или отрицательной суммой, то формируется отказ (`DECLINED`) с причиной `INVALID_AMOUNT` и состоянием резерва `NONE` без изменения лимитов отправителя (`limit-service/src/main/java/com/example/limit/service/LimitLedger.java`, `LimitLedger.decide()`).
* **БП-23:** Если при обращении к лимитам отправителя текущая календарная дата отличается от даты последнего суточного сброса, то накопленный суточный расход отправителя обнуляется, а дата последнего суточного сброса обновляется на текущую (`limit-service/src/main/java/com/example/limit/domain/SenderUsage.java`, `SenderUsage.lazyReset()`).
* **БП-24:** Если при обращении к лимитам отправителя текущий календарный месяц (год и месяц) отличается от месяца последнего месячного сброса, то накопленный месячный расход отправителя обнуляется, а дата последнего месячного сброса обновляется на текущую (`limit-service/src/main/java/com/example/limit/domain/SenderUsage.java`, `SenderUsage.lazyReset()`).
* **БП-25:** Если сумма нового перевода вместе с использованным за текущие сутки лимитом отправителя превышает `50 000.00`, то перевод отклоняется службой `limit` (`DECLINED`) с причиной `DAILY_LIMIT_EXCEEDED` и состоянием резерва `NONE` (`limit-service/src/main/java/com/example/limit/service/LimitLedger.java`, `LimitLedger.decide()`).
* **БП-26:** Если суточный лимит не превышен, но сумма нового перевода вместе с использованным за текущий месяц лимитом отправителя превышает `200 000.00`, то перевод отклоняется службой `limit` (`DECLINED`) с причиной `MONTHLY_LIMIT_EXCEEDED` и состоянием резерва `NONE` (`limit-service/src/main/java/com/example/limit/service/LimitLedger.java`, `LimitLedger.decide()`).
* **БП-27:** Если сумма нового перевода не приводит к превышению ни суточного (`50 000.00`), ни месячного (`200 000.00`) лимитов, то сумма перевода резервируется (увеличивает суточный и месячный расход отправителя), резерв получает состояние `RESERVED`, а перевод одобряется службой `limit` (`APPROVED`) (`limit-service/src/main/java/com/example/limit/service/LimitLedger.java`, `LimitLedger.decide()`).
* **БП-28:** Если поступает финальный статус по переводу, для которого нет резерва или резерв находится в состоянии, отличном от `RESERVED`, то урегулирование резерва пропускается (`limit-service/src/main/java/com/example/limit/service/LimitLedger.java`, `LimitLedger.settle()`).
* **БП-29:** Если по переводу с резервом в состоянии `RESERVED` поступает финальный статус `COMPLETED`, то состояние резерва меняется на `COMMITTED` (`limit-service/src/main/java/com/example/limit/service/LimitLedger.java`, `LimitLedger.settle()`).
* **БП-30:** Если по переводу с резервом в состоянии `RESERVED` поступает финальный статус `CANCELLED` или `DENIED`, то резерв переводится в состояние `RELEASED`, а зарезервированная сумма вычитается из суточного расхода (если дата резерва совпадает с текущей датой) и из месячного расхода (если месяц и год резерва совпадают с текущими), но не ниже `0` (`limit-service/src/main/java/com/example/limit/service/LimitLedger.java`, `LimitLedger.settle()`, `limit-service/src/main/java/com/example/limit/domain/SenderUsage.java`, `SenderUsage.release()`).
* **БП-31:** Если по переводу с резервом в состоянии `RESERVED` поступает статус, отличный от `NEW`, `COMPLETED`, `CANCELLED` и `DENIED`, то состояние резерва и расход лимитов не изменяются (`limit-service/src/main/java/com/example/limit/service/LimitLedger.java`, `LimitLedger.settle()`).
* **БП-32:** Если в службу скрининга поступает сообщение о переводе со статусом, отличным от `NEW`, то никаких проверок и изменений данных не выполняется (`screening-service/src/main/java/com/example/screening/listener/TransferEventsListener.java`, `TransferEventsListener.onEvent()`).
* **БП-33:** Если в службу скрининга повторно поступает сообщение со статусом `NEW` по уже проверенному переводу, то повторный поиск в списке заблокированных не выполняется, а в поток результатов повторно отправляется ранее сохранённый результат (`screening-service/src/main/java/com/example/screening/service/ScreeningJournal.java`, `ScreeningJournal.check()`, `screening-service/src/main/java/com/example/screening/service/ScreeningService.java`, `ScreeningService.onNewTransfer()`).
* **БП-34:** Если отправитель нового перевода присутствует в списке заблокированных отправителей, то служба `screening` отклоняет перевод (`DECLINED`) с причиной `SENDER_BLOCKED` (`screening-service/src/main/java/com/example/screening/service/ScreeningJournal.java`, `ScreeningJournal.check()`).
* **БП-35:** Если отправитель нового перевода отсутствует в списке заблокированных отправителей, то служба `screening` одобряет перевод (`APPROVED`, причина `null`) (`screening-service/src/main/java/com/example/screening/service/ScreeningJournal.java`, `ScreeningJournal.check()`).

---

#### 2. ТАБЛИЦЫ РЕШЕНИЙ

##### Таблица решений 1: Вынесение итогового решения по переводу по ответам служб лимитов и скрининга
* **Где в коде:** `transfer-service/src/main/java/com/example/transfer/config/DecisionTopologyConfig.java` (`DecisionTopologyConfig.decisionStream()`) и `transfer-service/src/main/java/com/example/transfer/service/TransferDecisionService.java` (`TransferDecisionService.decide()`).

| № | Разница во времени между ответами `limit` и `screening` | Ответ `limit-service` (`status`) | Ответ `screening-service` (`status`) | Итоговый статус (`status`) | Принявшая решение сторона (`decidedBy`) | Причина / описание (`messageInfo`) |
|---|---|---|---|---|---|---|
| 1 | $\le 10$ сек | `APPROVED` | `APPROVED` | `COMPLETED` | `null` | `null` |
| 2 | $\le 10$ сек | `DECLINED` | `DECLINED` | `DENIED` | `null` | `"<причина limit>; <причина screening>"` |
| 3 | $\le 10$ сек | `DECLINED` | `APPROVED` | `CANCELLED` | `"limit"` | `<причина limit>` |
| 4 | $\le 10$ сек | `APPROVED` | `DECLINED` | `CANCELLED` | `"screening"` | `<причина screening>` |
| 5 | $\le 10$ сек | Любой иной статус (или комбинация не из п. 1–4) | Любой иной статус | `CANCELLED` | `null` | `"UNEXPECTED_RESULT: limit=<статус limit>, screening=<статус screening>"` |
| 6 | $> 10$ сек (или один из ответов не поступил) | Любой | Любой | *Решение не формируется (перевод остаётся в `NEW`)* | — | — |

---

##### Таблица решений 2: Обработка запроса на создание перевода и проверка дубликатов (`POST /transfers`)
* **Где в коде:** `transfer-service/src/main/java/com/example/transfer/controller/TransferController.java` (`TransferController.create()`, `TransferController.duplicate()`, `TransferController.sameParameters()`).

| № | Перевод с таким `id` уже есть в БД? | Все параметры (`senderId`, `recipientId`, `amount`, `currency`) совпадают? | Текущий статус перевода в БД | Доставка события `NEW` в Kafka успешна? | Действие в БД | Отправка в Kafka | HTTP-ответ |
|---|---|---|---|---|---|---|---|
| 1 | Нет | — | — | Да | Создаётся перевод (`NEW`) и запись истории (`NEW`) | Отправляется `NEW` | `201 Created` (тело: созданный перевод) |
| 2 | Нет | — | — | Нет (ошибка / таймаут 10 с) | Создаётся перевод (`NEW`) и запись истории (`NEW`) | Попытка отправки завершилась сбоем | `503 Service Unavailable` |
| 3 | Да | Нет | Любой | — | Без изменений | Не отправляется | `409 Conflict` |
| 4 | Да | Да | `NEW` | Да | Без изменений | Повторно отправляется `NEW` | `200 OK` (тело: существующий перевод) |
| 5 | Да | Да | `NEW` | Нет (ошибка / таймаут 10 с) | Без изменений | Попытка повторной отправки завершилась сбоем | `503 Service Unavailable` |
| 6 | Да | Да | Не `NEW` (`COMPLETED`, `CANCELLED`, `DENIED`) | — | Без изменений | Не отправляется | `200 OK` (тело: существующий перевод) |

---

##### Таблица решений 3: Проверка лимитов и резервирование средств по новому переводу (`NEW`)
* **Где в коде:** `limit-service/src/main/java/com/example/limit/service/LimitLedger.java` (`LimitLedger.decide()`) и `limit-service/src/main/java/com/example/limit/domain/SenderUsage.java` (`SenderUsage.lazyReset()`).

| № | Резерв по `transferId` уже существует? | Сумма перевода (`amount`) | Превышен суточный лимит (после сброса при смене дня): `dailyUsed + amount > 50 000.00`? | Превышен месячный лимит (после сброса при смене месяца): `monthlyUsed + amount > 200 000.00`? | Решение (`decision`) | Причина (`reason`) | Состояние резерва (`state`) | Изменение расхода (`dailyUsed`, `monthlyUsed`) |
|---|---|---|---|---|---|---|---|---|
| 1 | Да | Любая | — | — | Сохранённое ранее | Сохранённая ранее | Сохранённое ранее | Без изменений |
| 2 | Нет | `null` или $\le 0$ | — | — | `DECLINED` | `"INVALID_AMOUNT"` | `NONE` | Без изменений |
| 3 | Нет | $> 0$ | Да | Не проверяется | `DECLINED` | `"DAILY_LIMIT_EXCEEDED"` | `NONE` | Сброс при смене дня/месяца; сумма не прибавляется |
| 4 | Нет | $> 0$ | Нет | Да | `DECLINED` | `"MONTHLY_LIMIT_EXCEEDED"` | `NONE` | Сброс при смене дня/месяца; сумма не прибавляется |
| 5 | Нет | $> 0$ | Нет | Нет | `APPROVED` | `null` | `RESERVED` | Сброс при смене дня/месяца; `dailyUsed += amount`, `monthlyUsed += amount` |

---

##### Таблица решений 4: Урегулирование резерва лимитов при получении финального статуса перевода
* **Где в коде:** `limit-service/src/main/java/com/example/limit/service/LimitLedger.java` (`LimitLedger.settle()`) и `limit-service/src/main/java/com/example/limit/domain/SenderUsage.java` (`SenderUsage.release()`).

| № | Резерв найден в БД? | Текущее состояние резерва (`state`) | Входящий финальный статус перевода | Дата резерва (`reservedOn`) = сегодня? | Месяц и год резерва (`reservedOn`) = текущий месяц и год? | Новое состояние резерва (`state`) | Изменение суточного расхода (`dailyUsed` после `lazyReset`) | Изменение месячного расхода (`monthlyUsed` после `lazyReset`) |
|---|---|---|---|---|---|---|---|---|
| 1 | Нет | — | Любой (не `NEW`) | — | — | — | Без изменений | Без изменений |
| 2 | Да | Не `RESERVED` (`COMMITTED`, `RELEASED`, `NONE`) | Любой (не `NEW`) | — | — | Без изменений | Без изменений | Без изменений |
| 3 | Да | `RESERVED` | `COMPLETED` | — | — | `COMMITTED` | Без изменений | Без изменений |
| 4 | Да | `RESERVED` | `CANCELLED` или `DENIED` | Да | Да | `RELEASED` | $\max(0, \text{dailyUsed} - \text{amount})$ | $\max(0, \text{monthlyUsed} - \text{amount})$ |
| 5 | Да | `RESERVED` | `CANCELLED` или `DENIED` | Нет | Да | `RELEASED` | Без изменений (обнулён при смене дня) | $\max(0, \text{monthlyUsed} - \text{amount})$ |
| 6 | Да | `RESERVED` | `CANCELLED` или `DENIED` | Нет | Нет | `RELEASED` | Без изменений (обнулён при смене дня) | Без изменений (обнулён при смене месяца) |
| 7 | Да | `RESERVED` | Иной (не `NEW`, `COMPLETED`, `CANCELLED`, `DENIED`) | — | — | `RESERVED` (без изменений) | Без изменений | Без изменений |

---

#### 3. РЕЕСТР АНОМАЛИЙ

##### АН-1: Потеря итогового решения и вечная блокировка зарезервированного лимита при задержке одного из сервисов более чем на 10 секунд
* **Описание:** Сопоставление ответов от службы лимитов и службы скрининга выполняется в ограниченном временном окне в 10 секунд без времени ожидания опоздавших сообщений (`JoinWindows.ofTimeDifferenceWithNoGrace(Duration.ofSeconds(10))`), и в системе отсутствует механизм компенсации по таймауту.
* **Сценарий:** Служба лимитов успешно проверила перевод, зарезервировала средства (`RESERVED`) и опубликовала ответ в `limit-results`. В этот момент служба скрининга (или её база данных) кратковременно замедлилась, перезапускалась или сообщение попало в повторные попытки обработки, в результате чего ответ в `screening-results` поступил через 11 секунд (либо сообщение ушло в `transfers.DLT`).
* **Последствие для бизнеса:** Итоговое решение по переводу никогда не будет сформировано. Перевод для клиента навсегда зависнет в статусе `NEW`. Зарезервированная сумма в `limit-service` навсегда останется в состоянии `RESERVED` и не вернётся в доступный суточный и месячный лимит клиента до конца суток/месяца.
* **Серьёзность:** Критичная.
* **Затронутые сервисы:** `transfer-service`, `limit-service`.
* **Где в коде:** `transfer-service/src/main/java/com/example/transfer/config/DecisionTopologyConfig.java`, `DecisionTopologyConfig.decisionStream()`.

---

##### АН-2: Полное игнорирование валюты перевода при проверке и списании лимитов
* **Описание:** API создания перевода позволяет передавать любую трёхбуквенную валюту (`RUB`, `USD`, `EUR` и т. д.), но служба лимитов никак не учитывает валюту перевода, не хранит её в таблицах `sender_usage` и `reservations` и складывает номинальные суммы переводов в разных валютах, сравнивая их с едиными порогами `50 000.00` и `200 000.00`.
* **Сценарий:** Отправитель создаёт перевод на сумму `40 000 USD` (или сначала `10 000 RUB`, а затем `40 000 EUR`).
* **Последствие для бизнеса:** Прямые финансовые потери и нарушение регуляторных/рисковых лимитов: клиент может перевести `50 000 USD/EUR` вместо `50 000 RUB`, либо переводы в разных валютах некорректно уменьшают единый численный остаток лимита без валютной переоценки.
* **Серьёзность:** Критичная.
* **Затронутые сервисы:** `limit-service`, `transfer-service`.
* **Где в коде:** `limit-service/src/main/java/com/example/limit/service/LimitLedger.java` (`LimitLedger.decide()`, `LimitLedger.settle()`), `limit-service/src/main/java/com/example/limit/domain/SenderUsage.java` (`SenderUsage`), `limit-service/src/main/resources/db/changelog/001-create-tables.sql`.

---

##### АН-3: Неатомарность сохранения в БД и публикации в Kafka во всех трёх сервисах (потеря событий и блокировка лимитов)
* **Описание:** Во всех трёх сервисах запись в базу данных фиксируется (коммитится транзакция) до синхронной отправки сообщения в Kafka:
  1. В `transfer-service`: перевод сохраняется в БД в `createTransfer()`, и затем вызывается `publishNew()`. Если приложение аварийно завершится до отправки или клиент не повторит запрос после ошибки `503`, перевод навсегда останется в БД в статусе `NEW` без отправки в Kafka.
  2. В `limit-service`: `ledger.decide()` коммитит резерв (`RESERVED`) и списание лимита в БД до вызова `sendAndWait()` в `limit-results`. Если отправка в `limit-results` падает во всех попытках (например, брокер недоступен дольше времени ретраев), исходное сообщение уходит в `transfers.DLT`, а в БД остаётся закоммиченный резерв, о котором не знает `transfer-service`.
  3. В `screening-service`: `journal.check()` коммитит результат проверки в БД до вызова `sendAndWait()` в `screening-results`.
* **Сценарий:** Кратковременный сетевой сбой или перезапуск брокера Kafka сразу после фиксации транзакции в БД.
* **Последствие для бизнеса:** Зависание переводов в статусе `NEW` и необратимая блокировка суточного/месячного лимита отправителя по зависшим резервам.
* **Серьёзность:** Критичная.
* **Затронутые сервисы:** `transfer-service`, `limit-service`, `screening-service`.
* **Где в коде:**
  * `transfer-service/src/main/java/com/example/transfer/controller/TransferController.java` (`TransferController.create()`, `TransferController.publishNew()`);
  * `limit-service/src/main/java/com/example/limit/service/LimitService.java` (`LimitService.onNewTransfer()`);
  * `screening-service/src/main/java/com/example/screening/service/ScreeningService.java` (`ScreeningService.onNewTransfer()`).

---

##### АН-4: Повторная отправка события `NEW` при идемпотентном HTTP-запросе для перевода, который уже находится в обработке, и размножение финальных решений
* **Описание:** Если перевод уже был успешно опубликован в Kafka при первом вызове `POST /transfers`, но проверки ещё идут (поэтому статус перевода в БД всё ещё `NEW`), повторный вызов `POST /transfers` с тем же `id` и параметрами видит статус `NEW` и безусловно вызывает `publishNew(existing)` повторно. При получении дубликата `NEW` сервисы `limit-service` и `screening-service` повторно публикуют ответы в `limit-results` и `screening-results`. Так как `DecisionTopologyConfig` использует соединение двух потоков (`KStream.join`), каждая новая запись из одного потока соединяется со всеми записями второго потока в пределах 10-секундного окна.
* **Сценарий:** Клиент отправляет два одинаковых запроса `POST /transfers` с интервалом 1 секунда, пока перевод ещё в статусе `NEW`.
* **Последствие для бизнеса:** В `limit-results` и `screening-results` публикуется по 2 сообщения, а потоковое соединение (`KStream-KStream join`) порождает 4 финальных события в топик `transfers`, создавая лишнюю нагрузку на все три сервиса и базы данных.
* **Серьёзность:** Средняя.
* **Затронутые сервисы:** `transfer-service`, `limit-service`, `screening-service`.
* **Где в коде:** `transfer-service/src/main/java/com/example/transfer/controller/TransferController.java` (`TransferController.duplicate()`), `transfer-service/src/main/java/com/example/transfer/config/DecisionTopologyConfig.java` (`DecisionTopologyConfig.decisionStream()`).

---

##### АН-5: Ошибка нарушения уникальности и конфликты блокировок при параллельных переводах одного отправителя в `limit-service`
* **Описание:** Сообщения в топик `transfers` партиционируются по ключу `transfer.id()`, а не по `senderId`. Из-за этого переводы одного и того же отправителя попадают в разные партиции. Если новый отправитель, которого ещё нет в таблице `sender_usage`, отправляет два перевода одновременно, параллельные обработчики (или конкурирующие транзакции) одновременно не находят запись в `sender_usage` и пытаются вставить новую строку с одним `sender_id`, что вызывает ошибку уникальности первичного ключа (`DataIntegrityViolationException`). Кроме того, даже при отказе по лимиту (`DAILY_LIMIT_EXCEEDED` / `MONTHLY_LIMIT_EXCEEDED`) вызывается `usageRepository.save(usage)`, создавая лишние записи и конфликты версий (`@Version`).
* **Сценарий:** Клиент одновременно инициирует несколько переводов (особенно при первом использовании системы).
* **Последствие для бизнеса:** Сбои при обработке сообщений, лишние повторные попытки и риск попадания переводов в `transfers.DLT` (что приведёт к зависанию перевода в статусе `NEW`).
* **Серьёзность:** Средняя.
* **Затронутые сервисы:** `limit-service`, `transfer-service`.
* **Где в коде:** `limit-service/src/main/java/com/example/limit/service/LimitLedger.java` (`LimitLedger.decide()`), `transfer-service/src/main/java/com/example/transfer/controller/TransferController.java` (`TransferController.publishNew()`).

---

##### АН-6: Потеря реальной причины блокировки отправителя при скрининге
* **Описание:** В базе данных `screening-service` в таблице `blocked_senders` хранится конкретная бизнес-причина блокировки каждого отправителя (например, `Fraud detected`, `Sanctions list`, `Court order`). Однако при формировании результата проверки в `ScreeningJournal.check()` это поле не используется, и всем отклонённым переводам записывается одна и та же общая строка `"SENDER_BLOCKED"`.
* **Сценарий:** Заблокированный отправитель (например, `senderId = 13` с причиной `Sanctions list`) пытается совершить перевод.
* **Последствие для бизнеса:** В