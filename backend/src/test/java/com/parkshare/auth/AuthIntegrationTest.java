package com.parkshare.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.parkshare.auth.jwt.JwtProperties;
import com.parkshare.auth.jwt.JwtProvider;
import com.parkshare.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AuthIntegrationTest {

    private static final String JWT_SECRET = "this-is-a-256-bit-dev-secret-key-do-not-use-in-prod!!";

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Autowired
    AuthIntegrationTest(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("app.jwt.secret", () -> JWT_SECRET);
        registry.add("app.jwt.access-token-ttl-seconds", () -> "900");
        registry.add("app.jwt.refresh-token-ttl-seconds", () -> "604800");
    }

    @Test
    void registerLoginMeRefreshLogoutFlow() throws Exception {
        String email = "driver-" + UUID.randomUUID() + "@example.com";

        JsonNode register = post("/api/auth/register", Map.of(
                "email", email,
                "password", "password123",
                "fullName", "Driver User",
                "phone", "555-0100",
                "role", "DRIVER"
        ), HttpStatus.CREATED);
        String userId = register.at("/data/userId").asText();
        assertThat(register.at("/data/email").asText()).isEqualTo(email);
        assertThat(register.at("/data/role").asText()).isEqualTo("DRIVER");

        JsonNode duplicateRegister = post("/api/auth/register", Map.of(
                "email", email,
                "password", "password123",
                "fullName", "Driver User",
                "role", "DRIVER"
        ), HttpStatus.CONFLICT);
        assertThat(duplicateRegister.at("/error/code").asText()).isEqualTo("EMAIL_ALREADY_EXISTS");

        JsonNode login = post("/api/auth/login", Map.of(
                "email", email,
                "password", "password123"
        ), HttpStatus.OK);
        String accessToken = login.at("/data/accessToken").asText();
        String refreshToken = login.at("/data/refreshToken").asText();
        assertThat(accessToken).isNotBlank();
        assertThat(refreshToken).isNotBlank();
        assertThat(login.at("/data/expiresIn").asInt()).isEqualTo(900);

        JsonNode me = get("/api/auth/me", accessToken, HttpStatus.OK);
        assertThat(me.at("/data/userId").asText()).isEqualTo(userId);
        assertThat(me.at("/data/email").asText()).isEqualTo(email);

        JsonNode refresh = post("/api/auth/refresh", Map.of("refreshToken", refreshToken), HttpStatus.OK);
        String newAccessToken = refresh.at("/data/accessToken").asText();
        assertThat(newAccessToken).isNotBlank();
        assertThat(newAccessToken).isNotEqualTo(accessToken);

        JsonNode meWithNewToken = get("/api/auth/me", newAccessToken, HttpStatus.OK);
        assertThat(meWithNewToken.at("/data/email").asText()).isEqualTo(email);

        JsonNode logout = post("/api/auth/logout", newAccessToken, Map.of("refreshToken", refreshToken), HttpStatus.OK);
        assertThat(logout.at("/success").asBoolean()).isTrue();

        JsonNode refreshAfterLogout = post("/api/auth/refresh", Map.of("refreshToken", refreshToken), HttpStatus.UNAUTHORIZED);
        assertThat(refreshAfterLogout.at("/error/code").asText()).isEqualTo("INVALID_REFRESH_TOKEN");
    }

    @Test
    void expiredAccessTokenReturnsTokenExpiredEnvelope() throws Exception {
        JwtProvider expiredTokenProvider = new JwtProvider(new JwtProperties(JWT_SECRET, -10, 604800));
        String expiredToken = expiredTokenProvider.generateAccessToken(
                UUID.randomUUID(),
                "driver@example.com",
                UserRole.DRIVER
        );

        JsonNode response = get("/api/auth/me", expiredToken, HttpStatus.UNAUTHORIZED);

        assertThat(response.at("/success").asBoolean()).isFalse();
        assertThat(response.at("/error/code").asText()).isEqualTo("TOKEN_EXPIRED");
    }

    private JsonNode post(String path, Object body, HttpStatus expectedStatus) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url(path)))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();
        return send(request, expectedStatus);
    }

    private JsonNode post(String path, String bearerToken, Object body, HttpStatus expectedStatus) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url(path)))
                .header("Authorization", "Bearer " + bearerToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();
        return send(request, expectedStatus);
    }

    private JsonNode get(String path, String bearerToken, HttpStatus expectedStatus) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url(path)))
                .header("Authorization", "Bearer " + bearerToken)
                .GET()
                .build();
        return send(request, expectedStatus);
    }

    private JsonNode send(HttpRequest request, HttpStatus expectedStatus) throws Exception {
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode())
                .as(response.body())
                .isEqualTo(expectedStatus.value());
        return objectMapper.readTree(response.body());
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
