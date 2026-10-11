package com.example.transfer.repository;

import org.springframework.data.repository.CrudRepository;
import com.example.transfer.domain.HistoryStage;
import com.example.transfer.domain.TransferHistoryEntry;

import java.util.List;

public interface TransferHistoryRepository extends CrudRepository<TransferHistoryEntry, Long> {
    List<TransferHistoryEntry> findByTransferIdOrderByChangedAtAsc(Long transferId);

    boolean existsByTransferIdAndStage(Long transferId, HistoryStage stage);
}
