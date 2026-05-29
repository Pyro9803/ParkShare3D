package com.parkshare.review.dto;

import com.parkshare.shared.api.PagedResponse;

public record SpotReviewsResponse(
        double averageRating,
        PagedResponse<ReviewResponse> reviews
) {
}
