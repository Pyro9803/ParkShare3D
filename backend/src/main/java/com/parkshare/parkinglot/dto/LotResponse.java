package com.parkshare.parkinglot.dto;

import java.time.Instant;
import java.util.UUID;

public record LotResponse(
        UUID id,
        UUID ownerId,
        String name,
        String address,
        String description,
        int floor,
        boolean verified,
        boolean active,
        Instant createdAt
) {
}
