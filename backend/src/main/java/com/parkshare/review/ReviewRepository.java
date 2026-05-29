package com.parkshare.review;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

    boolean existsByReservationId(UUID reservationId);

    Page<Review> findAllBySpotId(UUID spotId, Pageable pageable);

    @Query("SELECT COALESCE(AVG(r.rating), 0.0) FROM Review r WHERE r.spotId = :spotId")
    double findAverageRatingBySpotId(@Param("spotId") UUID spotId);
}
