package com.parkshare.parkingspot.dto;

import java.time.LocalTime;

import com.parkshare.parkingspot.DayOfWeek;
import jakarta.validation.constraints.NotNull;

public record CreateAvailabilityRequest(
        @NotNull DayOfWeek dayOfWeek,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime
) {
}
