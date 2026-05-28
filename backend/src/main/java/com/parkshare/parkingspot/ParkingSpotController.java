package com.parkshare.parkingspot;

import java.time.LocalDateTime;
import java.util.UUID;

import com.parkshare.parkinglot.ParkingLotService;
import com.parkshare.parkinglot.dto.UpdateSpotRequest;
import com.parkshare.parkingspot.dto.SpotResponse;
import com.parkshare.parkingspot.dto.SpotSearchResponse;
import com.parkshare.shared.api.ApiResponse;
import com.parkshare.shared.api.PagedResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/parking-spots")
@Validated
public class ParkingSpotController {

    private final ParkingLotService parkingLotService;
    private final SpotSearchService spotSearchService;

    public ParkingSpotController(ParkingLotService parkingLotService, SpotSearchService spotSearchService) {
        this.parkingLotService = parkingLotService;
        this.spotSearchService = spotSearchService;
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<PagedResponse<SpotSearchResponse>>> searchSpots(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam VehicleType vehicleType,
            @RequestParam(required = false) UUID lotId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                spotSearchService.searchSpots(startTime, endTime, vehicleType, lotId, page, size)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public ApiResponse<SpotResponse> updateSpot(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSpotRequest request,
            Authentication authentication
    ) {
        UUID callerId = (UUID) authentication.getPrincipal();
        return ApiResponse.success(parkingLotService.updateSpot(id, callerId, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<Void> deleteSpot(@PathVariable UUID id, Authentication authentication) {
        UUID callerId = (UUID) authentication.getPrincipal();
        parkingLotService.deleteSpot(id, callerId);
        return ResponseEntity.noContent().build();
    }
}
