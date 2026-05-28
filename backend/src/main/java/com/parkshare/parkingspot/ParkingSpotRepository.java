package com.parkshare.parkingspot;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ParkingSpotRepository extends JpaRepository<ParkingSpot, UUID> {

    List<ParkingSpot> findAllByLotIdAndActiveTrue(UUID lotId);

    boolean existsByLotIdAndCodeAndActiveTrue(UUID lotId, String code);

    boolean existsByLotIdAndCodeAndActiveTrueAndIdNot(UUID lotId, String code, UUID excludeId);
}
