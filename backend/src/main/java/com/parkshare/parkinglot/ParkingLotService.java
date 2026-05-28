package com.parkshare.parkinglot;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.parkshare.parkinglot.dto.CreateLotRequest;
import com.parkshare.parkinglot.dto.CreateSpotRequest;
import com.parkshare.parkinglot.dto.LotDetailResponse;
import com.parkshare.parkinglot.dto.LotResponse;
import com.parkshare.parkinglot.dto.UpdateLotRequest;
import com.parkshare.parkinglot.dto.UpdateSpotRequest;
import com.parkshare.parkingspot.ParkingSpot;
import com.parkshare.parkingspot.ParkingSpotRepository;
import com.parkshare.parkingspot.dto.SpotResponse;
import com.parkshare.shared.api.PagedResponse;
import com.parkshare.shared.exception.ConflictException;
import com.parkshare.shared.exception.EntityNotFoundException;
import com.parkshare.shared.exception.ForbiddenException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ParkingLotService {

    private final ParkingLotRepository parkingLotRepository;
    private final ParkingSpotRepository parkingSpotRepository;

    public ParkingLotService(
            ParkingLotRepository parkingLotRepository,
            ParkingSpotRepository parkingSpotRepository
    ) {
        this.parkingLotRepository = parkingLotRepository;
        this.parkingSpotRepository = parkingSpotRepository;
    }

    @Transactional
    public LotDetailResponse createLot(UUID ownerId, CreateLotRequest request) {
        ParkingLot lot = ParkingLot.builder()
                .ownerId(ownerId)
                .name(request.name())
                .address(request.address())
                .description(request.description())
                .floor(request.floor())
                .verified(false)
                .active(true)
                .build();
        ParkingLot savedLot = parkingLotRepository.save(lot);

        Set<String> seenCodes = new HashSet<>();
        List<SpotResponse> spots = spotRequests(request).stream()
                .map(spotRequest -> createSpot(savedLot.getId(), spotRequest, seenCodes))
                .toList();

        return toLotDetailResponse(savedLot, spots);
    }

    @Transactional(readOnly = true)
    public PagedResponse<LotResponse> listLots(Pageable pageable, boolean verifiedOnly) {
        Page<ParkingLot> lots = verifiedOnly
                ? parkingLotRepository.findAllByActiveTrueAndVerifiedTrue(pageable)
                : parkingLotRepository.findAllByActiveTrue(pageable);
        return PagedResponse.from(lots.map(this::toLotResponse));
    }

    @Transactional(readOnly = true)
    public LotDetailResponse getLot(UUID lotId) {
        ParkingLot lot = findActiveLotOrThrow(lotId);
        List<SpotResponse> spots = parkingSpotRepository.findAllByLotIdAndActiveTrue(lotId).stream()
                .map(this::toSpotResponse)
                .toList();
        return toLotDetailResponse(lot, spots);
    }

    @Transactional
    public LotResponse updateLot(UUID lotId, UUID callerId, UpdateLotRequest request) {
        ParkingLot lot = findActiveLotOrThrow(lotId);
        verifyOwner(lot, callerId);
        lot.setName(request.name());
        lot.setAddress(request.address());
        lot.setDescription(request.description());
        lot.setFloor(request.floor());
        return toLotResponse(parkingLotRepository.save(lot));
    }

    @Transactional
    public SpotResponse addSpot(UUID lotId, UUID callerId, CreateSpotRequest request) {
        ParkingLot lot = findActiveLotOrThrow(lotId);
        verifyOwner(lot, callerId);
        return createSpot(lotId, request, new HashSet<>());
    }

    @Transactional
    public SpotResponse updateSpot(UUID spotId, UUID callerId, UpdateSpotRequest request) {
        ParkingSpot spot = findSpotOrThrow(spotId);
        ParkingLot lot = findActiveLotOrThrow(spot.getLotId());
        verifyOwner(lot, callerId);
        if (parkingSpotRepository.existsByLotIdAndCodeAndActiveTrueAndIdNot(spot.getLotId(), request.code(), spotId)) {
            throw duplicateSpotCode();
        }

        spot.setCode(request.code());
        spot.setX(request.x());
        spot.setY(request.y());
        spot.setZ(request.z());
        spot.setWidth(request.width());
        spot.setLength(request.length());
        spot.setVehicleType(request.vehicleType());
        spot.setPricePerHour(request.pricePerHour());
        return toSpotResponse(parkingSpotRepository.save(spot));
    }

    @Transactional
    public void deleteSpot(UUID spotId, UUID callerId) {
        ParkingSpot spot = findSpotOrThrow(spotId);
        ParkingLot lot = findActiveLotOrThrow(spot.getLotId());
        verifyOwner(lot, callerId);
        // TODO Task 1.8: reject if active reservations exist.
        spot.setActive(false);
        parkingSpotRepository.save(spot);
    }

    @Transactional
    public LotResponse verifyLot(UUID lotId) {
        ParkingLot lot = findActiveLotOrThrow(lotId);
        lot.setVerified(true);
        return toLotResponse(parkingLotRepository.save(lot));
    }

    private SpotResponse createSpot(UUID lotId, CreateSpotRequest request, Set<String> seenCodes) {
        if (!seenCodes.add(request.code())
                || parkingSpotRepository.existsByLotIdAndCodeAndActiveTrue(lotId, request.code())) {
            throw duplicateSpotCode();
        }
        ParkingSpot spot = ParkingSpot.builder()
                .lotId(lotId)
                .code(request.code())
                .x(request.x())
                .y(request.y())
                .z(request.z())
                .width(request.width())
                .length(request.length())
                .vehicleType(request.vehicleType())
                .pricePerHour(request.pricePerHour())
                .active(true)
                .build();
        return toSpotResponse(parkingSpotRepository.save(spot));
    }

    private static List<CreateSpotRequest> spotRequests(CreateLotRequest request) {
        return request.spots() == null ? List.of() : request.spots();
    }

    private ParkingLot findActiveLotOrThrow(UUID lotId) {
        return parkingLotRepository.findByIdAndActiveTrue(lotId)
                .orElseThrow(() -> new EntityNotFoundException("LOT_NOT_FOUND", "Parking lot not found"));
    }

    private ParkingSpot findSpotOrThrow(UUID spotId) {
        return parkingSpotRepository.findByIdAndActiveTrue(spotId)
                .orElseThrow(() -> new EntityNotFoundException("SPOT_NOT_FOUND", "Parking spot not found"));
    }

    private static void verifyOwner(ParkingLot lot, UUID callerId) {
        if (!lot.getOwnerId().equals(callerId)) {
            throw new ForbiddenException("NOT_LOT_OWNER", "Only the parking lot owner can modify this resource");
        }
    }

    private static ConflictException duplicateSpotCode() {
        return new ConflictException("SPOT_CODE_DUPLICATE", "Spot code already exists for this parking lot");
    }

    private LotDetailResponse toLotDetailResponse(ParkingLot lot, List<SpotResponse> spots) {
        return new LotDetailResponse(
                lot.getId(),
                lot.getOwnerId(),
                lot.getName(),
                lot.getAddress(),
                lot.getDescription(),
                lot.getFloor(),
                lot.isVerified(),
                lot.isActive(),
                lot.getCreatedAt(),
                spots
        );
    }

    private LotResponse toLotResponse(ParkingLot lot) {
        return new LotResponse(
                lot.getId(),
                lot.getOwnerId(),
                lot.getName(),
                lot.getAddress(),
                lot.getDescription(),
                lot.getFloor(),
                lot.isVerified(),
                lot.isActive(),
                lot.getCreatedAt()
        );
    }

    private SpotResponse toSpotResponse(ParkingSpot spot) {
        return new SpotResponse(
                spot.getId(),
                spot.getLotId(),
                spot.getCode(),
                spot.getX(),
                spot.getY(),
                spot.getZ(),
                spot.getWidth(),
                spot.getLength(),
                spot.getVehicleType(),
                spot.getPricePerHour(),
                spot.isActive()
        );
    }
}
