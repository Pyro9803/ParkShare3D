package com.parkshare.shared.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ApiResponseTest {

    @Test
    void successBuildsEnvelopeWithDataAndTimestamp() {
        ApiResponse<String> response = ApiResponse.success("ok");

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isEqualTo("ok");
        assertThat(response.error()).isNull();
        assertThat(response.timestamp()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void failureBuildsEnvelopeWithErrorAndTimestamp() {
        ApiError error = new ApiError("VALIDATION_FAILED", "Validation failed", Map.of("name", "required"));

        ApiResponse<Void> response = ApiResponse.failure(error);

        assertThat(response.success()).isFalse();
        assertThat(response.data()).isNull();
        assertThat(response.error()).isEqualTo(error);
        assertThat(response.timestamp()).isBeforeOrEqualTo(Instant.now());
    }
}
