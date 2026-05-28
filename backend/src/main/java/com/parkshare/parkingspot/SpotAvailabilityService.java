package com.parkshare.parkingspot;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import com.parkshare.parkinglot.ParkingLot;
import com.parkshare.parkinglot.ParkingLotRepository;
import com.parkshare.parkingspot.dto.AvailabilityResponse;
import com.parkshare.parkingspot.dto.CreateAvailabilityRequest;
import com.parkshare.parkingspot.dto.ReplaceAvailabilityRequest;
import com.parkshare.shared.exception.BusinessException;
import com.parkshare.shared.exception.ConflictException;
import com.parkshare.shared.exception.EntityNotFoundException;
import com.parkshare.shared.exception.ForbiddenException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SpotAvailabilityService {

    private final SpotAvailabilityRepository spotAvailabilityRepository;
    private final ParkingSpotRepository parkingSpotRepository;
    private final ParkingLotRepository parkingLotRepository;

    public SpotAvailabilityService(
            SpotAvailabilityRepository spotAvailabilityRepository,
            ParkingSpotRepository parkingSpotRepository,
            ParkingLotRepository parkingLotRepository
    ) {
        this.spotAvailabilityRepository = spotAvailabilityRepository;
        this.parkingSpotRepository = parkingSpotRepository;
        this.parkingLotRepository = parkingLotRepository;
    }

    @Transactional
    public List<AvailabilityResponse> replaceAvailability(UUID spotId, UUID callerId, ReplaceAvailabilityRequest request) {
        verifySpotOwner(spotId, callerId);

        List<CreateAvailabilityRequest> slots = request.slots();
        slots.forEach(SpotAvailabilityService::validateTimeRange);
        validateNoOverlaps(slots);

        spotAvailabilityRepository.softDeleteAllBySpotId(spotId);

        List<SpotAvailability> newAvailabilities = slots.stream()
                .map(slot -> SpotAvailability.builder()
                        .spotId(spotId)
                        .dayOfWeek(slot.dayOfWeek())
                        .startTime(slot.startTime())
                        .endTime(slot.endTime())
                        .active(true)
                        .build())
                .toList();

        return spotAvailabilityRepository.saveAll(newAvailabilities).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AvailabilityResponse> getAvailability(UUID spotId) {
        parkingSpotRepository.findByIdAndActiveTrue(spotId)
                .orElseThrow(() -> new EntityNotFoundException("SPOT_NOT_FOUND", "Parking spot not found"));

        return spotAvailabilityRepository.findAllBySpotIdAndActiveTrue(spotId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void deleteAvailability(UUID spotId, UUID availabilityId, UUID callerId) {
        SpotAvailability availability = spotAvailabilityRepository.findByIdAndActiveTrue(availabilityId)
                .filter(sa -> sa.getSpotId().equals(spotId))
                .orElseThrow(() -> new EntityNotFoundException("AVAILABILITY_NOT_FOUND", "Availability slot not found"));

        verifySpotOwner(spotId, callerId);

        availability.setActive(false);
        spotAvailabilityRepository.save(availability);
    }

    private void verifySpotOwner(UUID spotId, UUID callerId) {
        ParkingSpot spot = parkingSpotRepository.findByIdAndActiveTrue(spotId)
                .orElseThrow(() -> new EntityNotFoundException("SPOT_NOT_FOUND", "Parking spot not found"));
        ParkingLot lot = parkingLotRepository.findByIdAndActiveTrue(spot.getLotId())
                .orElseThrow(() -> new EntityNotFoundException("LOT_NOT_FOUND", "Parking lot not found"));
        if (!lot.getOwnerId().equals(callerId)) {
            throw new ForbiddenException("NOT_LOT_OWNER", "Only the parking lot owner can modify this resource");
        }
    }

    private static void validateTimeRange(CreateAvailabilityRequest slot) {
        if (!slot.startTime().isBefore(slot.endTime())) {
            throw new BusinessException("AVAILABILITY_INVALID_TIME_RANGE", "Start time must be before end time");
        }
    }

    private static void validateNoOverlaps(List<CreateAvailabilityRequest> slots) {
        slots.stream()
                .collect(Collectors.groupingBy(CreateAvailabilityRequest::dayOfWeek))
                .values()
                .forEach(daySlots -> {
                    var sorted = daySlots.stream()
                            .sorted(Comparator.comparing(CreateAvailabilityRequest::startTime))
                            .toList();
                    for (int i = 1; i < sorted.size(); i++) {
                        if (sorted.get(i - 1).endTime().isAfter(sorted.get(i).startTime())) {
                            throw new ConflictException("AVAILABILITY_OVERLAP", "Schedule slots overlap for the same day");
                        }
                    }
                });
    }

    private AvailabilityResponse toResponse(SpotAvailability sa) {
        return new AvailabilityResponse(
                sa.getId(),
                sa.getSpotId(),
                sa.getDayOfWeek(),
                sa.getStartTime(),
                sa.getEndTime()
        );
    }
}
