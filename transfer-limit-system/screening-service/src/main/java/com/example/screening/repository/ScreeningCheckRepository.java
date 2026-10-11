package com.example.screening.repository;

import org.springframework.data.repository.CrudRepository;
import com.example.screening.domain.ScreeningCheck;

public interface ScreeningCheckRepository extends CrudRepository<ScreeningCheck, Long> {
}
