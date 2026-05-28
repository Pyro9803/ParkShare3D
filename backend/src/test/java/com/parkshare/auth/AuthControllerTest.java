package com.parkshare.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import com.parkshare.auth.dto.LoginRequest;
import com.parkshare.auth.dto.LoginResponse;
import com.parkshare.auth.dto.MeResponse;
import com.parkshare.auth.dto.RefreshRequest;
import com.parkshare.auth.dto.RefreshResponse;
import com.parkshare.auth.dto.RegisterRequest;
import com.parkshare.auth.dto.RegisterResponse;
import com.parkshare.shared.exception.BusinessException;
import com.parkshare.shared.exception.ConflictException;
import com.parkshare.shared.exception.GlobalExceptionHandler;
import com.parkshare.shared.exception.InvalidCredentialsException;
import com.parkshare.shared.exception.InvalidRefreshTokenException;
import com.parkshare.user.UserRole;
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
@WebMvcTest(AuthController.class)
class AuthControllerTest {

    private final MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @Autowired
    AuthControllerTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void registerReturnsCreatedUserEnvelope() throws Exception {
        UUID userId = UUID.randomUUID();
        when(authService.register(any(RegisterRequest.class)))
                .thenReturn(new RegisterResponse(userId, "driver@example.com", UserRole.DRIVER));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "driver@example.com",
                                  "password": "password123",
                                  "fullName": "Driver User",
                                  "phone": "555-0100",
                                  "role": "DRIVER"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(userId.toString()))
                .andExpect(jsonPath("$.data.email").value("driver@example.com"))
                .andExpect(jsonPath("$.data.role").value("DRIVER"))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void registerRejectsAdminRole() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new BusinessException("INVALID_REGISTRATION_ROLE", "Admin users cannot self-register"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "admin@example.com",
                                  "password": "password123",
                                  "fullName": "Admin User",
                                  "role": "ADMIN"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_REGISTRATION_ROLE"));
    }

    @Test
    void registerDuplicateEmailReturnsConflictEnvelope() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new ConflictException("EMAIL_ALREADY_EXISTS", "Email already exists"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "driver@example.com",
                                  "password": "password123",
                                  "fullName": "Driver User",
                                  "role": "DRIVER"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void registerValidationErrorReturnsBadRequestEnvelope() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "not-an-email",
                                  "password": "short",
                                  "fullName": "",
                                  "role": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.details.email").exists())
                .andExpect(jsonPath("$.error.details.password").exists())
                .andExpect(jsonPath("$.error.details.fullName").exists())
                .andExpect(jsonPath("$.error.details.role").exists());
    }

    @Test
    void registerRejectsPasswordLongerThanBcryptLimit() throws Exception {
        String longPassword = "a".repeat(73);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "driver@example.com",
                                  "password": "%s",
                                  "fullName": "Driver User",
                                  "role": "DRIVER"
                                }
                                """.formatted(longPassword)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.details.password").exists());
    }

    @Test
    void loginReturnsTokenEnvelope() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenReturn(new LoginResponse("access-token", "refresh-token", 900));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "driver@example.com",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.data.expiresIn").value(900));
    }

    @Test
    void loginInvalidCredentialsReturnsUnauthorizedEnvelope() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "driver@example.com",
                                  "password": "wrong-password"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void refreshReturnsNewAccessTokenEnvelope() throws Exception {
        when(authService.refresh(any(RefreshRequest.class)))
                .thenReturn(new RefreshResponse("new-access-token", 900));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "refresh-token"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.data.expiresIn").value(900));
    }

    @Test
    void refreshInvalidTokenReturnsUnauthorizedEnvelope() throws Exception {
        when(authService.refresh(any(RefreshRequest.class))).thenThrow(new InvalidRefreshTokenException());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "invalid-token"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void meReturnsAuthenticatedUserEnvelope() throws Exception {
        UUID userId = UUID.randomUUID();
        when(authService.me(userId)).thenReturn(new MeResponse(
                userId,
                "driver@example.com",
                "Driver User",
                "555-0100",
                UserRole.DRIVER,
                true
        ));

        mockMvc.perform(get("/api/auth/me")
                        .principal(new UsernamePasswordAuthenticationToken(userId, null, List.of())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(userId.toString()))
                .andExpect(jsonPath("$.data.email").value("driver@example.com"))
                .andExpect(jsonPath("$.data.role").value("DRIVER"))
                .andExpect(jsonPath("$.data.active").value(true));
    }

    @Test
    void logoutRevokesRefreshTokenAndReturnsSuccessEnvelope() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(post("/api/auth/logout")
                        .principal(new UsernamePasswordAuthenticationToken(userId, null, List.of()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "refresh-token"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.error").doesNotExist());
        verify(authService).logout(any(UUID.class), any(RefreshRequest.class));
    }
}
