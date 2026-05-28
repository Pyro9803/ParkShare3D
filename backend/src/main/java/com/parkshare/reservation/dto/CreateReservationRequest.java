package com.parkshare.reservation.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;

public record CreateReservationRequest(
        @NotNull UUID parkingSpotId,
        @NotNull UUID vehicleId,
        @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
        @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime
) {}
