package com.example.limit.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.common.Transfer;
import com.example.limit.domain.Reservation;
import com.example.limit.domain.SenderUsage;
import com.example.limit.repository.ReservationRepository;
import com.example.limit.repository.SenderUsageRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

@Service
public class LimitLedger {

    private static final Logger LOG = LoggerFactory.getLogger(LimitLedger.class);

    private final SenderUsageRepository usageRepository;
    private final ReservationRepository reservationRepository;

    @Value("${limits.daily-max}")
    private BigDecimal dailyMax;

    @Value("${limits.monthly-max}")
    private BigDecimal monthlyMax;

    public LimitLedger(SenderUsageRepository usageRepository, ReservationRepository reservationRepository) {
        this.usageRepository = usageRepository;
        this.reservationRepository = reservationRepository;
    }

    @Transactional
    public Reservation decide(Transfer transfer) {
        Optional<Reservation> existing = reservationRepository.findById(transfer.id());
        if (existing.isPresent()) {
            LOG.info("Duplicate NEW for transfer {}, returning stored decision", transfer.id());
            return existing.get();
        }

        LocalDate today = LocalDate.now();
        BigDecimal amount = transfer.amount();

        if (amount == null || amount.signum() <= 0) {
            Reservation invalid = new Reservation(transfer.id(), transfer.senderId(), BigDecimal.ZERO, today);
            invalid.decline("INVALID_AMOUNT");
            return reservationRepository.save(invalid);
        }

        Reservation reservation = new Reservation(transfer.id(), transfer.senderId(), amount, today);
        SenderUsage usage = usageRepository.findById(transfer.senderId())
                .orElseGet(() -> new SenderUsage(transfer.senderId(), today));
        usage.lazyReset(today);
        LOG.info("Sender {} usage before decision: daily={}, monthly={}",
                usage.getSenderId(), usage.getDailyUsed(), usage.getMonthlyUsed());

        if (usage.getDailyUsed().add(amount).compareTo(dailyMax) > 0) {
            reservation.decline("DAILY_LIMIT_EXCEEDED");
        } else if (usage.getMonthlyUsed().add(amount).compareTo(monthlyMax) > 0) {
            reservation.decline("MONTHLY_LIMIT_EXCEEDED");
        } else {
            usage.reserve(amount);
            reservation.approve();
        }

        usageRepository.save(usage);
        return reservationRepository.save(reservation);
    }

    @Transactional
    public void settle(Transfer transfer) {
        Optional<Reservation> found = reservationRepository.findById(transfer.id());
        if (found.isEmpty()) {
            LOG.warn("No reservation for transfer {}, final status {} skipped", transfer.id(), transfer.status());
            return;
        }
        Reservation reservation = found.get();
        if (!reservation.isReserved()) {
            LOG.info("Transfer {} is in state {}, nothing to settle", transfer.id(), reservation.getState());
            return;
        }

        switch (transfer.status()) {
            case "COMPLETED" -> reservation.markCommitted();
            case "CANCELLED", "DENIED" -> {
                SenderUsage usage = usageRepository.findById(reservation.getSenderId()).orElseThrow();
                usage.release(reservation.getAmount(), reservation.getReservedOn(), LocalDate.now());
                usageRepository.save(usage);
                reservation.markReleased();
            }
            default -> {
                LOG.warn("Unexpected final status {} for transfer {}", transfer.status(), transfer.id());
                return;
            }
        }
        reservationRepository.save(reservation);
        LOG.info("Reservation for transfer {} settled: {}", reservation.getTransferId(), reservation.getState());
    }
}
