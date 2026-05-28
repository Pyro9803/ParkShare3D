package com.parkshare.reservation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.parkshare.reservation.dto.CreateReservationRequest;
import com.parkshare.reservation.dto.ReservationResponse;
import com.parkshare.shared.api.PagedResponse;
import com.parkshare.shared.exception.BusinessException;
import com.parkshare.shared.exception.ConflictException;
import com.parkshare.shared.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@WebMvcTest(ReservationController.class)
class ReservationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReservationService reservationService;

    @Test
    void create_returns201() throws Exception {
        UUID driverId = UUID.randomUUID();
        UUID spotId = UUID.randomUUID();
        UUID vehicleId = UUID.randomUUID();
        LocalDateTime start = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = start.plusHours(2);
        ReservationResponse response = new ReservationResponse(
                UUID.randomUUID(), spotId, vehicleId, driverId,
                ReservationStatus.RESERVED, start, end, new BigDecimal("100000.00"), Instant.now()
        );

        when(reservationService.createReservation(eq(driverId), any(CreateReservationRequest.class), isNull()))
                .thenReturn(response);

        mockMvc.perform(post("/api/reservations")
                        .principal(new UsernamePasswordAuthenticationToken(driverId, null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parkingSpotId": "%s",
                                  "vehicleId": "%s",
                                  "startTime": "%s",
                                  "endTime": "%s"
                                }
                                """.formatted(spotId, vehicleId, start, end)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.driverId").value(driverId.toString()));
    }

    @Test
    void create_missingBody_returns400() throws Exception {
        mockMvc.perform(post("/api/reservations")
                        .principal(new UsernamePasswordAuthenticationToken(UUID.randomUUID(), null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_spotNotAvailable_returns409() throws Exception {
        UUID driverId = UUID.randomUUID();
        LocalDateTime start = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = start.plusHours(2);

        when(reservationService.createReservation(eq(driverId), any(CreateReservationRequest.class), any()))
                .thenThrow(new ConflictException("SPOT_NOT_AVAILABLE", "Spot is not available"));

        mockMvc.perform(post("/api/reservations")
                        .principal(new UsernamePasswordAuthenticationToken(driverId, null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parkingSpotId": "%s",
                                  "vehicleId": "%s",
                                  "startTime": "%s",
                                  "endTime": "%s"
                                }
                                """.formatted(UUID.randomUUID(), UUID.randomUUID(), start, end)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SPOT_NOT_AVAILABLE"));
    }

    @Test
    void create_vehicleTypeMismatch_returns400() throws Exception {
        UUID driverId = UUID.randomUUID();
        LocalDateTime start = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = start.plusHours(2);

        when(reservationService.createReservation(eq(driverId), any(CreateReservationRequest.class), any()))
                .thenThrow(new BusinessException("VEHICLE_TYPE_MISMATCH", "Vehicle type does not match spot type"));

        mockMvc.perform(post("/api/reservations")
                        .principal(new UsernamePasswordAuthenticationToken(driverId, null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parkingSpotId": "%s",
                                  "vehicleId": "%s",
                                  "startTime": "%s",
                                  "endTime": "%s"
                                }
                                """.formatted(UUID.randomUUID(), UUID.randomUUID(), start, end)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VEHICLE_TYPE_MISMATCH"));
    }

    @Test
    void create_withIdempotencyKey_returns201() throws Exception {
        UUID driverId = UUID.randomUUID();
        UUID spotId = UUID.randomUUID();
        UUID vehicleId = UUID.randomUUID();
        String idempotencyKey = "test-key-123";
        LocalDateTime start = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = start.plusHours(2);
        ReservationResponse response = new ReservationResponse(
                UUID.randomUUID(), spotId, vehicleId, driverId,
                ReservationStatus.RESERVED, start, end, new BigDecimal("100000.00"), Instant.now()
        );

        when(reservationService.createReservation(eq(driverId), any(CreateReservationRequest.class), eq(idempotencyKey)))
                .thenReturn(response);

        mockMvc.perform(post("/api/reservations")
                        .principal(new UsernamePasswordAuthenticationToken(driverId, null))
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parkingSpotId": "%s",
                                  "vehicleId": "%s",
                                  "startTime": "%s",
                                  "endTime": "%s"
                                }
                                """.formatted(spotId, vehicleId, start, end)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void getMyReservations_returns200() throws Exception {
        UUID driverId = UUID.randomUUID();
        when(reservationService.getMyReservations(eq(driverId), eq(0), eq(20)))
                .thenReturn(new PagedResponse<>(List.of(), 0, 0, 0, 20));

        mockMvc.perform(get("/api/reservations/my")
                        .principal(new UsernamePasswordAuthenticationToken(driverId, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
