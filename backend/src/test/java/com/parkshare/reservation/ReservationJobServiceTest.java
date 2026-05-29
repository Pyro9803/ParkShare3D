package com.parkshare.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReservationJobServiceTest {

    @Mock
    private ReservationRepository reservationRepository;

    private ReservationJobService reservationJobService;

    private final Clock clock = Clock.fixed(Instant.parse("2026-05-28T10:00:00Z"), ZoneId.systemDefault());

    @BeforeEach
    void setUp() {
        reservationJobService = new ReservationJobService(reservationRepository);
    }

    @Test
    void expireReservations_callsRepositoryWithCorrectCutoff() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime expectedCutoff = now.minusMinutes(45);
        Instant expectedNow = Instant.now(clock);

        when(reservationRepository.expireStaleReservations(eq(expectedCutoff), eq(expectedNow))).thenReturn(5);

        int count = reservationJobService.expireReservations(clock);

        assertThat(count).isEqualTo(5);
        verify(reservationRepository).expireStaleReservations(expectedCutoff, expectedNow);
    }

    @Test
    void markNoShow_callsRepositoryWithCorrectCutoff() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime expectedCutoff = now.minusHours(1);
        Instant expectedNow = Instant.now(clock);

        when(reservationRepository.markNoShowReservations(eq(expectedCutoff), eq(expectedNow))).thenReturn(3);

        int count = reservationJobService.markNoShow(clock);

        assertThat(count).isEqualTo(3);
        verify(reservationRepository).markNoShowReservations(expectedCutoff, expectedNow);
    }
}
