package io.github.akifacet.inventory_service.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    Optional<Reservation> findByOrderId(Long orderId);
}

