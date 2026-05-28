package com.parkshare.parkinglot.dto;

import java.math.BigDecimal;

import com.parkshare.parkingspot.VehicleType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateSpotRequest(
        @NotBlank String code,
        double x,
        double y,
        double z,
        @Positive double width,
        @Positive double length,
        @NotNull VehicleType vehicleType,
        @NotNull @Positive BigDecimal pricePerHour
) {
}
