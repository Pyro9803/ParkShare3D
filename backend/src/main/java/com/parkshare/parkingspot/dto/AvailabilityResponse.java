package com.parkshare.parkingspot.dto;

import java.time.LocalTime;
import java.util.UUID;

import com.parkshare.parkingspot.DayOfWeek;

public record AvailabilityResponse(
        UUID id,
        UUID spotId,
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        LocalTime endTime
) {
}
