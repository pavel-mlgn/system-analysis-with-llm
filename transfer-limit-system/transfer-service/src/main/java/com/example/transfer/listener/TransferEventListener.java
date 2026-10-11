package com.example.transfer.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import com.example.common.Transfer;
import com.example.transfer.domain.HistoryStage;
import com.example.transfer.service.TransferTrackingService;

@Component
public class TransferEventListener {

    private static final Logger LOG = LoggerFactory.getLogger(TransferEventListener.class);
    private final TransferTrackingService trackingService;

    public TransferEventListener(TransferTrackingService trackingService) {
        this.trackingService = trackingService;
    }

    @KafkaListener(id = "transfer-tracker",
            topics = {"transfers", "limit-results", "screening-results"},
            groupId = "transfer-tracker")
    public void onEvent(Transfer transfer, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        LOG.info("Received {} for transfer {} from {}", transfer.status(), transfer.id(), topic);
        switch (topic) {
            case "limit-results" -> trackingService.recordServiceResult(transfer, HistoryStage.LIMIT);
            case "screening-results" -> trackingService.recordServiceResult(transfer, HistoryStage.SCREENING);
            case "transfers" -> {
                if (!Transfer.NEW.equals(transfer.status())) {
                    trackingService.applyFinalStatus(transfer);
                }
            }
            default -> LOG.warn("Unexpected topic {}", topic);
        }
    }
}
