package com.parkshare.parkingspot;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.parkshare.reservation.ReservationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ParkingSpotRepository extends JpaRepository<ParkingSpot, UUID> {

    long countByActiveTrue();

    List<ParkingSpot> findAllByLotId(UUID lotId);

    List<ParkingSpot> findAllByLotIdAndActiveTrue(UUID lotId);

    Optional<ParkingSpot> findByIdAndActiveTrue(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ParkingSpot s WHERE s.id = :id AND s.active = true")
    Optional<ParkingSpot> findByIdAndActiveTrueForUpdate(@Param("id") UUID id);

    boolean existsByLotIdAndCodeAndActiveTrue(UUID lotId, String code);

    boolean existsByLotIdAndCodeAndActiveTrueAndIdNot(UUID lotId, String code, UUID excludeId);

    @Query("SELECT s FROM ParkingSpot s " +
            "WHERE s.active = true " +
            "  AND s.lotId = :lotId " +
            "  AND s.vehicleType = :vehicleType " +
            "  AND EXISTS (" +
            "    SELECT 1 FROM ParkingLot l " +
            "    WHERE l.id = s.lotId " +
            "      AND l.active = true " +
            "      AND l.verified = true " +
            "  ) " +
            "  AND EXISTS (" +
            "    SELECT 1 FROM SpotAvailability sa " +
            "    WHERE sa.spotId = s.id " +
            "      AND sa.active = true " +
            "      AND sa.dayOfWeek = :dayOfWeek " +
            "      AND sa.startTime <= :startTime " +
            "      AND sa.endTime >= :endTime " +
            "  ) " +
            "  AND NOT EXISTS (" +
            "    SELECT 1 FROM Reservation r " +
            "    WHERE r.spotId = s.id " +
            "      AND r.status IN :activeStatuses " +
            "      AND r.startTime < :endDateTime " +
            "      AND r.endTime > :startDateTime " +
            "  )")
    Page<ParkingSpot> searchAvailableSpotsByLot(
            @Param("vehicleType") VehicleType vehicleType,
            @Param("dayOfWeek") DayOfWeek dayOfWeek,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime,
            @Param("lotId") UUID lotId,
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime,
            @Param("activeStatuses") Collection<ReservationStatus> activeStatuses,
            Pageable pageable
    );

    @Query("SELECT s FROM ParkingSpot s " +
            "WHERE s.active = true " +
            "  AND s.vehicleType = :vehicleType " +
            "  AND EXISTS (" +
            "    SELECT 1 FROM ParkingLot l " +
            "    WHERE l.id = s.lotId " +
            "      AND l.active = true " +
            "      AND l.verified = true " +
            "  ) " +
            "  AND EXISTS (" +
            "    SELECT 1 FROM SpotAvailability sa " +
            "    WHERE sa.spotId = s.id " +
            "      AND sa.active = true " +
            "      AND sa.dayOfWeek = :dayOfWeek " +
            "      AND sa.startTime <= :startTime " +
            "      AND sa.endTime >= :endTime " +
            "  ) " +
            "  AND NOT EXISTS (" +
            "    SELECT 1 FROM Reservation r " +
            "    WHERE r.spotId = s.id " +
            "      AND r.status IN :activeStatuses " +
            "      AND r.startTime < :endDateTime " +
            "      AND r.endTime > :startDateTime " +
            "  )")
    Page<ParkingSpot> searchAvailableSpots(
            @Param("vehicleType") VehicleType vehicleType,
            @Param("dayOfWeek") DayOfWeek dayOfWeek,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime,
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime,
            @Param("activeStatuses") Collection<ReservationStatus> activeStatuses,
            Pageable pageable
    );
}
