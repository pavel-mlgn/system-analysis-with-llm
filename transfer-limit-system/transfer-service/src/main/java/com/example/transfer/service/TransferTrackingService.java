package com.example.transfer.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.common.Transfer;
import com.example.transfer.controller.TransferRequest;
import com.example.transfer.domain.HistoryStage;
import com.example.transfer.domain.TransferEntity;
import com.example.transfer.domain.TransferHistoryEntry;
import com.example.transfer.repository.TransferHistoryRepository;
import com.example.transfer.repository.TransferRepository;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
public class TransferTrackingService {

    private static final Logger LOG = LoggerFactory.getLogger(TransferTrackingService.class);
    private final TransferRepository transferRepository;
    private final TransferHistoryRepository historyRepository;

    public TransferTrackingService(TransferRepository transferRepository,
                                   TransferHistoryRepository historyRepository) {
        this.transferRepository = transferRepository;
        this.historyRepository = historyRepository;
    }

    @Transactional
    public TransferEntity createTransfer(TransferRequest request) {
        LocalDateTime now = now();
        TransferEntity entity = new TransferEntity();
        entity.setId(request.id());
        entity.setSenderId(request.senderId());
        entity.setRecipientId(request.recipientId());
        entity.setAmount(request.amount());
        entity.setCurrency(request.currencyOrDefault());
        entity.setStatus("NEW");
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        transferRepository.saveAndFlush(entity);

        historyRepository.save(historyEntry(entity.getId(), HistoryStage.NEW, "NEW", null, null, now));

        LOG.info("Created transfer {} with status {}", entity.getId(), entity.getStatus());
        return entity;
    }

    @Transactional
    public void recordServiceResult(Transfer result, HistoryStage stage) {
        var entity = transferRepository.findById(result.id());
        if (entity.isEmpty()) {
            LOG.warn("Unknown transfer {} in {} result, skipped", result.id(), stage);
            return;
        }
        if (historyRepository.existsByTransferIdAndStage(result.id(), stage)) {
            LOG.info("Duplicate {} result for transfer {}, skipped", stage, result.id());
            return;
        }
        LocalDateTime now = now();
        historyRepository.save(historyEntry(
                result.id(), stage, result.status(), result.decidedBy(), result.messageInfo(), now));
        entity.get().setUpdatedAt(now);
        transferRepository.save(entity.get());
        LOG.info("Recorded {} result for transfer {}: {}", stage, result.id(), result.status());
    }

    @Transactional
    public void applyFinalStatus(Transfer result) {
        var found = transferRepository.findById(result.id());
        if (found.isEmpty()) {
            LOG.warn("Unknown transfer {} in final status, skipped", result.id());
            return;
        }
        if (historyRepository.existsByTransferIdAndStage(result.id(), HistoryStage.FINAL)) {
            LOG.info("Duplicate final status for transfer {}, skipped", result.id());
            return;
        }
        TransferEntity entity = found.get();
        entity.setStatus(result.status());
        entity.setDecidedBy(result.decidedBy());
        entity.setMessageInfo(result.messageInfo());
        LocalDateTime now = now();
        entity.setUpdatedAt(now);
        transferRepository.save(entity);

        historyRepository.save(historyEntry(
                entity.getId(), HistoryStage.FINAL, result.status(), result.decidedBy(), result.messageInfo(), now));

        LOG.info("Final status {} applied to transfer {}", entity.getStatus(), entity.getId());
    }

    private static LocalDateTime now() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
    }

    private static TransferHistoryEntry historyEntry(Long transferId, HistoryStage stage, String status,
                                                     String decidedBy, String messageInfo, LocalDateTime changedAt) {
        TransferHistoryEntry entry = new TransferHistoryEntry(transferId, stage, status, decidedBy, messageInfo);
        entry.setChangedAt(changedAt);
        return entry;
    }
}
