package com.example.screening.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.common.Transfer;
import com.example.screening.domain.ScreeningCheck;
import com.example.screening.repository.BlockedSenderRepository;
import com.example.screening.repository.ScreeningCheckRepository;

import java.util.Optional;

@Service
public class ScreeningJournal {

    private static final Logger LOG = LoggerFactory.getLogger(ScreeningJournal.class);

    private final BlockedSenderRepository blockedSenderRepository;
    private final ScreeningCheckRepository checkRepository;

    public ScreeningJournal(BlockedSenderRepository blockedSenderRepository,
                            ScreeningCheckRepository checkRepository) {
        this.blockedSenderRepository = blockedSenderRepository;
        this.checkRepository = checkRepository;
    }

    @Transactional
    public ScreeningCheck check(Transfer transfer) {
        Optional<ScreeningCheck> existing = checkRepository.findById(transfer.id());
        if (existing.isPresent()) {
            LOG.info("Duplicate NEW for transfer {}, returning stored check", transfer.id());
            return existing.get();
        }

        var blocked = blockedSenderRepository.findBySenderId(transfer.senderId());
        ScreeningCheck check;
        if (blocked.isPresent()) {
            LOG.info("Sender {} is blocked: {}", transfer.senderId(), blocked.get());
            check = new ScreeningCheck(transfer.id(), transfer.senderId(),
                    ScreeningCheck.DECLINED, "SENDER_BLOCKED");
        } else {
            check = new ScreeningCheck(transfer.id(), transfer.senderId(),
                    ScreeningCheck.APPROVED, null);
        }
        return checkRepository.save(check);
    }
}
