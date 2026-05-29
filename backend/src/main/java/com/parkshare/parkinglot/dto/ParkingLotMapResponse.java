package com.parkshare.parkinglot.dto;

import java.util.List;
import java.util.UUID;

public record ParkingLotMapResponse(
        UUID lotId,
        String name,
        int floor,
        List<SpotMapEntry> spots
) {
}
