package com.parkshare.review.dto;

import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreateReviewRequest(
        @NotNull UUID reservationId,
        @Min(1) @Max(5) Short rating,
        String comment
) {
}
