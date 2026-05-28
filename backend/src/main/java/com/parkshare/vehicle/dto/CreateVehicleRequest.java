package com.parkshare.vehicle.dto;

import com.parkshare.parkingspot.VehicleType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CreateVehicleRequest(
        @NotBlank
        @Pattern(regexp = "^\\d{2,3}[A-Z]-\\d{4,5}$", message = "License plate must match Vietnamese format (e.g. 51A-12345)")
        String licensePlate,

        @NotNull
        VehicleType vehicleType,

        String brand,
        String model,
        String color
) {
}
