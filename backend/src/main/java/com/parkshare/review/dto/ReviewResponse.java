package com.parkshare.review.dto;

import java.time.Instant;
import java.util.UUID;

public record ReviewResponse(
        UUID id,
        UUID spotId,
        UUID driverId,
        int rating,
        String comment,
        Instant createdAt
) {
}
