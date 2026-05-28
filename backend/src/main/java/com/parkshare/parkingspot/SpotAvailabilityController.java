package com.parkshare.parkingspot;

import java.util.List;
import java.util.UUID;

import com.parkshare.parkingspot.dto.AvailabilityResponse;
import com.parkshare.parkingspot.dto.ReplaceAvailabilityRequest;
import com.parkshare.shared.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/parking-spots")
public class SpotAvailabilityController {

    private final SpotAvailabilityService spotAvailabilityService;

    public SpotAvailabilityController(SpotAvailabilityService spotAvailabilityService) {
        this.spotAvailabilityService = spotAvailabilityService;
    }

    @PostMapping("/{spotId}/availability")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('OWNER')")
    public ApiResponse<List<AvailabilityResponse>> replaceAvailability(
            @PathVariable UUID spotId,
            @Valid @RequestBody ReplaceAvailabilityRequest request,
            Authentication authentication
    ) {
        UUID callerId = (UUID) authentication.getPrincipal();
        return ApiResponse.success(spotAvailabilityService.replaceAvailability(spotId, callerId, request));
    }

    @GetMapping("/{spotId}/availability")
    public ApiResponse<List<AvailabilityResponse>> getAvailability(@PathVariable UUID spotId) {
        return ApiResponse.success(spotAvailabilityService.getAvailability(spotId));
    }

    @DeleteMapping("/{spotId}/availability/{availabilityId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('OWNER')")
    public void deleteAvailability(
            @PathVariable UUID spotId,
            @PathVariable UUID availabilityId,
            Authentication authentication
    ) {
        UUID callerId = (UUID) authentication.getPrincipal();
        spotAvailabilityService.deleteAvailability(spotId, availabilityId, callerId);
    }
}
