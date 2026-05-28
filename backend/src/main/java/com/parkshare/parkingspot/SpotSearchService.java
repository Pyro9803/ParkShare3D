package com.parkshare.parkingspot;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;

import com.parkshare.parkingspot.dto.SpotSearchResponse;
import com.parkshare.reservation.ReservationStatus;
import com.parkshare.shared.api.PagedResponse;
import com.parkshare.shared.exception.BusinessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SpotSearchService {

    private static final Set<ReservationStatus> ACTIVE_STATUSES =
            Set.of(ReservationStatus.RESERVED, ReservationStatus.CHECKED_IN);

    private final ParkingSpotRepository parkingSpotRepository;
    private final SpotSearchMapper spotSearchMapper;
    private final Clock clock;

    public SpotSearchService(ParkingSpotRepository parkingSpotRepository, SpotSearchMapper spotSearchMapper, Clock clock) {
        this.parkingSpotRepository = parkingSpotRepository;
        this.spotSearchMapper = spotSearchMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PagedResponse<SpotSearchResponse> searchSpots(
            LocalDateTime startTime,
            LocalDateTime endTime,
            VehicleType vehicleType,
            UUID lotId,
            int page,
            int size
    ) {
        validateSearchWindow(startTime, endTime);

        DayOfWeek dayOfWeek = DayOfWeek.from(startTime.getDayOfWeek());
        LocalTime startLocalTime = startTime.toLocalTime();
        LocalTime endLocalTime = endTime.toLocalTime();

        PageRequest pageRequest = PageRequest.of(page, size);
        Page<ParkingSpot> spotsPage = (lotId == null)
                ? parkingSpotRepository.searchAvailableSpots(
                        vehicleType, dayOfWeek, startLocalTime, endLocalTime,
                        startTime, endTime, ACTIVE_STATUSES, pageRequest)
                : parkingSpotRepository.searchAvailableSpotsByLot(
                        vehicleType, dayOfWeek, startLocalTime, endLocalTime, lotId,
                        startTime, endTime, ACTIVE_STATUSES, pageRequest);

        Page<SpotSearchResponse> responsePage = spotsPage.map(spot -> {
            BigDecimal estimatedPrice = com.parkshare.shared.util.PriceCalculator.calculate(startTime, endTime, spot.getPricePerHour());
            return spotSearchMapper.toResponse(spot, estimatedPrice);
        });

        return PagedResponse.from(responsePage);
    }

    private void validateSearchWindow(LocalDateTime startTime, LocalDateTime endTime) {
        if (!startTime.isAfter(LocalDateTime.now(clock))) {
            throw new BusinessException("INVALID_START_TIME", "Start time must be in the future");
        }
        if (!endTime.isAfter(startTime)) {
            throw new BusinessException("INVALID_TIME_RANGE", "End time must be after start time");
        }
        if (Duration.between(startTime, endTime).toMinutes() < 30) {
            throw new BusinessException("DURATION_TOO_SHORT", "Minimum booking duration is 30 minutes");
        }
        if (!startTime.toLocalDate().equals(endTime.toLocalDate())) {
            throw new BusinessException("CROSS_DAY_NOT_SUPPORTED", "Search window must be within the same day");
        }
    }
}
