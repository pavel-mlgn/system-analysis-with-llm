package com.example.limit.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import com.example.common.Transfer;
import com.example.limit.service.LimitService;

@Component
public class TransferEventsListener {

    private static final Logger LOG = LoggerFactory.getLogger(TransferEventsListener.class);

    private final LimitService limitService;

    public TransferEventsListener(LimitService limitService) {
        this.limitService = limitService;
    }

    @KafkaListener(id = "limit-transfers-listener", topics = "transfers", groupId = "limit")
    public void onEvent(Transfer transfer) {
        LOG.info("Received {} for transfer {}", transfer.status(), transfer.id());
        if (Transfer.NEW.equals(transfer.status())) {
            limitService.onNewTransfer(transfer);
        } else {
            limitService.onFinalStatus(transfer);
        }
    }
}
