package com.parkshare.reservation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import com.parkshare.shared.lock.DistributedLockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReservationSchedulerTest {

    @Mock
    private ReservationJobService jobService;

    @Mock
    private DistributedLockService lockService;

    private ReservationScheduler reservationScheduler;

    private final Clock clock = Clock.fixed(Instant.parse("2026-05-28T10:00:00Z"), ZoneId.systemDefault());

    @BeforeEach
    void setUp() {
        reservationScheduler = new ReservationScheduler(jobService, lockService, clock);
    }

    @Test
    void expireReservations_lockAcquired_callsService() {
        when(lockService.tryLock(eq("job:expireReservations"), anyString(), any())).thenReturn(true);

        reservationScheduler.expireReservations();

        verify(jobService).expireReservations(clock);
        verify(lockService).unlock(eq("job:expireReservations"), anyString());
    }

    @Test
    void expireReservations_lockNotAcquired_skips() {
        when(lockService.tryLock(eq("job:expireReservations"), anyString(), any())).thenReturn(false);

        reservationScheduler.expireReservations();

        verify(jobService, never()).expireReservations(any());
        verify(lockService, never()).unlock(any(), any());
    }

    @Test
    void markNoShow_lockAcquired_callsService() {
        when(lockService.tryLock(eq("job:markNoShow"), anyString(), any())).thenReturn(true);

        reservationScheduler.markNoShow();

        verify(jobService).markNoShow(clock);
        verify(lockService).unlock(eq("job:markNoShow"), anyString());
    }

    @Test
    void markNoShow_lockNotAcquired_skips() {
        when(lockService.tryLock(eq("job:markNoShow"), anyString(), any())).thenReturn(false);

        reservationScheduler.markNoShow();

        verify(jobService, never()).markNoShow(any());
        verify(lockService, never()).unlock(any(), any());
    }
}
