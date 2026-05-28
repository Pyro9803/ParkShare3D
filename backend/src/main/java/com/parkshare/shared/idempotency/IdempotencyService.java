package com.parkshare.shared.idempotency;

import java.time.Duration;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);
    private static final String KEY_PREFIX = "idempotency:";
    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public IdempotencyService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public <T> Optional<T> get(String key, Class<T> responseType) {
        try {
            String json = redisTemplate.opsForValue().get(KEY_PREFIX + key);
            if (json == null) return Optional.empty();
            return Optional.of(objectMapper.readValue(json, responseType));
        } catch (Exception e) {
            log.warn("Idempotency cache read failed for key {}, proceeding without cache: {}", key, e.getMessage());
            return Optional.empty();
        }
    }

    public void store(String key, Object response) {
        try {
            redisTemplate.opsForValue().set(
                    KEY_PREFIX + key,
                    objectMapper.writeValueAsString(response),
                    TTL
            );
        } catch (Exception e) {
            log.warn("Idempotency cache write failed for key {}: {}", key, e.getMessage());
        }
    }
}
