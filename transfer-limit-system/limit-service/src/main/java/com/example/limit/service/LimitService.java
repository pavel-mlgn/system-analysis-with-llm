package com.example.limit.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import com.example.common.Transfer;
import com.example.limit.domain.Reservation;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class LimitService {

    private static final String DECIDED_BY = "limit";
    private static final Logger LOG = LoggerFactory.getLogger(LimitService.class);
    private final LimitLedger ledger;
    private final KafkaTemplate<Long, Transfer> template;

    public LimitService(LimitLedger ledger, KafkaTemplate<Long, Transfer> template) {
        this.ledger = ledger;
        this.template = template;
    }

    public void onNewTransfer(Transfer transfer) {
        Reservation reservation = ledger.decide(transfer);
        sendAndWait(transfer.withDecision(reservation.getDecision(), DECIDED_BY, reservation.getReason()));
    }

    public void onFinalStatus(Transfer transfer) {
        ledger.settle(transfer);
    }

    private void sendAndWait(Transfer transfer) {
        try {
            template.send("limit-results", transfer.id(), transfer).get(10, TimeUnit.SECONDS);
            LOG.info("Published {} for transfer {}", transfer.status(), transfer.id());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while sending to limit-results", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Failed to send to limit-results", e);
        }
    }
}
