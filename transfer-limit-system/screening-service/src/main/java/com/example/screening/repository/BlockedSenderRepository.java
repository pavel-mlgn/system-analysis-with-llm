package com.example.screening.repository;

import org.springframework.data.repository.CrudRepository;
import com.example.screening.domain.BlockedSender;

import java.util.Optional;

public interface BlockedSenderRepository extends CrudRepository<BlockedSender, Long> {
    Optional<BlockedSender> findBySenderId(Long senderId);
}
