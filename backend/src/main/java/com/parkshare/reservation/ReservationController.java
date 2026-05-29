package com.parkshare.reservation;

import java.util.UUID;

import com.parkshare.reservation.dto.CheckInLogResponse;
import com.parkshare.reservation.dto.CreateReservationRequest;
import com.parkshare.reservation.dto.ReservationResponse;
import com.parkshare.shared.api.ApiResponse;
import com.parkshare.shared.api.PagedResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping("/reservations")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<ApiResponse<ReservationResponse>> createReservation(
            @Valid @RequestBody CreateReservationRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication authentication) {
        if (idempotencyKey != null && idempotencyKey.isBlank()) {
            idempotencyKey = null;
        }
        UUID driverId = (UUID) authentication.getPrincipal();
        ReservationResponse response = reservationService.createReservation(driverId, request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/reservations/my")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<ApiResponse<PagedResponse<ReservationResponse>>> getMyReservations(
            @RequestParam(required = false) ReservationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        UUID driverId = (UUID) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(reservationService.getMyReservations(driverId, status, page, size)));
    }

    @GetMapping("/reservations/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ReservationResponse>> getReservationById(
            @PathVariable UUID id,
            Authentication authentication) {
        UUID callerId = (UUID) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(reservationService.getReservationById(id, callerId)));
    }

    @PostMapping("/reservations/{id}/cancel")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<ApiResponse<ReservationResponse>> cancelReservation(
            @PathVariable UUID id,
            Authentication authentication) {
        UUID driverId = (UUID) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(reservationService.cancelReservation(id, driverId)));
    }

    @PostMapping("/reservations/{id}/check-in")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<ApiResponse<ReservationResponse>> checkInReservation(
            @PathVariable UUID id,
            Authentication authentication) {
        UUID driverId = (UUID) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(reservationService.checkInReservation(id, driverId)));
    }

    @PostMapping("/reservations/{id}/check-out")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<ApiResponse<CheckInLogResponse>> checkOutReservation(
            @PathVariable UUID id,
            Authentication authentication) {
        UUID driverId = (UUID) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(reservationService.checkOutReservation(id, driverId)));
    }

    @GetMapping("/parking-lots/{lotId}/reservations")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<PagedResponse<ReservationResponse>>> getReservationsByLotId(
            @PathVariable UUID lotId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        UUID ownerId = (UUID) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(reservationService.getReservationsByLotId(lotId, ownerId, page, size)));
    }
}
