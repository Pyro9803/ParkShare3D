package com.parkshare.review;

import java.util.UUID;

import com.parkshare.reservation.Reservation;
import com.parkshare.reservation.ReservationRepository;
import com.parkshare.reservation.ReservationStatus;
import com.parkshare.review.dto.CreateReviewRequest;
import com.parkshare.review.dto.ReviewResponse;
import com.parkshare.review.dto.SpotReviewsResponse;
import com.parkshare.shared.api.PagedResponse;
import com.parkshare.shared.exception.ConflictException;
import com.parkshare.shared.exception.EntityNotFoundException;
import com.parkshare.shared.exception.ForbiddenException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ReservationRepository reservationRepository;
    private final ReviewMapper reviewMapper;

    public ReviewService(ReviewRepository reviewRepository,
                         ReservationRepository reservationRepository,
                         ReviewMapper reviewMapper) {
        this.reviewRepository = reviewRepository;
        this.reservationRepository = reservationRepository;
        this.reviewMapper = reviewMapper;
    }

    @Transactional
    public ReviewResponse createReview(UUID driverId, CreateReviewRequest request) {
        Reservation reservation = reservationRepository.findById(request.reservationId())
                .orElseThrow(() -> new EntityNotFoundException("RESERVATION_NOT_FOUND", "Reservation not found"));

        if (!reservation.getDriverId().equals(driverId)) {
            throw new ForbiddenException("NOT_YOUR_RESERVATION", "You can only review your own reservations");
        }

        if (reservation.getStatus() != ReservationStatus.COMPLETED) {
            throw new ConflictException("RESERVATION_NOT_COMPLETED", "You can only review completed reservations");
        }

        if (reviewRepository.existsByReservationId(request.reservationId())) {
            throw new ConflictException("REVIEW_ALREADY_EXISTS", "A review already exists for this reservation");
        }

        Review review = Review.builder()
                .reservationId(reservation.getId())
                .spotId(reservation.getSpotId())
                .driverId(driverId)
                .rating(request.rating())
                .comment(request.comment())
                .build();

        return reviewMapper.toResponse(reviewRepository.save(review));
    }

    @Transactional(readOnly = true)
    public SpotReviewsResponse getSpotReviews(UUID spotId, Pageable pageable) {
        double averageRating = reviewRepository.findAverageRatingBySpotId(spotId);
        Page<Review> reviewPage = reviewRepository.findAllBySpotId(spotId, pageable);
        
        return new SpotReviewsResponse(
                averageRating,
                PagedResponse.from(reviewPage.map(reviewMapper::toResponse))
        );
    }
}
