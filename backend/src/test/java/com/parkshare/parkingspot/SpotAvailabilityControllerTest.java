package com.parkshare.parkingspot;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import com.parkshare.parkingspot.dto.AvailabilityResponse;
import com.parkshare.parkingspot.dto.ReplaceAvailabilityRequest;
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
@WebMvcTest(SpotAvailabilityController.class)
class SpotAvailabilityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SpotAvailabilityService spotAvailabilityService;

    @Test
    void replaceAvailability_returns201WithSlots() throws Exception {
        UUID spotId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        List<AvailabilityResponse> responses = List.of(
                new AvailabilityResponse(UUID.randomUUID(), spotId, DayOfWeek.MON, LocalTime.of(8, 0), LocalTime.of(12, 0))
        );
        when(spotAvailabilityService.replaceAvailability(eq(spotId), eq(callerId), any(ReplaceAvailabilityRequest.class)))
                .thenReturn(responses);

        mockMvc.perform(post("/api/parking-spots/{spotId}/availability", spotId)
                        .principal(new UsernamePasswordAuthenticationToken(callerId, null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "slots": [
                                    {
                                      "dayOfWeek": "MON",
                                      "startTime": "08:00:00",
                                      "endTime": "12:00:00"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].dayOfWeek").value("MON"));
    }

    @Test
    void getAvailability_public_returns200() throws Exception {
        UUID spotId = UUID.randomUUID();
        when(spotAvailabilityService.getAvailability(spotId)).thenReturn(List.of());

        mockMvc.perform(get("/api/parking-spots/{spotId}/availability", spotId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void deleteAvailability_returns204() throws Exception {
        UUID spotId = UUID.randomUUID();
        UUID availabilityId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();

        mockMvc.perform(delete("/api/parking-spots/{spotId}/availability/{availabilityId}", spotId, availabilityId)
                        .principal(new UsernamePasswordAuthenticationToken(callerId, null)))
                .andExpect(status().isNoContent());
    }

    @Test
    void replaceAvailability_invalidTimeRange_returns400() throws Exception {
        UUID spotId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        when(spotAvailabilityService.replaceAvailability(eq(spotId), eq(callerId), any(ReplaceAvailabilityRequest.class)))
                .thenThrow(new BusinessException("AVAILABILITY_INVALID_TIME_RANGE", "Start time must be before end time"));

        mockMvc.perform(post("/api/parking-spots/{spotId}/availability", spotId)
                        .principal(new UsernamePasswordAuthenticationToken(callerId, null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "slots": [
                                    {
                                      "dayOfWeek": "MON",
                                      "startTime": "17:00:00",
                                      "endTime": "08:00:00"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AVAILABILITY_INVALID_TIME_RANGE"));
    }

    @Test
    void replaceAvailability_overlap_returns409() throws Exception {
        UUID spotId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        when(spotAvailabilityService.replaceAvailability(eq(spotId), eq(callerId), any(ReplaceAvailabilityRequest.class)))
                .thenThrow(new ConflictException("AVAILABILITY_OVERLAP", "Schedule slots overlap"));

        mockMvc.perform(post("/api/parking-spots/{spotId}/availability", spotId)
                        .principal(new UsernamePasswordAuthenticationToken(callerId, null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "slots": [
                                    {
                                      "dayOfWeek": "MON",
                                      "startTime": "08:00:00",
                                      "endTime": "12:00:00"
                                    },
                                    {
                                      "dayOfWeek": "MON",
                                      "startTime": "11:00:00",
                                      "endTime": "13:00:00"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AVAILABILITY_OVERLAP"));
    }

    @Test
    void replaceAvailability_emptySlots_returns400() throws Exception {
        mockMvc.perform(post("/api/parking-spots/{spotId}/availability", UUID.randomUUID())
                        .principal(new UsernamePasswordAuthenticationToken(UUID.randomUUID(), null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "slots": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }
}
