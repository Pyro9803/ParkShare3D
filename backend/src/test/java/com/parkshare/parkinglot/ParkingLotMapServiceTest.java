package com.parkshare.parkinglot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.parkshare.parkinglot.dto.ParkingLotMapResponse;
import com.parkshare.parkingspot.ParkingSpot;
import com.parkshare.parkingspot.ParkingSpotRepository;
import com.parkshare.parkingspot.SpotStatus;
import com.parkshare.parkingspot.VehicleType;
import com.parkshare.reservation.ReservationRepository;
import com.parkshare.shared.cache.MapCacheService;
import com.parkshare.shared.exception.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ParkingLotMapServiceTest {

    @Mock
    private ParkingLotRepository parkingLotRepository;

    @Mock
    private ParkingSpotRepository parkingSpotRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private MapCacheService mapCacheService;

    private final Clock clock = Clock.fixed(Instant.parse("2026-05-28T10:00:00Z"), ZoneId.systemDefault());

    private ParkingLotMapService mapService;

    @BeforeEach
    void setUp() {
        mapService = new ParkingLotMapService(parkingLotRepository, parkingSpotRepository, reservationRepository, mapCacheService, clock);
    }

    @Test
    void getMap_cacheHit_returnsCachedWithoutDbCall() {
        UUID lotId = UUID.randomUUID();
        ParkingLotMapResponse cached = new ParkingLotMapResponse(lotId, "L", 1, List.of());
        when(mapCacheService.get(lotId)).thenReturn(Optional.of(cached));

        ParkingLotMapResponse response = mapService.getMap(lotId);

        assertThat(response).isSameAs(cached);
        verify(parkingLotRepository, never()).findByIdAndActiveTrue(any());
    }

    @Test
    void getMap_lotNotFound_throws404() {
        UUID lotId = UUID.randomUUID();
        when(mapCacheService.get(lotId)).thenReturn(Optional.empty());
        when(parkingLotRepository.findByIdAndActiveTrue(lotId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> mapService.getMap(lotId))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void getMap_lotNotVerified_allSpotsUnavailable() {
        UUID lotId = UUID.randomUUID();
        ParkingLot lot = ParkingLot.builder().id(lotId).name("L").floor(1).verified(false).build();
        ParkingSpot spot = ParkingSpot.builder().id(UUID.randomUUID()).code("A1").active(true).build();

        when(mapCacheService.get(lotId)).thenReturn(Optional.empty());
        when(parkingLotRepository.findByIdAndActiveTrue(lotId)).thenReturn(Optional.of(lot));
        when(parkingSpotRepository.findAllByLotId(lotId)).thenReturn(List.of(spot));

        ParkingLotMapResponse response = mapService.getMap(lotId);

        assertThat(response.spots()).hasSize(1);
        assertThat(response.spots().get(0).status()).isEqualTo(SpotStatus.UNAVAILABLE);
        verify(reservationRepository, never()).findOccupiedSpotIdsForLot(any(), any());
    }

    @Test
    void getMap_available_noActiveReservations() {
        UUID lotId = UUID.randomUUID();
        ParkingLot lot = ParkingLot.builder().id(lotId).name("L").floor(1).verified(true).build();
        ParkingSpot spot = ParkingSpot.builder().id(UUID.randomUUID()).code("A1").active(true).vehicleType(VehicleType.CAR).pricePerHour(BigDecimal.TEN).build();

        when(mapCacheService.get(lotId)).thenReturn(Optional.empty());
        when(parkingLotRepository.findByIdAndActiveTrue(lotId)).thenReturn(Optional.of(lot));
        when(parkingSpotRepository.findAllByLotId(lotId)).thenReturn(List.of(spot));
        when(reservationRepository.findOccupiedSpotIdsForLot(eq(lotId), any())).thenReturn(List.of());
        when(reservationRepository.findPendingSpotIdsForLot(eq(lotId), any())).thenReturn(List.of());

        ParkingLotMapResponse response = mapService.getMap(lotId);

        assertThat(response.spots().get(0).status()).isEqualTo(SpotStatus.AVAILABLE);
    }

    @Test
    void getMap_occupied_checkedInReservation() {
        UUID lotId = UUID.randomUUID();
        UUID spotId = UUID.randomUUID();
        ParkingLot lot = ParkingLot.builder().id(lotId).name("L").floor(1).verified(true).build();
        ParkingSpot spot = ParkingSpot.builder().id(spotId).code("A1").active(true).build();

        when(mapCacheService.get(lotId)).thenReturn(Optional.empty());
        when(parkingLotRepository.findByIdAndActiveTrue(lotId)).thenReturn(Optional.of(lot));
        when(parkingSpotRepository.findAllByLotId(lotId)).thenReturn(List.of(spot));
        when(reservationRepository.findOccupiedSpotIdsForLot(eq(lotId), any())).thenReturn(List.of(spotId));
        when(reservationRepository.findPendingSpotIdsForLot(eq(lotId), any())).thenReturn(List.of());

        ParkingLotMapResponse response = mapService.getMap(lotId);

        assertThat(response.spots().get(0).status()).isEqualTo(SpotStatus.OCCUPIED);
    }

    @Test
    void getMap_pendingBooking_reservedReservation() {
        UUID lotId = UUID.randomUUID();
        UUID spotId = UUID.randomUUID();
        ParkingLot lot = ParkingLot.builder().id(lotId).name("L").floor(1).verified(true).build();
        ParkingSpot spot = ParkingSpot.builder().id(spotId).code("A1").active(true).build();

        when(mapCacheService.get(lotId)).thenReturn(Optional.empty());
        when(parkingLotRepository.findByIdAndActiveTrue(lotId)).thenReturn(Optional.of(lot));
        when(parkingSpotRepository.findAllByLotId(lotId)).thenReturn(List.of(spot));
        when(reservationRepository.findOccupiedSpotIdsForLot(eq(lotId), any())).thenReturn(List.of());
        when(reservationRepository.findPendingSpotIdsForLot(eq(lotId), any())).thenReturn(List.of(spotId));

        ParkingLotMapResponse response = mapService.getMap(lotId);

        assertThat(response.spots().get(0).status()).isEqualTo(SpotStatus.PENDING_BOOKING);
    }

    @Test
    void getMap_storesResponseInCacheAfterDbFetch() {
        UUID lotId = UUID.randomUUID();
        ParkingLot lot = ParkingLot.builder().id(lotId).name("L").floor(1).verified(true).build();

        when(mapCacheService.get(lotId)).thenReturn(Optional.empty());
        when(parkingLotRepository.findByIdAndActiveTrue(lotId)).thenReturn(Optional.of(lot));

        mapService.getMap(lotId);

        verify(mapCacheService).store(eq(lotId), any());
    }
}
