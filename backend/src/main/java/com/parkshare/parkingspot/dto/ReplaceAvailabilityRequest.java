package com.parkshare.parkingspot.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record ReplaceAvailabilityRequest(
        @NotNull @NotEmpty List<@Valid CreateAvailabilityRequest> slots
) {
}
