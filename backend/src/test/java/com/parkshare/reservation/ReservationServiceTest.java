package com.parkshare.reservation;

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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.parkshare.parkinglot.ParkingLot;
import com.parkshare.parkinglot.ParkingLotRepository;
import com.parkshare.parkingspot.ParkingSpot;
import com.parkshare.parkingspot.ParkingSpotRepository;
import com.parkshare.parkingspot.VehicleType;
import com.parkshare.reservation.dto.CreateReservationRequest;
import com.parkshare.reservation.dto.ReservationResponse;
import com.parkshare.shared.api.PagedResponse;
import com.parkshare.shared.exception.BusinessException;
import com.parkshare.shared.exception.ConflictException;
import com.parkshare.shared.exception.EntityNotFoundException;
import com.parkshare.shared.exception.ForbiddenException;
import com.parkshare.shared.idempotency.IdempotencyService;
import com.parkshare.vehicle.Vehicle;
import com.parkshare.vehicle.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private ParkingSpotRepository parkingSpotRepository;

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private ParkingLotRepository parkingLotRepository;

    @Mock
    private IdempotencyService idempotencyService;

    @Spy
    private ReservationMapper reservationMapper = Mappers.getMapper(ReservationMapper.class);

    @Mock
    private org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    private ReservationService reservationService;

    private final Clock clock = Clock.fixed(Instant.parse("2026-05-28T10:00:00Z"), ZoneId.systemDefault());

    @BeforeEach
    void setUp() {
        reservationService = new ReservationService(
                reservationRepository, parkingSpotRepository, vehicleRepository,
                parkingLotRepository, idempotencyService, reservationMapper, clock, transactionTemplate
        );
        org.mockito.Mockito.lenient().when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            org.springframework.transaction.support.TransactionCallback<?> callback = inv.getArgument(0);
            return callback.doInTransaction(new org.springframework.transaction.support.SimpleTransactionStatus());
        });
    }

    @Test
    void create_success_returnsResponse() {
        UUID driverId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID lotId = UUID.randomUUID();
        UUID spotId = UUID.randomUUID();
        UUID vehicleId = UUID.randomUUID();
        LocalDateTime start = futureStart();
        LocalDateTime end = start.plusHours(2);
        CreateReservationRequest request = new CreateReservationRequest(spotId, vehicleId, start, end);

        stubSuccessPath(driverId, ownerId, lotId, spotId, vehicleId, new BigDecimal("50000"));

        ReservationResponse response = reservationService.createReservation(driverId, request, null);

        assertThat(response.driverId()).isEqualTo(driverId);
        assertThat(response.spotId()).isEqualTo(spotId);
    }

    @Test
    void create_idempotencyKeyFound_returnsCachedResponse() {
        UUID driverId = UUID.randomUUID();
        String key = "test-key";
        LocalDateTime start = futureStart();
        ReservationResponse cached = new ReservationResponse(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                driverId, ReservationStatus.RESERVED, start, start.plusHours(2), new BigDecimal("100000.00"), Instant.now());

        when(idempotencyService.get(key, ReservationResponse.class)).thenReturn(Optional.of(cached));

        CreateReservationRequest request = new CreateReservationRequest(UUID.randomUUID(), UUID.randomUUID(), start, start.plusHours(2));
        ReservationResponse response = reservationService.createReservation(driverId, request, key);

        assertThat(response).isSameAs(cached);
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void create_startTimeInPast_throws400() {
        LocalDateTime start = LocalDateTime.now(clock).minusHours(1);
        CreateReservationRequest request = new CreateReservationRequest(UUID.randomUUID(), UUID.randomUUID(), start, start.plusHours(2));

        assertThatThrownBy(() -> reservationService.createReservation(UUID.randomUUID(), request, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Start time must be in the future");
    }

    @Test
    void create_durationTooShort_throws400() {
        LocalDateTime start = futureStart();
        CreateReservationRequest request = new CreateReservationRequest(UUID.randomUUID(), UUID.randomUUID(), start, start.plusMinutes(20));

        assertThatThrownBy(() -> reservationService.createReservation(UUID.randomUUID(), request, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Minimum booking duration is 30 minutes");
    }

    @Test
    void create_vehicleNotFound_throws404() {
        UUID vehicleId = UUID.randomUUID();
        LocalDateTime start = futureStart();
        CreateReservationRequest request = new CreateReservationRequest(UUID.randomUUID(), vehicleId, start, start.plusHours(2));
        when(vehicleRepository.findByIdAndActiveTrue(vehicleId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reservationService.createReservation(UUID.randomUUID(), request, null))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void create_vehicleNotOwnedByDriver_throws403() {
        UUID driverId = UUID.randomUUID();
        UUID vehicleId = UUID.randomUUID();
        LocalDateTime start = futureStart();
        CreateReservationRequest request = new CreateReservationRequest(UUID.randomUUID(), vehicleId, start, start.plusHours(2));
        when(vehicleRepository.findByIdAndActiveTrue(vehicleId))
                .thenReturn(Optional.of(Vehicle.builder().id(vehicleId).userId(UUID.randomUUID()).vehicleType(VehicleType.CAR).active(true).build()));

        assertThatThrownBy(() -> reservationService.createReservation(driverId, request, null))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void create_spotNotFound_throws404() {
        UUID driverId = UUID.randomUUID();
        UUID vehicleId = UUID.randomUUID();
        UUID spotId = UUID.randomUUID();
        LocalDateTime start = futureStart();
        CreateReservationRequest request = new CreateReservationRequest(spotId, vehicleId, start, start.plusHours(2));
        when(vehicleRepository.findByIdAndActiveTrue(vehicleId))
                .thenReturn(Optional.of(Vehicle.builder().id(vehicleId).userId(driverId).vehicleType(VehicleType.CAR).active(true).build()));
        when(parkingSpotRepository.findByIdAndActiveTrueForUpdate(spotId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reservationService.createReservation(driverId, request, null))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void create_vehicleTypeMismatch_throws400() {
        UUID driverId = UUID.randomUUID();
        UUID vehicleId = UUID.randomUUID();
        UUID spotId = UUID.randomUUID();
        LocalDateTime start = futureStart();
        CreateReservationRequest request = new CreateReservationRequest(spotId, vehicleId, start, start.plusHours(2));
        when(vehicleRepository.findByIdAndActiveTrue(vehicleId))
                .thenReturn(Optional.of(Vehicle.builder().id(vehicleId).userId(driverId).vehicleType(VehicleType.CAR).active(true).build()));
        when(parkingSpotRepository.findByIdAndActiveTrueForUpdate(spotId))
                .thenReturn(Optional.of(ParkingSpot.builder().id(spotId).vehicleType(VehicleType.MOTORBIKE).pricePerHour(BigDecimal.ONE).active(true).build()));

        assertThatThrownBy(() -> reservationService.createReservation(driverId, request, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Vehicle type does not match spot type");
    }

    @Test
    void create_driverIsLotOwner_throws403() {
        UUID driverId = UUID.randomUUID();
        UUID vehicleId = UUID.randomUUID();
        UUID spotId = UUID.randomUUID();
        UUID lotId = UUID.randomUUID();
        LocalDateTime start = futureStart();
        CreateReservationRequest request = new CreateReservationRequest(spotId, vehicleId, start, start.plusHours(2));
        when(vehicleRepository.findByIdAndActiveTrue(vehicleId))
                .thenReturn(Optional.of(Vehicle.builder().id(vehicleId).userId(driverId).vehicleType(VehicleType.CAR).active(true).build()));
        when(parkingSpotRepository.findByIdAndActiveTrueForUpdate(spotId))
                .thenReturn(Optional.of(ParkingSpot.builder().id(spotId).lotId(lotId).vehicleType(VehicleType.CAR).pricePerHour(BigDecimal.ONE).active(true).build()));
        when(parkingLotRepository.findByIdAndActiveTrue(lotId))
                .thenReturn(Optional.of(ParkingLot.builder().id(lotId).ownerId(driverId).name("L").address("A").floor(1).build()));

        assertThatThrownBy(() -> reservationService.createReservation(driverId, request, null))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void create_spotNotAvailable_throws409() {
        UUID driverId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID lotId = UUID.randomUUID();
        UUID spotId = UUID.randomUUID();
        UUID vehicleId = UUID.randomUUID();
        LocalDateTime start = futureStart();
        CreateReservationRequest request = new CreateReservationRequest(spotId, vehicleId, start, start.plusHours(2));

        stubVehicleAndSpotAndLot(driverId, ownerId, lotId, spotId, vehicleId, BigDecimal.ONE);
        when(reservationRepository.existsOverlappingReservation(any(), any(), any(), any())).thenReturn(true);

        assertThatThrownBy(() -> reservationService.createReservation(driverId, request, null))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void create_totalPrice_roundedTo1000VND() {
        // 90 minutes at 50000/hr = 75000 → already rounded
        UUID driverId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID lotId = UUID.randomUUID();
        UUID spotId = UUID.randomUUID();
        UUID vehicleId = UUID.randomUUID();
        LocalDateTime start = futureStart();
        LocalDateTime end = start.plusMinutes(90);
        CreateReservationRequest request = new CreateReservationRequest(spotId, vehicleId, start, end);

        stubSuccessPath(driverId, ownerId, lotId, spotId, vehicleId, new BigDecimal("50000"));

        ReservationResponse response = reservationService.createReservation(driverId, request, null);

        assertThat(response.totalPrice()).isEqualByComparingTo("75000.00");
    }

    @Test
    void create_idempotencyKeyStored_afterSuccess() {
        UUID driverId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID lotId = UUID.randomUUID();
        UUID spotId = UUID.randomUUID();
        UUID vehicleId = UUID.randomUUID();
        String key = "idem-key-123";
        LocalDateTime start = futureStart();
        CreateReservationRequest request = new CreateReservationRequest(spotId, vehicleId, start, start.plusHours(2));

        when(idempotencyService.get(key, ReservationResponse.class)).thenReturn(Optional.empty());
        stubSuccessPath(driverId, ownerId, lotId, spotId, vehicleId, new BigDecimal("50000"));

        reservationService.createReservation(driverId, request, key);

        verify(idempotencyService).store(eq(key), any(ReservationResponse.class));
    }

    @Test
    void getMyReservations_returnsPaged() {
        UUID driverId = UUID.randomUUID();
        LocalDateTime start = futureStart();
        Reservation reservation = Reservation.builder()
                .id(UUID.randomUUID())
                .driverId(driverId)
                .spotId(UUID.randomUUID())
                .vehicleId(UUID.randomUUID())
                .startTime(start)
                .endTime(start.plusHours(2))
                .totalPrice(new BigDecimal("100000.00"))
                .build();

        when(reservationRepository.findAllByDriverIdOrderByCreatedAtDesc(eq(driverId), any()))
                .thenReturn(new PageImpl<>(List.of(reservation)));

        PagedResponse<ReservationResponse> response = reservationService.getMyReservations(driverId, 0, 20);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).driverId()).isEqualTo(driverId);
    }

    // --- helpers ---

    private LocalDateTime futureStart() {
        return LocalDateTime.now(clock).plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0);
    }

    private void stubSuccessPath(UUID driverId, UUID ownerId, UUID lotId, UUID spotId, UUID vehicleId, BigDecimal price) {
        stubVehicleAndSpotAndLot(driverId, ownerId, lotId, spotId, vehicleId, price);
        when(reservationRepository.existsOverlappingReservation(any(), any(), any(), any())).thenReturn(false);
        when(reservationRepository.save(any())).thenAnswer(inv -> {
            Reservation r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });
    }

    private void stubVehicleAndSpotAndLot(UUID driverId, UUID ownerId, UUID lotId, UUID spotId, UUID vehicleId, BigDecimal price) {
        when(vehicleRepository.findByIdAndActiveTrue(vehicleId))
                .thenReturn(Optional.of(Vehicle.builder().id(vehicleId).userId(driverId).vehicleType(VehicleType.CAR).active(true).build()));
        when(parkingSpotRepository.findByIdAndActiveTrueForUpdate(spotId))
                .thenReturn(Optional.of(ParkingSpot.builder().id(spotId).lotId(lotId).vehicleType(VehicleType.CAR).pricePerHour(price).active(true).build()));
        when(parkingLotRepository.findByIdAndActiveTrue(lotId))
                .thenReturn(Optional.of(ParkingLot.builder().id(lotId).ownerId(ownerId).name("L").address("A").floor(1).build()));
    }
}
