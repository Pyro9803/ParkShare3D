package com.parkshare.reservation;

import com.parkshare.parkinglot.ParkingLot;
import com.parkshare.parkinglot.ParkingLotRepository;
import com.parkshare.parkingspot.ParkingSpot;
import com.parkshare.parkingspot.ParkingSpotRepository;
import com.parkshare.reservation.dto.CreateReservationRequest;
import com.parkshare.reservation.dto.ReservationResponse;
import com.parkshare.shared.api.PagedResponse;
import com.parkshare.shared.exception.BusinessException;
import com.parkshare.shared.exception.ConflictException;
import com.parkshare.shared.exception.EntityNotFoundException;
import com.parkshare.shared.exception.ForbiddenException;
import com.parkshare.shared.idempotency.IdempotencyService;
import com.parkshare.shared.util.PriceCalculator;
import com.parkshare.vehicle.Vehicle;
import com.parkshare.vehicle.VehicleRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Service
public class ReservationService {

    private static final Set<ReservationStatus> ACTIVE_STATUSES =
            Set.of(ReservationStatus.RESERVED, ReservationStatus.CHECKED_IN);

    private final ReservationRepository reservationRepository;
    private final ParkingSpotRepository parkingSpotRepository;
    private final VehicleRepository vehicleRepository;
    private final ParkingLotRepository parkingLotRepository;
    private final IdempotencyService idempotencyService;
    private final ReservationMapper reservationMapper;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public ReservationService(ReservationRepository reservationRepository,
                              ParkingSpotRepository parkingSpotRepository,
                              VehicleRepository vehicleRepository,
                              ParkingLotRepository parkingLotRepository,
                              IdempotencyService idempotencyService,
                              ReservationMapper reservationMapper,
                              Clock clock,
                              TransactionTemplate transactionTemplate) {
        this.reservationRepository = reservationRepository;
        this.parkingSpotRepository = parkingSpotRepository;
        this.vehicleRepository = vehicleRepository;
        this.parkingLotRepository = parkingLotRepository;
        this.idempotencyService = idempotencyService;
        this.reservationMapper = reservationMapper;
        this.clock = clock;
        this.transactionTemplate = transactionTemplate;
    }

    public ReservationResponse createReservation(UUID driverId, CreateReservationRequest request, String idempotencyKey) {
        try {
            return transactionTemplate.execute(status -> createReservationInternal(driverId, request, idempotencyKey));
        } catch (DataIntegrityViolationException e) {
            if (idempotencyKey != null && e.getMessage() != null && e.getMessage().contains("uq_reservations_idempotency_key")) {
                // Race condition: another thread saved the record but hasn't updated the Redis cache yet.
                // We wait briefly and then attempt to return the cached response.
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                return idempotencyService.get(idempotencyKey, ReservationResponse.class)
                        .orElseThrow(() -> e);
            }
            throw e;
        }
    }

    private ReservationResponse createReservationInternal(UUID driverId, CreateReservationRequest request, String idempotencyKey) {
        // Step 1: Idempotency check
        if (idempotencyKey != null) {
            var cached = idempotencyService.get(idempotencyKey, ReservationResponse.class);
            if (cached.isPresent()) return cached.get();
        }

        // Step 2: Validate time window
        LocalDateTime now = LocalDateTime.now(clock);
        if (!request.startTime().isAfter(now)) {
            throw new BusinessException("INVALID_START_TIME", "Start time must be in the future");
        }
        if (!request.endTime().isAfter(request.startTime())) {
            throw new BusinessException("INVALID_TIME_RANGE", "End time must be after start time");
        }
        if (Duration.between(request.startTime(), request.endTime()).toMinutes() < 30) {
            throw new BusinessException("DURATION_TOO_SHORT", "Minimum booking duration is 30 minutes");
        }
        if (!request.startTime().toLocalDate().equals(request.endTime().toLocalDate())) {
            throw new BusinessException("CROSS_DAY_NOT_SUPPORTED", "Reservations must be within the same day");
        }

        // Step 3: Load vehicle
        Vehicle vehicle = vehicleRepository.findByIdAndActiveTrue(request.vehicleId())
                .orElseThrow(() -> new EntityNotFoundException("VEHICLE_NOT_FOUND", "Vehicle not found"));
        if (!vehicle.getUserId().equals(driverId)) {
            throw new ForbiddenException("NOT_YOUR_VEHICLE", "You don't own this vehicle");
        }

        // Step 4: Acquire pessimistic lock on spot
        ParkingSpot spot = parkingSpotRepository.findByIdAndActiveTrueForUpdate(request.parkingSpotId())
                .orElseThrow(() -> new EntityNotFoundException("SPOT_NOT_FOUND", "Parking spot not found"));

        // Step 5: Vehicle type must match spot type
        if (!vehicle.getVehicleType().equals(spot.getVehicleType())) {
            throw new BusinessException("VEHICLE_TYPE_MISMATCH", "Vehicle type does not match spot type");
        }

        // Step 6: Load lot; driver must not be the lot owner
        ParkingLot lot = parkingLotRepository.findByIdAndActiveTrue(spot.getLotId())
                .orElseThrow(() -> new EntityNotFoundException("LOT_NOT_FOUND", "Parking lot not found"));
        if (lot.getOwnerId().equals(driverId)) {
            throw new ForbiddenException("CANNOT_BOOK_OWN_SPOT", "You cannot book a spot in your own lot");
        }

        // Step 7: Overlap check (within the pessimistic lock)
        if (reservationRepository.existsOverlappingReservation(
                request.parkingSpotId(), request.startTime(), request.endTime(), ACTIVE_STATUSES)) {
            throw new ConflictException("SPOT_NOT_AVAILABLE", "Spot is not available for the requested time");
        }

        // Step 8: Compute total price, rounded to nearest 1000 VND
        BigDecimal totalPrice = PriceCalculator.calculate(request.startTime(), request.endTime(), spot.getPricePerHour());

        // Step 9: Persist
        Reservation reservation = Reservation.builder()
                .spotId(request.parkingSpotId())
                .vehicleId(request.vehicleId())
                .driverId(driverId)
                .startTime(request.startTime())
                .endTime(request.endTime())
                .totalPrice(totalPrice)
                .idempotencyKey(idempotencyKey)
                .build();
        reservation = reservationRepository.save(reservation);

        // Step 10: Map and store idempotency response
        ReservationResponse response = reservationMapper.toResponse(reservation);
        if (idempotencyKey != null) {
            idempotencyService.store(idempotencyKey, response);
        }

        return response;
    }

    @Transactional(readOnly = true)
    public PagedResponse<ReservationResponse> getMyReservations(UUID driverId, int page, int size) {
        Page<Reservation> reservationsPage = reservationRepository.findAllByDriverIdOrderByCreatedAtDesc(
                driverId, PageRequest.of(page, size));
        return PagedResponse.from(reservationsPage.map(reservationMapper::toResponse));
    }
}
