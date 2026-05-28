package com.parkshare.parkingspot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import com.parkshare.parkingspot.dto.SpotSearchResponse;
import com.parkshare.shared.api.PagedResponse;
import com.parkshare.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class SpotSearchServiceTest {

    @Mock
    private ParkingSpotRepository parkingSpotRepository;

    @Spy
    private SpotSearchMapper spotSearchMapper = Mappers.getMapper(SpotSearchMapper.class);

    private SpotSearchService spotSearchService;

    private final Clock clock = Clock.fixed(Instant.parse("2026-05-28T10:00:00Z"), ZoneId.systemDefault());

    @BeforeEach
    void setUp() {
        spotSearchService = new SpotSearchService(parkingSpotRepository, spotSearchMapper, clock);
    }

    @Test
    void search_success_returnsPagedResults() {
        LocalDateTime start = LocalDateTime.now(clock).plusDays(1).withHour(10).withMinute(0);
        LocalDateTime end = start.plusHours(2);
        UUID lotId = UUID.randomUUID();
        ParkingSpot spot = ParkingSpot.builder()
                .id(UUID.randomUUID())
                .lotId(lotId)
                .code("A-01")
                .vehicleType(VehicleType.CAR)
                .pricePerHour(new BigDecimal("10.00"))
                .active(true)
                .build();

        when(parkingSpotRepository.searchAvailableSpotsByLot(eq(VehicleType.CAR), any(), any(), any(), eq(lotId), any()))
                .thenReturn(new PageImpl<>(List.of(spot)));

        PagedResponse<SpotSearchResponse> response = spotSearchService.searchSpots(start, end, VehicleType.CAR, lotId, 0, 20);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).estimatedPrice()).isEqualByComparingTo("20.00");
    }

    @Test
    void search_startTimeInPast_throwsBusinessException() {
        LocalDateTime start = LocalDateTime.now(clock).minusHours(1);
        LocalDateTime end = start.plusHours(2);

        assertThatThrownBy(() -> spotSearchService.searchSpots(start, end, VehicleType.CAR, null, 0, 20))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Start time must be in the future");
    }

    @Test
    void search_endTimeBeforeStart_throwsBusinessException() {
        LocalDateTime start = LocalDateTime.now(clock).plusDays(1);
        LocalDateTime end = start.minusHours(1);

        assertThatThrownBy(() -> spotSearchService.searchSpots(start, end, VehicleType.CAR, null, 0, 20))
                .isInstanceOf(BusinessException.class)
                .hasMessage("End time must be after start time");
    }

    @Test
    void search_durationTooShort_throwsBusinessException() {
        LocalDateTime start = LocalDateTime.now(clock).plusDays(1);
        LocalDateTime end = start.plusMinutes(20);

        assertThatThrownBy(() -> spotSearchService.searchSpots(start, end, VehicleType.CAR, null, 0, 20))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Minimum booking duration is 30 minutes");
    }

    @Test
    void search_crossDay_throwsBusinessException() {
        LocalDateTime start = LocalDateTime.now(clock).plusDays(1).withHour(23).withMinute(0);
        LocalDateTime end = start.plusHours(2);

        assertThatThrownBy(() -> spotSearchService.searchSpots(start, end, VehicleType.CAR, null, 0, 20))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Search window must be within the same day");
    }

    @Test
    void search_estimatedPrice_calculatedCorrectly() {
        // 90 minutes at 40.00/hr = 60.00
        LocalDateTime start = LocalDateTime.now(clock).plusDays(1).withHour(10).withMinute(0);
        LocalDateTime end = start.plusMinutes(90);
        ParkingSpot spot = ParkingSpot.builder()
                .pricePerHour(new BigDecimal("40.00"))
                .active(true)
                .build();

        when(parkingSpotRepository.searchAvailableSpots(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(spot)));

        PagedResponse<SpotSearchResponse> response = spotSearchService.searchSpots(start, end, VehicleType.CAR, null, 0, 20);

        assertThat(response.content().get(0).estimatedPrice()).isEqualByComparingTo("60.00");
    }

    @Test
    void search_withLotId_passesLotIdToRepository() {
        LocalDateTime start = LocalDateTime.now(clock).plusDays(1);
        LocalDateTime end = start.plusHours(1);
        UUID lotId = UUID.randomUUID();
        when(parkingSpotRepository.searchAvailableSpotsByLot(any(), any(), any(), any(), eq(lotId), any()))
                .thenReturn(new PageImpl<>(List.of()));

        spotSearchService.searchSpots(start, end, VehicleType.CAR, lotId, 0, 20);

        verify(parkingSpotRepository).searchAvailableSpotsByLot(eq(VehicleType.CAR), any(), any(), any(), eq(lotId), any());
    }

    @Test
    void search_withNullLotId_passesNullToRepository() {
        LocalDateTime start = LocalDateTime.now(clock).plusDays(1);
        LocalDateTime end = start.plusHours(1);
        when(parkingSpotRepository.searchAvailableSpots(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        spotSearchService.searchSpots(start, end, VehicleType.CAR, null, 0, 20);

        verify(parkingSpotRepository).searchAvailableSpots(eq(VehicleType.CAR), any(), any(), any(), any());
    }
}
