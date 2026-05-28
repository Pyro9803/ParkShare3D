package com.parkshare.vehicle;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import com.parkshare.parkingspot.VehicleType;
import com.parkshare.shared.exception.ConflictException;
import com.parkshare.shared.exception.EntityNotFoundException;
import com.parkshare.shared.exception.ForbiddenException;
import com.parkshare.shared.exception.GlobalExceptionHandler;
import com.parkshare.vehicle.dto.CreateVehicleRequest;
import com.parkshare.vehicle.dto.UpdateVehicleRequest;
import com.parkshare.vehicle.dto.VehicleResponse;
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
@WebMvcTest(VehicleController.class)
class VehicleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private VehicleService vehicleService;

    @Test
    void createVehicle_returns201() throws Exception {
        UUID userId = UUID.randomUUID();
        VehicleResponse response = new VehicleResponse(UUID.randomUUID(), "51A-12345", VehicleType.CAR, "Honda", "Civic", "Black");
        when(vehicleService.createVehicle(eq(userId), any(CreateVehicleRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/vehicles")
                        .principal(new UsernamePasswordAuthenticationToken(userId, null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "licensePlate": "51A-12345",
                                  "vehicleType": "CAR",
                                  "brand": "Honda",
                                  "model": "Civic",
                                  "color": "Black"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.licensePlate").value("51A-12345"));
    }

    @Test
    void getMyVehicles_returns200() throws Exception {
        UUID userId = UUID.randomUUID();
        when(vehicleService.getMyVehicles(userId)).thenReturn(List.of());

        mockMvc.perform(get("/api/vehicles/my")
                        .principal(new UsernamePasswordAuthenticationToken(userId, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void updateVehicle_returns200() throws Exception {
        UUID vehicleId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        VehicleResponse response = new VehicleResponse(vehicleId, "51A-22222", VehicleType.CAR, "Honda", "Civic", "White");
        when(vehicleService.updateVehicle(eq(vehicleId), eq(userId), any(UpdateVehicleRequest.class))).thenReturn(response);

        mockMvc.perform(put("/api/vehicles/{id}", vehicleId)
                        .principal(new UsernamePasswordAuthenticationToken(userId, null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "licensePlate": "51A-22222",
                                  "vehicleType": "CAR",
                                  "brand": "Honda",
                                  "model": "Civic",
                                  "color": "White"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.licensePlate").value("51A-22222"));
    }

    @Test
    void updateVehicle_forbidden_returns403() throws Exception {
        UUID vehicleId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(vehicleService.updateVehicle(eq(vehicleId), eq(userId), any(UpdateVehicleRequest.class)))
                .thenThrow(new ForbiddenException("NOT_VEHICLE_OWNER", "Not your vehicle"));

        mockMvc.perform(put("/api/vehicles/{id}", vehicleId)
                        .principal(new UsernamePasswordAuthenticationToken(userId, null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "licensePlate": "51A-22222",
                                  "vehicleType": "CAR"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteVehicle_returns204() throws Exception {
        UUID vehicleId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        mockMvc.perform(delete("/api/vehicles/{id}", vehicleId)
                        .principal(new UsernamePasswordAuthenticationToken(userId, null)))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteVehicle_notFound_returns404() throws Exception {
        UUID vehicleId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        doThrow(new EntityNotFoundException("VEHICLE_NOT_FOUND", "Vehicle not found"))
                .when(vehicleService).deleteVehicle(vehicleId, userId);

        mockMvc.perform(delete("/api/vehicles/{id}", vehicleId)
                        .principal(new UsernamePasswordAuthenticationToken(userId, null)))
                .andExpect(status().isNotFound());
    }

    @Test
    void createVehicle_invalidLicensePlate_returns400() throws Exception {
        mockMvc.perform(post("/api/vehicles")
                        .principal(new UsernamePasswordAuthenticationToken(UUID.randomUUID(), null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "licensePlate": "INVALID",
                                  "vehicleType": "CAR"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void createVehicle_duplicatePlate_returns409() throws Exception {
        UUID userId = UUID.randomUUID();
        when(vehicleService.createVehicle(eq(userId), any(CreateVehicleRequest.class)))
                .thenThrow(new ConflictException("LICENSE_PLATE_DUPLICATE", "This license plate is already registered"));

        mockMvc.perform(post("/api/vehicles")
                        .principal(new UsernamePasswordAuthenticationToken(userId, null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "licensePlate": "51A-12345",
                                  "vehicleType": "CAR"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("LICENSE_PLATE_DUPLICATE"));
    }
}
