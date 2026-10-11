package com.example.screening.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import com.example.common.Transfer;
import com.example.screening.service.ScreeningService;

@Component
public class TransferEventsListener {

    private static final Logger LOG = LoggerFactory.getLogger(TransferEventsListener.class);

    private final ScreeningService screeningService;

    public TransferEventsListener(ScreeningService screeningService) {
        this.screeningService = screeningService;
    }

    @KafkaListener(id = "screening-transfers-listener", topics = "transfers", groupId = "screening")
    public void onEvent(Transfer transfer) {
        LOG.info("Received {} for transfer {}", transfer.status(), transfer.id());
        if (Transfer.NEW.equals(transfer.status())) {
            screeningService.onNewTransfer(transfer);
        } else {
            LOG.info("Final status {} for transfer {}: screening holds no reservations, nothing to do",
                    transfer.status(), transfer.id());
        }
    }
}
