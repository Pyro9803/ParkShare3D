package com.parkshare.vehicle;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {

    List<Vehicle> findAllByUserIdAndActiveTrue(UUID userId);

    Optional<Vehicle> findByIdAndActiveTrue(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM Vehicle v WHERE v.id = :id AND v.active = true")
    Optional<Vehicle> findByIdAndActiveTrueForUpdate(@Param("id") UUID id);

    boolean existsByUserIdAndLicensePlateAndActiveTrue(UUID userId, String licensePlate);

    boolean existsByUserIdAndLicensePlateAndActiveTrueAndIdNot(UUID userId, String licensePlate, UUID excludeId);
}
