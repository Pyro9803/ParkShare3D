package com.parkshare.auth.token;

import java.time.Duration;
import java.util.UUID;

import com.parkshare.auth.jwt.JwtProperties;
import com.parkshare.shared.exception.InvalidRefreshTokenException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RefreshTokenService {

    private static final String KEY_PREFIX = "refresh_token:";

    private final StringRedisTemplate redisTemplate;
    private final JwtProperties properties;

    public RefreshTokenService(StringRedisTemplate redisTemplate, JwtProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    public String issue(UUID userId) {
        UUID tokenId = UUID.randomUUID();
        store(userId, tokenId);
        return userId + ":" + tokenId;
    }

    public RefreshToken validate(String rawToken) {
        RefreshToken token = parse(rawToken);
        Boolean exists = redisTemplate.hasKey(key(token.userId(), token.tokenId()));
        if (!Boolean.TRUE.equals(exists)) {
            throw new InvalidRefreshTokenException();
        }
        return token;
    }

    public UUID extractTokenId(String rawToken) {
        return parse(rawToken).tokenId();
    }

    public void rotate(UUID userId, UUID oldTokenId, UUID newTokenId) {
        redisTemplate.delete(key(userId, oldTokenId));
        store(userId, newTokenId);
    }

    public void revoke(UUID userId, UUID tokenId) {
        redisTemplate.delete(key(userId, tokenId));
    }

    private void store(UUID userId, UUID tokenId) {
        redisTemplate.opsForValue().set(
                key(userId, tokenId),
                "1",
                Duration.ofSeconds(properties.refreshTokenTtlSeconds())
        );
    }

    public RefreshToken parse(String rawToken) {
        if (rawToken == null) {
            throw new InvalidRefreshTokenException();
        }

        int separator = rawToken.indexOf(':');
        if (separator <= 0 || separator == rawToken.length() - 1) {
            throw new InvalidRefreshTokenException();
        }

        try {
            UUID userId = UUID.fromString(rawToken.substring(0, separator));
            UUID tokenId = UUID.fromString(rawToken.substring(separator + 1));
            return new RefreshToken(userId, tokenId);
        } catch (IllegalArgumentException exception) {
            throw new InvalidRefreshTokenException();
        }
    }

    private String key(UUID userId, UUID tokenId) {
        return KEY_PREFIX + userId + ":" + tokenId;
    }

}
