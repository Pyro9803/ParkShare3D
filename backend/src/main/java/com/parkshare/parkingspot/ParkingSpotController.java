package com.parkshare.parkingspot;

import java.util.UUID;

import com.parkshare.parkinglot.ParkingLotService;
import com.parkshare.parkinglot.dto.UpdateSpotRequest;
import com.parkshare.parkingspot.dto.SpotResponse;
import com.parkshare.shared.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/parking-spots")
public class ParkingSpotController {

    private final ParkingLotService parkingLotService;

    public ParkingSpotController(ParkingLotService parkingLotService) {
        this.parkingLotService = parkingLotService;
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
