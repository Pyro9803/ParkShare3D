package com.parkshare.reservation;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

import com.parkshare.shared.lock.DistributedLockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReservationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReservationScheduler.class);
    private static final Duration EXPIRE_LOCK_TTL = Duration.ofMinutes(4);
    private static final Duration NOSHOW_LOCK_TTL = Duration.ofMinutes(14);

    private final ReservationJobService jobService;
    private final DistributedLockService lockService;
    private final Clock clock;

    public ReservationScheduler(ReservationJobService jobService, DistributedLockService lockService, Clock clock) {
        this.jobService = jobService;
        this.lockService = lockService;
        this.clock = clock;
    }

    @Scheduled(fixedRateString = "PT5M")
    public void expireReservations() {
        String token = UUID.randomUUID().toString();
        if (!lockService.tryLock("job:expireReservations", token, EXPIRE_LOCK_TTL)) {
            log.warn("expireReservations: could not acquire lock, skipping");
            return;
        }
        try {
            int count = jobService.expireReservations(clock);
            log.info("expireReservations: {} reservation(s) expired", count);
        } catch (Exception e) {
            log.error("expireReservations: error occurred", e);
        } finally {
            lockService.unlock("job:expireReservations", token);
        }
    }

    @Scheduled(fixedRateString = "PT15M")
    public void markNoShow() {
        String token = UUID.randomUUID().toString();
        if (!lockService.tryLock("job:markNoShow", token, NOSHOW_LOCK_TTL)) {
            log.warn("markNoShow: could not acquire lock, skipping");
            return;
        }
        try {
            int count = jobService.markNoShow(clock);
            log.info("markNoShow: {} reservation(s) marked as NO_SHOW", count);
        } catch (Exception e) {
            log.error("markNoShow: error occurred", e);
        } finally {
            lockService.unlock("job:markNoShow", token);
        }
    }
}
