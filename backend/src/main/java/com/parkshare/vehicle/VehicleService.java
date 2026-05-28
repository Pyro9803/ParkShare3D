package com.parkshare.vehicle;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.parkshare.reservation.ReservationRepository;
import com.parkshare.reservation.ReservationStatus;
import com.parkshare.shared.exception.ConflictException;
import com.parkshare.shared.exception.EntityNotFoundException;
import com.parkshare.shared.exception.ForbiddenException;
import com.parkshare.vehicle.dto.CreateVehicleRequest;
import com.parkshare.vehicle.dto.UpdateVehicleRequest;
import com.parkshare.vehicle.dto.VehicleResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VehicleService {

    private static final Set<ReservationStatus> ACTIVE_STATUSES =
            Set.of(ReservationStatus.RESERVED, ReservationStatus.CHECKED_IN);

    private final VehicleRepository vehicleRepository;
    private final VehicleMapper vehicleMapper;
    private final ReservationRepository reservationRepository;

    public VehicleService(VehicleRepository vehicleRepository, VehicleMapper vehicleMapper,
                           ReservationRepository reservationRepository) {
        this.vehicleRepository = vehicleRepository;
        this.vehicleMapper = vehicleMapper;
        this.reservationRepository = reservationRepository;
    }

    @Transactional
    public VehicleResponse createVehicle(UUID userId, CreateVehicleRequest request) {
        // Guard 1 (Fast-fail): Check for existing active plate for this user
        if (vehicleRepository.existsByUserIdAndLicensePlateAndActiveTrue(userId, request.licensePlate())) {
            throw new ConflictException("LICENSE_PLATE_DUPLICATE", "This license plate is already registered");
        }

        Vehicle vehicle = Vehicle.builder()
                .userId(userId)
                .licensePlate(request.licensePlate())
                .vehicleType(request.vehicleType())
                .brand(request.brand())
                .model(request.model())
                .color(request.color())
                .active(true)
                .build();

        // Guard 2 (Real guard): DB unique constraint uq_vehicles_active_plate handles TOCTOU race conditions
        return vehicleMapper.toResponse(vehicleRepository.save(vehicle));
    }

    @Transactional(readOnly = true)
    public List<VehicleResponse> getMyVehicles(UUID userId) {
        return vehicleMapper.toResponseList(vehicleRepository.findAllByUserIdAndActiveTrue(userId));
    }

    @Transactional
    public VehicleResponse updateVehicle(UUID vehicleId, UUID callerId, UpdateVehicleRequest request) {
        Vehicle vehicle = findVehicleOrThrow(vehicleId);

        if (!vehicle.getUserId().equals(callerId)) {
            throw new ForbiddenException("NOT_VEHICLE_OWNER", "Only the vehicle owner can modify this resource");
        }

        if (vehicleRepository.existsByUserIdAndLicensePlateAndActiveTrueAndIdNot(callerId, request.licensePlate(), vehicleId)) {
            throw new ConflictException("LICENSE_PLATE_DUPLICATE", "This license plate is already registered");
        }

        vehicle.setLicensePlate(request.licensePlate());
        vehicle.setVehicleType(request.vehicleType());
        vehicle.setBrand(request.brand());
        vehicle.setModel(request.model());
        vehicle.setColor(request.color());

        return vehicleMapper.toResponse(vehicleRepository.save(vehicle));
    }

    @Transactional
    public void deleteVehicle(UUID vehicleId, UUID callerId) {
        Vehicle vehicle = vehicleRepository.findByIdAndActiveTrueForUpdate(vehicleId)
                .orElseThrow(() -> new EntityNotFoundException("VEHICLE_NOT_FOUND", "Vehicle not found"));

        if (!vehicle.getUserId().equals(callerId)) {
            throw new ForbiddenException("NOT_VEHICLE_OWNER", "Only the vehicle owner can delete this resource");
        }

        if (reservationRepository.existsByVehicleIdAndStatusIn(vehicleId, ACTIVE_STATUSES)) {
            throw new ConflictException("VEHICLE_HAS_ACTIVE_RESERVATIONS", "Cannot delete vehicle with active reservations");
        }
        vehicle.setActive(false);
        vehicleRepository.save(vehicle);
    }

    private Vehicle findVehicleOrThrow(UUID vehicleId) {
        return vehicleRepository.findByIdAndActiveTrue(vehicleId)
                .orElseThrow(() -> new EntityNotFoundException("VEHICLE_NOT_FOUND", "Vehicle not found"));
    }
}
