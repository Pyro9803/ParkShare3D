package com.parkshare.parkinglot;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.parkshare.parkinglot.dto.ParkingLotMapResponse;
import com.parkshare.parkinglot.dto.SpotMapEntry;
import com.parkshare.parkingspot.ParkingSpot;
import com.parkshare.parkingspot.ParkingSpotRepository;
import com.parkshare.parkingspot.SpotStatus;
import com.parkshare.reservation.ReservationRepository;
import com.parkshare.shared.cache.MapCacheService;
import com.parkshare.shared.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ParkingLotMapService {

    private final ParkingLotRepository parkingLotRepository;
    private final ParkingSpotRepository parkingSpotRepository;
    private final ReservationRepository reservationRepository;
    private final MapCacheService mapCacheService;
    private final Clock clock;

    public ParkingLotMapService(ParkingLotRepository parkingLotRepository,
                                ParkingSpotRepository parkingSpotRepository,
                                ReservationRepository reservationRepository,
                                MapCacheService mapCacheService,
                                Clock clock) {
        this.parkingLotRepository = parkingLotRepository;
        this.parkingSpotRepository = parkingSpotRepository;
        this.reservationRepository = reservationRepository;
        this.mapCacheService = mapCacheService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ParkingLotMapResponse getMap(UUID lotId) {
        var cached = mapCacheService.get(lotId);
        if (cached.isPresent()) {
            return cached.get();
        }

        ParkingLot lot = parkingLotRepository.findByIdAndActiveTrue(lotId)
                .orElseThrow(() -> new EntityNotFoundException("LOT_NOT_FOUND", "Parking lot not found"));

        List<ParkingSpot> spots = parkingSpotRepository.findAllByLotId(lotId);
        LocalDateTime now = LocalDateTime.now(clock);

        Set<UUID> occupiedIds = new HashSet<>();
        Set<UUID> pendingIds = new HashSet<>();

        if (lot.isVerified()) {
            occupiedIds.addAll(reservationRepository.findOccupiedSpotIdsForLot(lotId, now));
            pendingIds.addAll(reservationRepository.findPendingSpotIdsForLot(lotId, now));
        }

        List<SpotMapEntry> spotEntries = spots.stream()
                .map(s -> new SpotMapEntry(
                        s.getId(),
                        s.getCode(),
                        s.getX(),
                        s.getY(),
                        s.getZ(),
                        s.getWidth(),
                        s.getLength(),
                        s.getVehicleType(),
                        s.getPricePerHour(),
                        computeStatus(s, lot.isVerified(), occupiedIds, pendingIds)
                ))
                .toList();

        ParkingLotMapResponse response = new ParkingLotMapResponse(
                lot.getId(),
                lot.getName(),
                lot.getFloor(),
                spotEntries
        );

        mapCacheService.store(lotId, response);
        return response;
    }

    private SpotStatus computeStatus(ParkingSpot spot, boolean lotVerified, Set<UUID> occupiedIds, Set<UUID> pendingIds) {
        if (!lotVerified || !spot.isActive()) {
            return SpotStatus.UNAVAILABLE;
        }
        if (occupiedIds.contains(spot.getId())) {
            return SpotStatus.OCCUPIED;
        }
        if (pendingIds.contains(spot.getId())) {
            return SpotStatus.PENDING_BOOKING;
        }
        return SpotStatus.AVAILABLE;
    }
}
