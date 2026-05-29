package com.parkshare.parkinglot.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.parkshare.parkingspot.SpotStatus;
import com.parkshare.parkingspot.VehicleType;

public record SpotMapEntry(
        UUID id,
        String code,
        double x,
        double y,
        double z,
        double width,
        double length,
        VehicleType vehicleType,
        BigDecimal pricePerHour,
        SpotStatus status
) {
}
