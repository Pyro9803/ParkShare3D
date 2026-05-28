package com.parkshare.parkinglot;

import java.util.UUID;

import com.parkshare.parkinglot.dto.CreateLotRequest;
import com.parkshare.parkinglot.dto.CreateSpotRequest;
import com.parkshare.parkinglot.dto.LotDetailResponse;
import com.parkshare.parkinglot.dto.LotResponse;
import com.parkshare.parkinglot.dto.UpdateLotRequest;
import com.parkshare.parkingspot.dto.SpotResponse;
import com.parkshare.shared.api.ApiResponse;
import com.parkshare.shared.api.PagedResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/parking-lots")
public class ParkingLotController {

    private final ParkingLotService parkingLotService;

    public ParkingLotController(ParkingLotService parkingLotService) {
        this.parkingLotService = parkingLotService;
    }

    @PostMapping
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<LotDetailResponse>> createLot(
            @Valid @RequestBody CreateLotRequest request,
            Authentication authentication
    ) {
        UUID ownerId = (UUID) authentication.getPrincipal();
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(parkingLotService.createLot(ownerId, request)));
    }

    @GetMapping
    public ApiResponse<PagedResponse<LotResponse>> listLots(
            @RequestParam(defaultValue = "true") boolean verified,
            Pageable pageable
    ) {
        return ApiResponse.success(parkingLotService.listLots(pageable, verified));
    }

    @GetMapping("/{id}")
    public ApiResponse<LotDetailResponse> getLot(@PathVariable UUID id) {
        return ApiResponse.success(parkingLotService.getLot(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public ApiResponse<LotResponse> updateLot(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateLotRequest request,
            Authentication authentication
    ) {
        UUID callerId = (UUID) authentication.getPrincipal();
        return ApiResponse.success(parkingLotService.updateLot(id, callerId, request));
    }

    @PostMapping("/{id}/spots")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<SpotResponse>> addSpot(
            @PathVariable UUID id,
            @Valid @RequestBody CreateSpotRequest request,
            Authentication authentication
    ) {
        UUID callerId = (UUID) authentication.getPrincipal();
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(parkingLotService.addSpot(id, callerId, request)));
    }
}
