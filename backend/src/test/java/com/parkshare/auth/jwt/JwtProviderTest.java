package com.parkshare.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import com.parkshare.user.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

class JwtProviderTest {

    private static final String SECRET = "this-is-a-256-bit-test-secret-key-123456";

    @Test
    void generatedAccessTokenCanBeValidatedAndExtracted() {
        JwtProvider provider = new JwtProvider(new JwtProperties(SECRET, 900, 604800));
        UUID userId = UUID.randomUUID();

        String token = provider.generateAccessToken(userId, "driver@example.com", UserRole.DRIVER);

        Claims claims = provider.validateAndExtractClaims(token);
        assertThat(claims.getSubject()).isEqualTo(userId.toString());
        assertThat(claims.get("email", String.class)).isEqualTo("driver@example.com");
        assertThat(claims.get("role", String.class)).isEqualTo(UserRole.DRIVER.name());
    }

    @Test
    void expiredAccessTokenThrowsExpiredJwtException() {
        JwtProvider provider = new JwtProvider(new JwtProperties(SECRET, -10, 604800));
        String token = provider.generateAccessToken(UUID.randomUUID(), "driver@example.com", UserRole.DRIVER);

        assertThatThrownBy(() -> provider.validateAndExtractClaims(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void tamperedTokenThrowsJwtException() {
        JwtProvider provider = new JwtProvider(new JwtProperties(SECRET, 900, 604800));
        String token = provider.generateAccessToken(UUID.randomUUID(), "driver@example.com", UserRole.DRIVER);
        String[] parts = token.split("\\.");
        String payload = parts[1];
        char replacement = payload.charAt(0) == 'a' ? 'b' : 'a';
        parts[1] = replacement + payload.substring(1);
        String tamperedToken = String.join(".", parts);

        assertThatThrownBy(() -> provider.validateAndExtractClaims(tamperedToken))
                .isInstanceOf(JwtException.class);
    }
}
