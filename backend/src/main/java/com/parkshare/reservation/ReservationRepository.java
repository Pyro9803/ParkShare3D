package com.parkshare.reservation;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Modifying;
import java.time.Instant;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    @Modifying
    @Query("UPDATE Reservation r SET r.status = com.parkshare.reservation.ReservationStatus.EXPIRED, " +
           "r.updatedAt = :now " +
           "WHERE r.status = com.parkshare.reservation.ReservationStatus.RESERVED " +
           "  AND r.startTime < :cutoff")
    int expireStaleReservations(@Param("cutoff") LocalDateTime cutoff, @Param("now") Instant now);

    @Modifying
    @Query("UPDATE Reservation r SET r.status = com.parkshare.reservation.ReservationStatus.NO_SHOW, " +
           "r.updatedAt = :now " +
           "WHERE r.status = com.parkshare.reservation.ReservationStatus.CHECKED_IN " +
           "  AND r.endTime < :cutoff")
    int markNoShowReservations(@Param("cutoff") LocalDateTime cutoff, @Param("now") Instant now);

    @Query("SELECT r.spotId FROM Reservation r " +
           "WHERE r.spotId IN (SELECT s.id FROM ParkingSpot s WHERE s.lotId = :lotId) " +
           "  AND r.status = com.parkshare.reservation.ReservationStatus.CHECKED_IN " +
           "  AND r.startTime <= :now AND r.endTime > :now")
    List<UUID> findOccupiedSpotIdsForLot(@Param("lotId") UUID lotId, @Param("now") LocalDateTime now);

    @Query("SELECT r.spotId FROM Reservation r " +
           "WHERE r.spotId IN (SELECT s.id FROM ParkingSpot s WHERE s.lotId = :lotId) " +
           "  AND r.status = com.parkshare.reservation.ReservationStatus.RESERVED " +
           "  AND r.startTime <= :now AND r.endTime > :now")
    List<UUID> findPendingSpotIdsForLot(@Param("lotId") UUID lotId, @Param("now") LocalDateTime now);

    Page<Reservation> findAllByDriverIdOrderByCreatedAtDesc(UUID driverId, Pageable pageable);

    Page<Reservation> findAllByDriverIdAndStatusOrderByCreatedAtDesc(UUID driverId, ReservationStatus status, Pageable pageable);

    @Query("SELECT r FROM Reservation r WHERE r.spotId IN " +
           "(SELECT s.id FROM ParkingSpot s WHERE s.lotId = :lotId) " +
           "ORDER BY r.createdAt DESC")
    Page<Reservation> findAllByLotId(@Param("lotId") UUID lotId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Reservation r WHERE r.id = :id")
    Optional<Reservation> findByIdForUpdate(@Param("id") UUID id);

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

    boolean existsByVehicleIdAndStatusIn(UUID vehicleId, Collection<ReservationStatus> statuses);
}
