package com.parkshare.reservation.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import com.parkshare.reservation.ReservationStatus;

public record ReservationResponse(
        UUID id,
        UUID spotId,
        UUID vehicleId,
        UUID driverId,
        ReservationStatus status,
        LocalDateTime startTime,
        LocalDateTime endTime,
        BigDecimal totalPrice,
        Instant createdAt
) {}
