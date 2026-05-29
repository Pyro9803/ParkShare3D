package com.parkshare.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.parkshare.admin.dto.AdminUserResponse;
import com.parkshare.admin.dto.StatisticsResponse;
import com.parkshare.shared.api.PagedResponse;
import com.parkshare.shared.exception.GlobalExceptionHandler;
import com.parkshare.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@WebMvcTest({AdminUserController.class, AdminReservationController.class, AdminStatisticsController.class})
class AdminControllersTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminService adminService;

    @MockBean
    private com.parkshare.auth.jwt.JwtProvider jwtProvider;

    @Test
    void getUsers_asAdmin_returns200() throws Exception {
        when(adminService.getUsers(any(), any(), anyInt(), anyInt())).thenReturn(new PagedResponse<>(List.of(), 0, 0, 0, 20));

        mockMvc.perform(get("/api/admin/users")
                        .principal(authentication(UUID.randomUUID())))
                .andExpect(status().isOk());
    }

    @Test
    void deactivateUser_asAdmin_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(adminService.deactivateUser(id)).thenReturn(new AdminUserResponse(id, "e", "f", "p", UserRole.DRIVER, false, null));

        mockMvc.perform(put("/api/admin/users/" + id + "/deactivate")
                        .principal(authentication(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));
    }

    @Test
    void getStatistics_asAdmin_returns200() throws Exception {
        when(adminService.getStatistics()).thenReturn(new StatisticsResponse(1, 2, BigDecimal.TEN, 3));

        mockMvc.perform(get("/api/admin/statistics")
                        .principal(authentication(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalUsers").value(1));
    }

    private static UsernamePasswordAuthenticationToken authentication(UUID userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }
}
