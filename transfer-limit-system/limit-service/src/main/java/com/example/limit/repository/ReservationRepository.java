package com.example.limit.repository;

import org.springframework.data.repository.CrudRepository;
import com.example.limit.domain.Reservation;

public interface ReservationRepository extends CrudRepository<Reservation, Long> {
}
