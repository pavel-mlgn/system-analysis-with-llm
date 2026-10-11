package com.example.screening.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import com.example.common.Transfer;
import com.example.screening.domain.ScreeningCheck;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class ScreeningService {

    private static final String DECIDED_BY = "screening";
    private static final Logger LOG = LoggerFactory.getLogger(ScreeningService.class);
    private final ScreeningJournal journal;
    private final KafkaTemplate<Long, Transfer> template;

    public ScreeningService(ScreeningJournal journal, KafkaTemplate<Long, Transfer> template) {
        this.journal = journal;
        this.template = template;
    }

    public void onNewTransfer(Transfer transfer) {
        ScreeningCheck check = journal.check(transfer);
        sendAndWait(transfer.withDecision(check.getResult(), DECIDED_BY, check.getReason()));
    }

    private void sendAndWait(Transfer transfer) {
        try {
            template.send("screening-results", transfer.id(), transfer).get(10, TimeUnit.SECONDS);
            LOG.info("Published {} for transfer {}", transfer.status(), transfer.id());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while sending to screening-results", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Failed to send to screening-results", e);
        }
    }
}
