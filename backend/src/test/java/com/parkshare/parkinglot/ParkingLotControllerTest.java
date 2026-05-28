package com.parkshare.parkinglot;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.parkshare.admin.AdminParkingLotController;
import com.parkshare.parkinglot.dto.CreateLotRequest;
import com.parkshare.parkinglot.dto.CreateSpotRequest;
import com.parkshare.parkinglot.dto.LotDetailResponse;
import com.parkshare.parkinglot.dto.LotResponse;
import com.parkshare.parkinglot.dto.UpdateLotRequest;
import com.parkshare.parkingspot.ParkingSpotController;
import com.parkshare.parkingspot.VehicleType;
import com.parkshare.parkingspot.dto.SpotResponse;
import com.parkshare.shared.api.PagedResponse;
import com.parkshare.shared.exception.ConflictException;
import com.parkshare.shared.exception.EntityNotFoundException;
import com.parkshare.shared.exception.ForbiddenException;
import com.parkshare.shared.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@WebMvcTest({ParkingLotController.class, ParkingSpotController.class, AdminParkingLotController.class})
class ParkingLotControllerTest {

    private final MockMvc mockMvc;

    @MockBean
    private ParkingLotService parkingLotService;

    @Autowired
    ParkingLotControllerTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void createLotAsOwnerReturnsCreatedEnvelope() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID lotId = UUID.randomUUID();
        when(parkingLotService.createLot(eq(ownerId), any(CreateLotRequest.class)))
                .thenReturn(lotDetail(lotId, ownerId, List.of(spot(UUID.randomUUID(), lotId, "A-01"))));

        mockMvc.perform(post("/api/parking-lots")
                        .principal(authentication(ownerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Central Garage",
                                  "address": "123 Main St",
                                  "description": "Covered parking",
                                  "floor": 2,
                                  "spots": [
                                    {
                                      "code": "A-01",
                                      "x": 1.0,
                                      "y": 2.0,
                                      "z": 3.0,
                                      "width": 2.5,
                                      "length": 5.0,
                                      "vehicleType": "CAR",
                                      "pricePerHour": 2.75
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(lotId.toString()))
                .andExpect(jsonPath("$.data.ownerId").value(ownerId.toString()))
                .andExpect(jsonPath("$.data.spots[0].code").value("A-01"));
    }

    @Test
    void listLotsPublicReturnsPagedEnvelope() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID lotId = UUID.randomUUID();
        when(parkingLotService.listLots(any(Pageable.class), eq(true)))
                .thenReturn(new PagedResponse<>(
                        List.of(lot(lotId, ownerId)),
                        1,
                        1,
                        0,
                        20
                ));

        mockMvc.perform(get("/api/parking-lots"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].id").value(lotId.toString()))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20));
    }

    @Test
    void getLotNotFoundReturnsNotFoundEnvelope() throws Exception {
        UUID lotId = UUID.randomUUID();
        when(parkingLotService.getLot(lotId))
                .thenThrow(new EntityNotFoundException("LOT_NOT_FOUND", "Parking lot not found"));

        mockMvc.perform(get("/api/parking-lots/{id}", lotId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("LOT_NOT_FOUND"));
    }

    @Test
    void updateLotNotOwnerReturnsForbiddenEnvelope() throws Exception {
        UUID callerId = UUID.randomUUID();
        UUID lotId = UUID.randomUUID();
        when(parkingLotService.updateLot(eq(lotId), eq(callerId), any(UpdateLotRequest.class)))
                .thenThrow(new ForbiddenException("NOT_LOT_OWNER", "Only the parking lot owner can modify this resource"));

        mockMvc.perform(put("/api/parking-lots/{id}", lotId)
                        .principal(authentication(callerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Updated Garage",
                                  "address": "456 Updated Ave",
                                  "description": "Updated",
                                  "floor": 4
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("NOT_LOT_OWNER"));
    }

    @Test
    void addSpotDuplicateCodeReturnsConflictEnvelope() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID lotId = UUID.randomUUID();
        when(parkingLotService.addSpot(eq(lotId), eq(ownerId), any(CreateSpotRequest.class)))
                .thenThrow(new ConflictException("SPOT_CODE_DUPLICATE", "Spot code already exists"));

        mockMvc.perform(post("/api/parking-lots/{id}/spots", lotId)
                        .principal(authentication(ownerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "A-01",
                                  "x": 1.0,
                                  "y": 2.0,
                                  "z": 3.0,
                                  "width": 2.5,
                                  "length": 5.0,
                                  "vehicleType": "CAR",
                                  "pricePerHour": 2.75
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("SPOT_CODE_DUPLICATE"));
    }

    @Test
    void deleteSpotReturnsNoContent() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID spotId = UUID.randomUUID();

        mockMvc.perform(delete("/api/parking-spots/{id}", spotId)
                        .principal(authentication(ownerId)))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(parkingLotService).deleteSpot(spotId, ownerId);
    }

    @Test
    void verifyLotReturnsVerifiedEnvelope() throws Exception {
        UUID lotId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        LotResponse verifiedLot = new LotResponse(
                lotId,
                ownerId,
                "Central Garage",
                "123 Main St",
                "Covered parking",
                3,
                true,
                true,
                Instant.parse("2026-01-01T00:00:00Z")
        );
        when(parkingLotService.verifyLot(lotId)).thenReturn(verifiedLot);

        mockMvc.perform(post("/api/admin/parking-lots/{id}/verify", lotId)
                        .principal(authentication(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(lotId.toString()))
                .andExpect(jsonPath("$.data.verified").value(true));
    }

    @Test
    void createLotValidationErrorReturnsBadRequestEnvelope() throws Exception {
        mockMvc.perform(post("/api/parking-lots")
                        .principal(authentication(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "",
                                  "address": "",
                                  "floor": 0,
                                  "spots": [
                                    {
                                      "code": "",
                                      "width": -2.5,
                                      "length": -5.0,
                                      "vehicleType": null,
                                      "pricePerHour": -1
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.details.name").exists())
                .andExpect(jsonPath("$.error.details.address").exists())
                .andExpect(jsonPath("$.error.details.floor").exists())
                .andExpect(jsonPath("$.error.details['spots[0].code']").exists())
                .andExpect(jsonPath("$.error.details['spots[0].width']").exists())
                .andExpect(jsonPath("$.error.details['spots[0].length']").exists())
                .andExpect(jsonPath("$.error.details['spots[0].vehicleType']").exists())
                .andExpect(jsonPath("$.error.details['spots[0].pricePerHour']").exists());
    }

    private static UsernamePasswordAuthenticationToken authentication(UUID userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }

    private static LotResponse lot(UUID lotId, UUID ownerId) {
        return new LotResponse(
                lotId,
                ownerId,
                "Central Garage",
                "123 Main St",
                "Covered parking",
                3,
                true,
                true,
                Instant.parse("2026-01-01T00:00:00Z")
        );
    }

    private static LotDetailResponse lotDetail(UUID lotId, UUID ownerId, List<SpotResponse> spots) {
        return new LotDetailResponse(
                lotId,
                ownerId,
                "Central Garage",
                "123 Main St",
                "Covered parking",
                3,
                false,
                true,
                Instant.parse("2026-01-01T00:00:00Z"),
                spots
        );
    }

    private static SpotResponse spot(UUID spotId, UUID lotId, String code) {
        return new SpotResponse(
                spotId,
                lotId,
                code,
                1.0,
                2.0,
                3.0,
                2.5,
                5.0,
                VehicleType.CAR,
                new BigDecimal("2.75"),
                true
        );
    }
}
