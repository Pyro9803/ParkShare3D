package com.parkshare.parkingspot;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpotAvailabilityRepository extends JpaRepository<SpotAvailability, UUID> {

    List<SpotAvailability> findAllBySpotIdAndActiveTrue(UUID spotId);

    Optional<SpotAvailability> findByIdAndActiveTrue(UUID id);

    @Modifying
    @Query("UPDATE SpotAvailability sa SET sa.active = false WHERE sa.spotId = :spotId AND sa.active = true")
    void softDeleteAllBySpotId(@Param("spotId") UUID spotId);
}
