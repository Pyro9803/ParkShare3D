package com.parkshare.reservation;

import com.parkshare.checkin.CheckInLog;
import com.parkshare.checkin.CheckInLogMapper;
import com.parkshare.checkin.CheckInLogRepository;
import com.parkshare.parkinglot.ParkingLot;
import com.parkshare.parkinglot.ParkingLotRepository;
import com.parkshare.parkingspot.ParkingSpot;
import com.parkshare.parkingspot.ParkingSpotRepository;
import com.parkshare.reservation.dto.CheckInLogResponse;
import com.parkshare.reservation.dto.CreateReservationRequest;
import com.parkshare.reservation.dto.ReservationResponse;
import com.parkshare.shared.api.PagedResponse;
import com.parkshare.shared.cache.MapCacheService;
import com.parkshare.shared.exception.BusinessException;
import com.parkshare.shared.exception.CheckInWindowException;
import com.parkshare.shared.exception.ConflictException;
import com.parkshare.shared.exception.EntityNotFoundException;
import com.parkshare.shared.exception.ForbiddenException;
import com.parkshare.shared.idempotency.IdempotencyService;
import com.parkshare.shared.util.PriceCalculator;
import com.parkshare.vehicle.Vehicle;
import com.parkshare.vehicle.VehicleRepository;
import org.hibernate.exception.ConstraintViolationException;
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
    private final CheckInLogRepository checkInLogRepository;
    private final IdempotencyService idempotencyService;
    private final MapCacheService mapCacheService;
    private final ReservationMapper reservationMapper;
    private final CheckInLogMapper checkInLogMapper;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public ReservationService(ReservationRepository reservationRepository,
                              ParkingSpotRepository parkingSpotRepository,
                              VehicleRepository vehicleRepository,
                              ParkingLotRepository parkingLotRepository,
                              CheckInLogRepository checkInLogRepository,
                              IdempotencyService idempotencyService,
                              MapCacheService mapCacheService,
                              ReservationMapper reservationMapper,
                              CheckInLogMapper checkInLogMapper,
                              Clock clock,
                              TransactionTemplate transactionTemplate) {
        this.reservationRepository = reservationRepository;
        this.parkingSpotRepository = parkingSpotRepository;
        this.vehicleRepository = vehicleRepository;
        this.parkingLotRepository = parkingLotRepository;
        this.checkInLogRepository = checkInLogRepository;
        this.idempotencyService = idempotencyService;
        this.mapCacheService = mapCacheService;
        this.reservationMapper = reservationMapper;
        this.checkInLogMapper = checkInLogMapper;
        this.clock = clock;
        this.transactionTemplate = transactionTemplate;
    }

    public ReservationResponse createReservation(UUID driverId, CreateReservationRequest request, String idempotencyKey) {
        try {
            return transactionTemplate.execute(status -> createReservationInternal(driverId, request, idempotencyKey));
        } catch (DataIntegrityViolationException e) {
            if (idempotencyKey != null && isIdempotencyKeyViolation(e)) {
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

    private static boolean isIdempotencyKeyViolation(DataIntegrityViolationException e) {
        if (e.getCause() instanceof ConstraintViolationException cve) {
            return "uq_reservations_idempotency_key".equals(cve.getConstraintName());
        }
        return false;
    }

    private ReservationResponse createReservationInternal(UUID driverId, CreateReservationRequest request, String idempotencyKey) {
        if (idempotencyKey != null) {
            var cached = idempotencyService.get(idempotencyKey, ReservationResponse.class);
            if (cached.isPresent()) return cached.get();
        }

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

        Vehicle vehicle = vehicleRepository.findByIdAndActiveTrue(request.vehicleId())
                .orElseThrow(() -> new EntityNotFoundException("VEHICLE_NOT_FOUND", "Vehicle not found"));
        if (!vehicle.getUserId().equals(driverId)) {
            throw new ForbiddenException("NOT_YOUR_VEHICLE", "You don't own this vehicle");
        }

        ParkingSpot spot = parkingSpotRepository.findByIdAndActiveTrueForUpdate(request.parkingSpotId())
                .orElseThrow(() -> new EntityNotFoundException("SPOT_NOT_FOUND", "Parking spot not found"));

        if (!vehicle.getVehicleType().equals(spot.getVehicleType())) {
            throw new BusinessException("VEHICLE_TYPE_MISMATCH", "Vehicle type does not match spot type");
        }

        ParkingLot lot = parkingLotRepository.findByIdAndActiveTrue(spot.getLotId())
                .orElseThrow(() -> new EntityNotFoundException("LOT_NOT_FOUND", "Parking lot not found"));
        if (lot.getOwnerId().equals(driverId)) {
            throw new ForbiddenException("CANNOT_BOOK_OWN_SPOT", "You cannot book a spot in your own lot");
        }

        if (reservationRepository.existsOverlappingReservation(
                request.parkingSpotId(), request.startTime(), request.endTime(), ACTIVE_STATUSES)) {
            throw new ConflictException("SPOT_NOT_AVAILABLE", "Spot is not available for the requested time");
        }

        BigDecimal totalPrice = PriceCalculator.calculate(request.startTime(), request.endTime(), spot.getPricePerHour());

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

        mapCacheService.evict(lot.getId());

        ReservationResponse response = reservationMapper.toResponse(reservation);
        if (idempotencyKey != null) {
            idempotencyService.store(idempotencyKey, response);
        }

        return response;
    }

    @Transactional(readOnly = true)
    public PagedResponse<ReservationResponse> getMyReservations(UUID driverId, ReservationStatus status, int page, int size) {
        Page<Reservation> reservationsPage;
        if (status == null) {
            reservationsPage = reservationRepository.findAllByDriverIdOrderByCreatedAtDesc(
                    driverId, PageRequest.of(page, size));
        } else {
            reservationsPage = reservationRepository.findAllByDriverIdAndStatusOrderByCreatedAtDesc(
                    driverId, status, PageRequest.of(page, size));
        }
        return PagedResponse.from(reservationsPage.map(reservationMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public ReservationResponse getReservationById(UUID reservationId, UUID callerId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new EntityNotFoundException("RESERVATION_NOT_FOUND", "Reservation not found"));

        if (reservation.getDriverId().equals(callerId)) {
            return reservationMapper.toResponse(reservation);
        }

        ParkingSpot spot = parkingSpotRepository.findByIdAndActiveTrue(reservation.getSpotId())
                .orElseThrow(() -> new EntityNotFoundException("SPOT_NOT_FOUND", "Parking spot not found"));
        ParkingLot lot = parkingLotRepository.findByIdAndActiveTrue(spot.getLotId())
                .orElseThrow(() -> new EntityNotFoundException("LOT_NOT_FOUND", "Parking lot not found"));

        if (lot.getOwnerId().equals(callerId)) {
            return reservationMapper.toResponse(reservation);
        }

        throw new ForbiddenException("NOT_AUTHORIZED", "Access denied");
    }

    @Transactional
    public ReservationResponse cancelReservation(UUID reservationId, UUID driverId) {
        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new EntityNotFoundException("RESERVATION_NOT_FOUND", "Reservation not found"));

        if (!reservation.getDriverId().equals(driverId)) {
            throw new ForbiddenException("NOT_AUTHORIZED", "Access denied");
        }

        if (reservation.getStatus() != ReservationStatus.RESERVED) {
            throw new BusinessException("INVALID_STATUS", "Cannot cancel a reservation that is not RESERVED");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        if (!reservation.getStartTime().isAfter(now.plusMinutes(30))) {
            throw new BusinessException("CANCEL_WINDOW_EXPIRED", "Cancellation must be at least 30 minutes before start time");
        }

        reservation.setStatus(ReservationStatus.CANCELLED);
        reservation = reservationRepository.save(reservation);

        ParkingSpot spot = parkingSpotRepository.findByIdAndActiveTrue(reservation.getSpotId())
                .orElseThrow(() -> new EntityNotFoundException("SPOT_NOT_FOUND", "Parking spot not found"));
        mapCacheService.evict(spot.getLotId());

        return reservationMapper.toResponse(reservation);
    }

    @Transactional
    public ReservationResponse checkInReservation(UUID reservationId, UUID driverId) {
        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new EntityNotFoundException("RESERVATION_NOT_FOUND", "Reservation not found"));

        if (!reservation.getDriverId().equals(driverId)) {
            throw new ForbiddenException("NOT_AUTHORIZED", "Access denied");
        }

        if (reservation.getStatus() != ReservationStatus.RESERVED) {
            throw new BusinessException("INVALID_STATUS", "Cannot check-in a reservation that is not RESERVED");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        if (now.isBefore(reservation.getStartTime().minusMinutes(15)) || now.isAfter(reservation.getStartTime().plusMinutes(30))) {
            throw new CheckInWindowException("Check-in is only allowed between 15 minutes before and 30 minutes after the start time");
        }

        reservation.setStatus(ReservationStatus.CHECKED_IN);
        reservation.setCheckedInAt(now);
        reservation = reservationRepository.save(reservation);

        return reservationMapper.toResponse(reservation);
    }

    @Transactional
    public CheckInLogResponse checkOutReservation(UUID reservationId, UUID driverId) {
        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new EntityNotFoundException("RESERVATION_NOT_FOUND", "Reservation not found"));

        if (!reservation.getDriverId().equals(driverId)) {
            throw new ForbiddenException("NOT_AUTHORIZED", "Access denied");
        }

        if (reservation.getStatus() != ReservationStatus.CHECKED_IN) {
            throw new BusinessException("INVALID_STATUS", "Cannot check-out a reservation that is not CHECKED_IN");
        }

        if (reservation.getCheckedInAt() == null) {
            throw new BusinessException("MISSING_CHECKIN_TIME", "Reservation is in CHECKED_IN state but has no check-in timestamp");
        }

        LocalDateTime checkOutTime = LocalDateTime.now(clock);
        int actualMinutes = (int) Duration.between(reservation.getCheckedInAt(), checkOutTime).toMinutes();

        reservation.setStatus(ReservationStatus.COMPLETED);
        reservationRepository.save(reservation);

        ParkingSpot spot = parkingSpotRepository.findByIdAndActiveTrue(reservation.getSpotId())
                .orElseThrow(() -> new EntityNotFoundException("SPOT_NOT_FOUND", "Parking spot not found"));
        mapCacheService.evict(spot.getLotId());

        CheckInLog log = CheckInLog.builder()
                .reservationId(reservation.getId())
                .checkInTime(reservation.getCheckedInAt())
                .checkOutTime(checkOutTime)
                .actualDurationMinutes(actualMinutes)
                .build();
        log = checkInLogRepository.save(log);

        return checkInLogMapper.toResponse(log);
    }

    @Transactional(readOnly = true)
    public PagedResponse<ReservationResponse> getReservationsByLotId(UUID lotId, UUID ownerId, int page, int size) {
        ParkingLot lot = parkingLotRepository.findByIdAndActiveTrue(lotId)
                .orElseThrow(() -> new EntityNotFoundException("LOT_NOT_FOUND", "Parking lot not found"));

        if (!lot.getOwnerId().equals(ownerId)) {
            throw new ForbiddenException("NOT_AUTHORIZED", "Access denied");
        }

        Page<Reservation> reservationsPage = reservationRepository.findAllByLotId(lotId, PageRequest.of(page, size));
        return PagedResponse.from(reservationsPage.map(reservationMapper::toResponse));
    }
}
