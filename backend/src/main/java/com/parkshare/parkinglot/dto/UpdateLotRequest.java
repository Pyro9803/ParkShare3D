package com.parkshare.parkinglot.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record UpdateLotRequest(
        @NotBlank String name,
        @NotBlank String address,
        String description,
        @Min(1) int floor
) {
}
