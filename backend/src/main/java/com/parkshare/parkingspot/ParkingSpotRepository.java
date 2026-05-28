package com.parkshare.parkingspot;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ParkingSpotRepository extends JpaRepository<ParkingSpot, UUID> {

    List<ParkingSpot> findAllByLotIdAndActiveTrue(UUID lotId);

    Optional<ParkingSpot> findByIdAndActiveTrue(UUID id);

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
            "  )")
    // TODO Task 1.8: add AND NOT EXISTS (reservation overlap check) to this query
    Page<ParkingSpot> searchAvailableSpotsByLot(
            @Param("vehicleType") VehicleType vehicleType,
            @Param("dayOfWeek") DayOfWeek dayOfWeek,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime,
            @Param("lotId") UUID lotId,
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
            "  )")
    // TODO Task 1.8: add AND NOT EXISTS (reservation overlap check) to this query
    Page<ParkingSpot> searchAvailableSpots(
            @Param("vehicleType") VehicleType vehicleType,
            @Param("dayOfWeek") DayOfWeek dayOfWeek,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime,
            Pageable pageable
    );
}
