package com.parkshare.parkingspot.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.parkshare.parkingspot.VehicleType;

public record SpotSearchResponse(
        UUID id,
        UUID lotId,
        String code,
        double x,
        double y,
        double z,
        double width,
        double length,
        VehicleType vehicleType,
        BigDecimal pricePerHour,
        BigDecimal estimatedPrice
) {
}
