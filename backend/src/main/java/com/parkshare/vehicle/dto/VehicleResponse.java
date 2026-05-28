package com.parkshare.vehicle.dto;

import java.util.UUID;

import com.parkshare.parkingspot.VehicleType;

public record VehicleResponse(
        UUID id,
        String licensePlate,
        VehicleType vehicleType,
        String brand,
        String model,
        String color
) {
}
