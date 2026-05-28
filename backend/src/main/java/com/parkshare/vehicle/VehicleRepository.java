package com.parkshare.vehicle;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {

    List<Vehicle> findAllByUserIdAndActiveTrue(UUID userId);

    Optional<Vehicle> findByIdAndActiveTrue(UUID id);

    boolean existsByUserIdAndLicensePlateAndActiveTrue(UUID userId, String licensePlate);

    boolean existsByUserIdAndLicensePlateAndActiveTrueAndIdNot(UUID userId, String licensePlate, UUID excludeId);
}
