package com.parkshare.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.parkshare.reservation.Reservation;
import com.parkshare.reservation.ReservationRepository;
import com.parkshare.reservation.ReservationStatus;
import com.parkshare.review.dto.CreateReviewRequest;
import com.parkshare.review.dto.ReviewResponse;
import com.parkshare.review.dto.SpotReviewsResponse;
import com.parkshare.shared.exception.ConflictException;
import com.parkshare.shared.exception.EntityNotFoundException;
import com.parkshare.shared.exception.ForbiddenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Spy
    private ReviewMapper reviewMapper = Mappers.getMapper(ReviewMapper.class);

    private ReviewService reviewService;

    @BeforeEach
    void setUp() {
        reviewService = new ReviewService(reviewRepository, reservationRepository, reviewMapper);
    }

    @Test
    void createReview_success_returnsResponse() {
        UUID driverId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        UUID spotId = UUID.randomUUID();
        CreateReviewRequest request = new CreateReviewRequest(reservationId, (short) 5, "Great!");
        Reservation reservation = Reservation.builder()
                .id(reservationId)
                .driverId(driverId)
                .spotId(spotId)
                .status(ReservationStatus.COMPLETED)
                .build();

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(reviewRepository.existsByReservationId(reservationId)).thenReturn(false);
        when(reviewRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ReviewResponse response = reviewService.createReview(driverId, request);

        assertThat(response.rating()).isEqualTo(5);
        assertThat(response.comment()).isEqualTo("Great!");
        assertThat(response.spotId()).isEqualTo(spotId);
    }

    @Test
    void createReview_reservationNotFound_throws404() {
        when(reservationRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.createReview(UUID.randomUUID(), new CreateReviewRequest(UUID.randomUUID(), (short) 5, "")))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void createReview_notYourReservation_throws403() {
        Reservation reservation = Reservation.builder().driverId(UUID.randomUUID()).build();
        when(reservationRepository.findById(any())).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> reviewService.createReview(UUID.randomUUID(), new CreateReviewRequest(UUID.randomUUID(), (short) 5, "")))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void createReview_notCompleted_throws409() {
        UUID driverId = UUID.randomUUID();
        Reservation reservation = Reservation.builder().driverId(driverId).status(ReservationStatus.CHECKED_IN).build();
        when(reservationRepository.findById(any())).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> reviewService.createReview(driverId, new CreateReviewRequest(UUID.randomUUID(), (short) 5, "")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("You can only review completed reservations");
    }

    @Test
    void createReview_alreadyExists_throws409() {
        UUID driverId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        Reservation reservation = Reservation.builder().id(reservationId).driverId(driverId).status(ReservationStatus.COMPLETED).build();
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(reviewRepository.existsByReservationId(reservationId)).thenReturn(true);

        assertThatThrownBy(() -> reviewService.createReview(driverId, new CreateReviewRequest(reservationId, (short) 5, "")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("A review already exists for this reservation");
    }

    @Test
    void getSpotReviews_returnsPaginatedWithAverage() {
        UUID spotId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 20);
        Review review = Review.builder().rating((short) 4).build();

        when(reviewRepository.findAverageRatingBySpotId(spotId)).thenReturn(4.5);
        when(reviewRepository.findAllBySpotId(eq(spotId), any())).thenReturn(new PageImpl<>(List.of(review)));

        SpotReviewsResponse response = reviewService.getSpotReviews(spotId, pageable);

        assertThat(response.averageRating()).isEqualTo(4.5);
        assertThat(response.reviews().content()).hasSize(1);
    }
}
