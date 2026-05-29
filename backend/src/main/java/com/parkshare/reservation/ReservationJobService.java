package com.parkshare.reservation;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationJobService {

    private final ReservationRepository reservationRepository;

    public ReservationJobService(ReservationRepository reservationRepository) {
        this.reservationRepository = reservationRepository;
    }

    @Transactional
    public int expireReservations(Clock clock) {
        LocalDateTime cutoff = LocalDateTime.now(clock).minusMinutes(45);
        return reservationRepository.expireStaleReservations(cutoff, Instant.now(clock));
    }

    @Transactional
    public int markNoShow(Clock clock) {
        LocalDateTime cutoff = LocalDateTime.now(clock).minusHours(1);
        return reservationRepository.markNoShowReservations(cutoff, Instant.now(clock));
    }
}
