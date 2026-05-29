package com.parkshare.review;

import java.util.UUID;

import com.parkshare.review.dto.CreateReviewRequest;
import com.parkshare.review.dto.ReviewResponse;
import com.parkshare.review.dto.SpotReviewsResponse;
import com.parkshare.shared.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping("/reviews")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<ApiResponse<ReviewResponse>> createReview(
            @Valid @RequestBody CreateReviewRequest request,
            Authentication authentication
    ) {
        UUID driverId = (UUID) authentication.getPrincipal();
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(reviewService.createReview(driverId, request)));
    }

    @GetMapping("/parking-spots/{id}/reviews")
    public ResponseEntity<ApiResponse<SpotReviewsResponse>> getSpotReviews(
            @PathVariable UUID id,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(reviewService.getSpotReviews(id, pageable)));
    }
}
