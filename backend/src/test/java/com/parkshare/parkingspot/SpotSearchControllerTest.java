package com.parkshare.parkingspot;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import com.parkshare.parkinglot.ParkingLotService;
import com.parkshare.shared.api.PagedResponse;
import com.parkshare.shared.exception.BusinessException;
import com.parkshare.shared.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@WebMvcTest(ParkingSpotController.class)
class SpotSearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SpotSearchService spotSearchService;

    @MockBean
    private ParkingLotService parkingLotService;

    // Required because SpotAvailabilityController is in the same package and loaded in this WebMvcTest context
    @MockBean
    private SpotAvailabilityService spotAvailabilityService;

    @Test
    void search_validParams_returns200WithPagedResponse() throws Exception {
        LocalDateTime start = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0);
        LocalDateTime end = start.plusHours(2);
        when(spotSearchService.searchSpots(any(), any(), eq(VehicleType.CAR), any(), anyInt(), anyInt()))
                .thenReturn(PagedResponse.from(new PageImpl<>(List.of())));

        mockMvc.perform(get("/api/parking-spots/search")
                        .param("startTime", start.toString())
                        .param("endTime", end.toString())
                        .param("vehicleType", "CAR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    void search_missingStartTime_returns400() throws Exception {
        mockMvc.perform(get("/api/parking-spots/search")
                        .param("endTime", LocalDateTime.now().plusHours(2).toString())
                        .param("vehicleType", "CAR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MISSING_PARAMETER"));
    }

    @Test
    void search_invalidVehicleType_returns400() throws Exception {
        mockMvc.perform(get("/api/parking-spots/search")
                        .param("startTime", LocalDateTime.now().plusDays(1).toString())
                        .param("endTime", LocalDateTime.now().plusDays(1).plusHours(1).toString())
                        .param("vehicleType", "INVALID"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_PARAMETER"));
    }

    @Test
    void search_startTimeInPast_returns400() throws Exception {
        LocalDateTime start = LocalDateTime.now().minusHours(1);
        LocalDateTime end = start.plusHours(2);
        when(spotSearchService.searchSpots(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenThrow(new BusinessException("INVALID_START_TIME", "Start time must be in the future"));

        mockMvc.perform(get("/api/parking-spots/search")
                        .param("startTime", start.toString())
                        .param("endTime", end.toString())
                        .param("vehicleType", "CAR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_START_TIME"));
    }

    @Test
    void search_durationTooShort_returns400() throws Exception {
        when(spotSearchService.searchSpots(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenThrow(new BusinessException("DURATION_TOO_SHORT", "Minimum booking duration is 30 minutes"));

        mockMvc.perform(get("/api/parking-spots/search")
                        .param("startTime", LocalDateTime.now().plusDays(1).toString())
                        .param("endTime", LocalDateTime.now().plusDays(1).plusMinutes(20).toString())
                        .param("vehicleType", "CAR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("DURATION_TOO_SHORT"));
    }
}
