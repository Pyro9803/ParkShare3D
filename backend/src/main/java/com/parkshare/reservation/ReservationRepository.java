package com.parkshare.reservation;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    Page<Reservation> findAllByDriverIdOrderByCreatedAtDesc(UUID driverId, Pageable pageable);

    @Query("SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END FROM Reservation r " +
           "WHERE r.spotId = :spotId " +
           "  AND r.status IN :statuses " +
           "  AND r.startTime < :endTime " +
           "  AND r.endTime > :startTime")
    boolean existsOverlappingReservation(
            @Param("spotId") UUID spotId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("statuses") Collection<ReservationStatus> statuses
    );
}
