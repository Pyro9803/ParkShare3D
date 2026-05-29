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

import com.parkshare.checkin.CheckInLog;
import com.parkshare.checkin.CheckInLogMapper;
import com.parkshare.checkin.CheckInLogRepository;
import com.parkshare.parkinglot.ParkingLot;
import com.parkshare.parkinglot.ParkingLotRepository;
import com.parkshare.parkingspot.ParkingSpot;
import com.parkshare.parkingspot.ParkingSpotRepository;
import com.parkshare.parkingspot.VehicleType;
import com.parkshare.reservation.dto.CheckInLogResponse;
import com.parkshare.reservation.dto.CreateReservationRequest;
import com.parkshare.reservation.dto.ReservationResponse;
import com.parkshare.shared.api.PagedResponse;
import com.parkshare.shared.exception.BusinessException;
import com.parkshare.shared.exception.CheckInWindowException;
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
    private CheckInLogRepository checkInLogRepository;

    @Mock
    private IdempotencyService idempotencyService;

    @Spy
    private ReservationMapper reservationMapper = Mappers.getMapper(ReservationMapper.class);

    @Spy
    private CheckInLogMapper checkInLogMapper = Mappers.getMapper(CheckInLogMapper.class);

    @Mock
    private org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    private ReservationService reservationService;

    private final Clock clock = Clock.fixed(Instant.parse("2026-05-28T10:00:00Z"), ZoneId.systemDefault());

    @BeforeEach
    void setUp() {
        reservationService = new ReservationService(
                reservationRepository, parkingSpotRepository, vehicleRepository,
                parkingLotRepository, checkInLogRepository, idempotencyService, reservationMapper, checkInLogMapper, clock, transactionTemplate
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
                driverId, ReservationStatus.RESERVED, start, start.plusHours(2), new BigDecimal("100000.00"), null, Instant.now());

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
    void getMyReservations_withoutStatus_returnsPaged() {
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

        PagedResponse<ReservationResponse> response = reservationService.getMyReservations(driverId, null, 0, 20);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).driverId()).isEqualTo(driverId);
    }

    @Test
    void getMyReservations_withStatusFilter_returnsPaged() {
        UUID driverId = UUID.randomUUID();
        LocalDateTime start = futureStart();
        Reservation reservation = Reservation.builder()
                .id(UUID.randomUUID())
                .driverId(driverId)
                .status(ReservationStatus.RESERVED)
                .spotId(UUID.randomUUID())
                .vehicleId(UUID.randomUUID())
                .startTime(start)
                .endTime(start.plusHours(2))
                .totalPrice(new BigDecimal("100000.00"))
                .build();

        when(reservationRepository.findAllByDriverIdAndStatusOrderByCreatedAtDesc(eq(driverId), eq(ReservationStatus.RESERVED), any()))
                .thenReturn(new PageImpl<>(List.of(reservation)));

        PagedResponse<ReservationResponse> response = reservationService.getMyReservations(driverId, ReservationStatus.RESERVED, 0, 20);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).driverId()).isEqualTo(driverId);
    }

    @Test
    void getById_driver_success() {
        UUID driverId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        Reservation reservation = Reservation.builder()
                .id(reservationId)
                .driverId(driverId)
                .build();

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));

        ReservationResponse response = reservationService.getReservationById(reservationId, driverId);

        assertThat(response.id()).isEqualTo(reservationId);
    }

    @Test
    void getById_owner_success() {
        UUID driverId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID lotId = UUID.randomUUID();
        UUID spotId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();

        Reservation reservation = Reservation.builder()
                .id(reservationId)
                .driverId(driverId)
                .spotId(spotId)
                .build();

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(parkingSpotRepository.findByIdAndActiveTrue(spotId)).thenReturn(Optional.of(ParkingSpot.builder().id(spotId).lotId(lotId).build()));
        when(parkingLotRepository.findByIdAndActiveTrue(lotId)).thenReturn(Optional.of(ParkingLot.builder().id(lotId).ownerId(ownerId).build()));

        ReservationResponse response = reservationService.getReservationById(reservationId, ownerId);

        assertThat(response.id()).isEqualTo(reservationId);
    }

    @Test
    void getById_unauthorizedUser_throws403() {
        UUID driverId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        UUID lotId = UUID.randomUUID();
        UUID spotId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();

        Reservation reservation = Reservation.builder()
                .id(reservationId)
                .driverId(driverId)
                .spotId(spotId)
                .build();

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(parkingSpotRepository.findByIdAndActiveTrue(spotId)).thenReturn(Optional.of(ParkingSpot.builder().id(spotId).lotId(lotId).build()));
        when(parkingLotRepository.findByIdAndActiveTrue(lotId)).thenReturn(Optional.of(ParkingLot.builder().id(lotId).ownerId(ownerId).build()));

        assertThatThrownBy(() -> reservationService.getReservationById(reservationId, otherId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void cancel_success() {
        UUID driverId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        Reservation reservation = Reservation.builder()
                .id(reservationId)
                .driverId(driverId)
                .status(ReservationStatus.RESERVED)
                .startTime(LocalDateTime.now(clock).plusHours(2))
                .build();

        when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));
        when(reservationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ReservationResponse response = reservationService.cancelReservation(reservationId, driverId);

        assertThat(response.status()).isEqualTo(ReservationStatus.CANCELLED);
    }

    @Test
    void cancel_notOwner_throws403() {
        UUID driverId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        Reservation reservation = Reservation.builder()
                .id(reservationId)
                .driverId(driverId)
                .build();

        when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> reservationService.cancelReservation(reservationId, UUID.randomUUID()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void cancel_wrongStatus_throws400() {
        UUID driverId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        Reservation reservation = Reservation.builder()
                .id(reservationId)
                .driverId(driverId)
                .status(ReservationStatus.CHECKED_IN)
                .build();

        when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> reservationService.cancelReservation(reservationId, driverId))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Cannot cancel a reservation that is not RESERVED");
    }

    @Test
    void cancel_windowExpired_throws400() {
        UUID driverId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        Reservation reservation = Reservation.builder()
                .id(reservationId)
                .driverId(driverId)
                .status(ReservationStatus.RESERVED)
                .startTime(LocalDateTime.now(clock).plusMinutes(20))
                .build();

        when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> reservationService.cancelReservation(reservationId, driverId))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Cancellation must be at least 30 minutes before start time");
    }

    @Test
    void checkIn_success() {
        UUID driverId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        Reservation reservation = Reservation.builder()
                .id(reservationId)
                .driverId(driverId)
                .status(ReservationStatus.RESERVED)
                .startTime(LocalDateTime.now(clock).plusMinutes(10))
                .build();

        when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));
        when(reservationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ReservationResponse response = reservationService.checkInReservation(reservationId, driverId);

        assertThat(response.status()).isEqualTo(ReservationStatus.CHECKED_IN);
    }

    @Test
    void checkIn_notOwner_throws403() {
        UUID driverId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        Reservation reservation = Reservation.builder()
                .id(reservationId)
                .driverId(driverId)
                .build();

        when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> reservationService.checkInReservation(reservationId, UUID.randomUUID()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void checkIn_wrongStatus_throws400() {
        UUID driverId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        Reservation reservation = Reservation.builder()
                .id(reservationId)
                .driverId(driverId)
                .status(ReservationStatus.CANCELLED)
                .build();

        when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> reservationService.checkInReservation(reservationId, driverId))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Cannot check-in a reservation that is not RESERVED");
    }

    @Test
    void checkIn_tooEarly_throws400() {
        UUID driverId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        Reservation reservation = Reservation.builder()
                .id(reservationId)
                .driverId(driverId)
                .status(ReservationStatus.RESERVED)
                .startTime(LocalDateTime.now(clock).plusHours(1))
                .build();

        when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> reservationService.checkInReservation(reservationId, driverId))
                .isInstanceOf(CheckInWindowException.class);
    }

    @Test
    void checkIn_tooLate_throws400() {
        UUID driverId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        Reservation reservation = Reservation.builder()
                .id(reservationId)
                .driverId(driverId)
                .status(ReservationStatus.RESERVED)
                .startTime(LocalDateTime.now(clock).minusMinutes(45))
                .build();

        when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> reservationService.checkInReservation(reservationId, driverId))
                .isInstanceOf(CheckInWindowException.class);
    }

    @Test
    void checkOut_success_createsCheckInLog() {
        UUID driverId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        Reservation reservation = Reservation.builder()
                .id(reservationId)
                .driverId(driverId)
                .status(ReservationStatus.CHECKED_IN)
                .checkedInAt(LocalDateTime.now(clock).minusHours(2))
                .build();

        when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));
        when(checkInLogRepository.save(any())).thenAnswer(i -> {
            CheckInLog log = i.getArgument(0);
            log.setId(UUID.randomUUID());
            return log;
        });

        CheckInLogResponse response = reservationService.checkOutReservation(reservationId, driverId);

        assertThat(response.actualDurationMinutes()).isEqualTo(120);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.COMPLETED);
    }

    @Test
    void checkOut_notOwner_throws403() {
        UUID driverId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        Reservation reservation = Reservation.builder()
                .id(reservationId)
                .driverId(driverId)
                .build();

        when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> reservationService.checkOutReservation(reservationId, UUID.randomUUID()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void checkOut_wrongStatus_throws400() {
        UUID driverId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        Reservation reservation = Reservation.builder()
                .id(reservationId)
                .driverId(driverId)
                .status(ReservationStatus.RESERVED)
                .build();

        when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> reservationService.checkOutReservation(reservationId, driverId))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Cannot check-out a reservation that is not CHECKED_IN");
    }

    @Test
    void getByLot_notLotOwner_throws403() {
        UUID lotId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        when(parkingLotRepository.findByIdAndActiveTrue(lotId)).thenReturn(Optional.of(ParkingLot.builder().id(lotId).ownerId(UUID.randomUUID()).build()));

        assertThatThrownBy(() -> reservationService.getReservationsByLotId(lotId, ownerId, 0, 20))
                .isInstanceOf(ForbiddenException.class);
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
