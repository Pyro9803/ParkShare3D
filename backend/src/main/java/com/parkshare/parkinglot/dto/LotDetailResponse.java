package com.parkshare.parkinglot.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.parkshare.parkingspot.dto.SpotResponse;

public record LotDetailResponse(
        UUID id,
        UUID ownerId,
        String name,
        String address,
        String description,
        int floor,
        boolean verified,
        boolean active,
        Instant createdAt,
        List<SpotResponse> spots
) {
}
