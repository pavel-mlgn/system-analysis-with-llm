package com.example.limit.repository;

import org.springframework.data.repository.CrudRepository;
import com.example.limit.domain.SenderUsage;

public interface SenderUsageRepository extends CrudRepository<SenderUsage, Long> {
}
