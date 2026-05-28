package com.parkshare.parkinglot.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreateLotRequest(
        @NotBlank String name,
        @NotBlank String address,
        String description,
        @Min(1) int floor,
        @Valid List<CreateSpotRequest> spots
) {
}
