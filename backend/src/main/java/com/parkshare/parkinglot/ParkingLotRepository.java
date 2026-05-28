package com.parkshare.parkinglot;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParkingLotRepository extends JpaRepository<ParkingLot, UUID> {

    Page<ParkingLot> findAllByActiveTrue(Pageable pageable);

    Page<ParkingLot> findAllByActiveTrueAndVerifiedTrue(Pageable pageable);

    Optional<ParkingLot> findByIdAndActiveTrue(UUID id);
}
