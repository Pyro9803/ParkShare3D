package com.parkshare.shared.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, GlobalExceptionHandlerTest.TestController.class})
@WebMvcTest(GlobalExceptionHandlerTest.TestController.class)
public class GlobalExceptionHandlerTest {

    private final MockMvc mockMvc;

    @Autowired
    GlobalExceptionHandlerTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void validationErrorReturnsFieldDetails() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.message").value("Validation failed"))
                .andExpect(jsonPath("$.error.details.name").value("name is required"))
                .andExpect(jsonPath("$.error.stackTrace").doesNotExist());
    }

    @Test
    void accessDeniedReturnsForbiddenEnvelope() throws Exception {
        mockMvc.perform(get("/test/access-denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.error.message").value("Access denied"))
                .andExpect(jsonPath("$.error.stackTrace").doesNotExist());
    }

    @Test
    void entityNotFoundReturnsNotFoundEnvelope() throws Exception {
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("User not found"))
                .andExpect(jsonPath("$.error.stackTrace").doesNotExist());
    }

    @Test
    void businessExceptionReturnsBadRequestEnvelope() throws Exception {
        mockMvc.perform(get("/test/business"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_STATE"))
                .andExpect(jsonPath("$.error.message").value("Invalid state"))
                .andExpect(jsonPath("$.error.stackTrace").doesNotExist());
    }

    @Test
    void conflictExceptionReturnsConflictEnvelope() throws Exception {
        mockMvc.perform(get("/test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("EMAIL_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.error.message").value("Email already exists"))
                .andExpect(jsonPath("$.error.stackTrace").doesNotExist());
    }

    @Test
    void rateLimitExceptionReturnsTooManyRequestsEnvelope() throws Exception {
        mockMvc.perform(get("/test/rate-limit"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("RATE_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.error.message").value("Too many requests"))
                .andExpect(jsonPath("$.error.stackTrace").doesNotExist());
    }

    @Test
    void checkInWindowExceptionReturnsBadRequestEnvelope() throws Exception {
        mockMvc.perform(get("/test/check-in-window"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("CHECKIN_WINDOW_EXPIRED"))
                .andExpect(jsonPath("$.error.message").value("Check-in window expired"))
                .andExpect(jsonPath("$.error.stackTrace").doesNotExist());
    }

    @Test
    void genericExceptionReturnsInternalErrorWithoutStackTrace() throws Exception {
        mockMvc.perform(get("/test/generic"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.error.message").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.error.stackTrace").doesNotExist());
    }

    @Test
    void parkingSpotUniqueConstraintViolationReturnsConflictEnvelope() throws Exception {
        mockMvc.perform(get("/test/spot-code-constraint"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("SPOT_CODE_DUPLICATE"))
                .andExpect(jsonPath("$.error.message").value("Spot code already exists for this parking lot"))
                .andExpect(jsonPath("$.error.stackTrace").doesNotExist());
    }

    @Test
    void otherDataIntegrityViolationReturnsInternalErrorWithoutStackTrace() throws Exception {
        mockMvc.perform(get("/test/other-data-integrity"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.error.message").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.error.stackTrace").doesNotExist());
    }

    @RestController
    @RequestMapping("/test")
    public static class TestController {

        @PostMapping("/validation")
        void validation(@Valid @RequestBody ValidationRequest request) {
        }

        @GetMapping("/access-denied")
        void accessDenied() {
            throw new AccessDeniedException("Denied by test");
        }

        @GetMapping("/not-found")
        void notFound() {
            throw new EntityNotFoundException("USER_NOT_FOUND", "User not found");
        }

        @GetMapping("/business")
        void business() {
            throw new BusinessException("INVALID_STATE", "Invalid state");
        }

        @GetMapping("/conflict")
        void conflict() {
            throw new ConflictException("EMAIL_ALREADY_EXISTS", "Email already exists");
        }

        @GetMapping("/rate-limit")
        void rateLimit() {
            throw new RateLimitExceededException("Too many requests");
        }

        @GetMapping("/check-in-window")
        void checkInWindow() {
            throw new CheckInWindowException("Check-in window expired");
        }

        @GetMapping("/generic")
        void generic() {
            throw new IllegalStateException("Sensitive implementation detail");
        }

        @GetMapping("/spot-code-constraint")
        void spotCodeConstraint() {
            throw new DataIntegrityViolationException("violates constraint uq_parking_spots_active_code");
        }

        @GetMapping("/other-data-integrity")
        void otherDataIntegrity() {
            throw new DataIntegrityViolationException("violates constraint some_other_constraint");
        }
    }

    record ValidationRequest(@NotBlank(message = "name is required") String name) {
    }
}
