package com.example.transfer.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.example.transfer.domain.TransferEntity;

import java.util.List;

public interface TransferRepository extends JpaRepository<TransferEntity, Long> {
    List<TransferEntity> findAllByOrderByCreatedAtDesc();
}
