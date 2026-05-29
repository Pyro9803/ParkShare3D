package com.parkshare.reservation.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record CheckInLogResponse(
        UUID id,
        UUID reservationId,
        LocalDateTime checkInTime,
        LocalDateTime checkOutTime,
        int actualDurationMinutes
) {}
