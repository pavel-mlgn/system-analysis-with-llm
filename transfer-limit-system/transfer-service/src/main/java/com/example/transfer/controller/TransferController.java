package com.example.transfer.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import com.example.common.Transfer;
import com.example.transfer.domain.TransferEntity;
import com.example.transfer.domain.TransferHistoryEntry;
import com.example.transfer.repository.TransferHistoryRepository;
import com.example.transfer.repository.TransferRepository;
import com.example.transfer.service.TransferTrackingService;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@RestController
@RequestMapping("/transfers")
public class TransferController {

    private static final Logger LOG = LoggerFactory.getLogger(TransferController.class);
    private static final String STATUS_NEW = "NEW";
    private static final String KAFKA_UNAVAILABLE =
            "Kafka недоступна, перевод сохранён — повторите запрос с тем же id";

    private final KafkaTemplate<Long, Transfer> template;
    private final TransferRepository transferRepository;
    private final TransferHistoryRepository historyRepository;
    private final TransferTrackingService trackingService;

    public TransferController(KafkaTemplate<Long, Transfer> template,
                              TransferRepository transferRepository,
                              TransferHistoryRepository historyRepository,
                              TransferTrackingService trackingService) {
        this.template = template;
        this.transferRepository = transferRepository;
        this.historyRepository = historyRepository;
        this.trackingService = trackingService;
    }

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody TransferRequest request) {
        var existing = transferRepository.findById(request.id());
        if (existing.isPresent()) {
            return duplicate(existing.get(), request);
        }

        TransferEntity entity;
        try {
            entity = trackingService.createTransfer(request);
        } catch (DataIntegrityViolationException e) {
            return transferRepository.findById(request.id())
                    .map(found -> duplicate(found, request))
                    .orElseThrow(() -> e);
        }

        publishNew(entity);
        return ResponseEntity.status(HttpStatus.CREATED).body(entity);
    }

    @GetMapping
    public List<TransferEntity> all() {
        return transferRepository.findAllByOrderByCreatedAtDesc();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getById(@PathVariable Long id) {
        var transfer = transferRepository.findById(id);
        if (transfer.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        List<TransferHistoryEntry> history = historyRepository.findByTransferIdOrderByChangedAtAsc(id).stream()
                .sorted(Comparator.comparing(TransferHistoryEntry::getStage)
                        .thenComparing(TransferHistoryEntry::getChangedAt))
                .toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("transfer", transfer.get());
        response.put("history", history);
        return ResponseEntity.ok(response);
    }

    private ResponseEntity<?> duplicate(TransferEntity existing, TransferRequest request) {
        if (!sameParameters(existing, request)) {
            LOG.warn("Transfer id={} already exists with other parameters", request.id());
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                    "Перевод с id=" + request.id() + " уже существует с другими параметрами");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
        }
        if (STATUS_NEW.equals(existing.getStatus())) {
            publishNew(existing);
        }
        LOG.info("Duplicate transfer id={}, returning existing", request.id());
        return ResponseEntity.ok(existing);
    }

    private boolean sameParameters(TransferEntity existing, TransferRequest request) {
        return existing.getSenderId().equals(request.senderId())
                && existing.getRecipientId().equals(request.recipientId())
                && existing.getAmount().compareTo(request.amount()) == 0
                && existing.getCurrency().equals(request.currencyOrDefault());
    }

    private void publishNew(TransferEntity entity) {
        Transfer message = Transfer.newTransfer(entity.getId(), entity.getSenderId(), entity.getRecipientId(),
                entity.getAmount(), entity.getCurrency(), entity.getCreatedAt());
        try {
            template.send("transfers", message.id(), message).get(10, TimeUnit.SECONDS);
            LOG.info("Published {} for transfer {}", message.status(), message.id());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, KAFKA_UNAVAILABLE, e);
        } catch (ExecutionException | TimeoutException | KafkaException | org.apache.kafka.common.KafkaException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, KAFKA_UNAVAILABLE, e);
        }
    }
}
