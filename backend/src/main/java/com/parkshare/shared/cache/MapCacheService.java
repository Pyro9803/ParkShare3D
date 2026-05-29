package com.parkshare.shared.cache;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.parkshare.parkinglot.dto.ParkingLotMapResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class MapCacheService {

    private static final Logger log = LoggerFactory.getLogger(MapCacheService.class);
    private static final String KEY_PREFIX = "map:lot:";
    private static final Duration TTL = Duration.ofSeconds(30);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public MapCacheService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public Optional<ParkingLotMapResponse> get(UUID lotId) {
        try {
            String json = redisTemplate.opsForValue().get(KEY_PREFIX + lotId);
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, ParkingLotMapResponse.class));
        } catch (Exception e) {
            log.warn("Map cache get failed for lot {}", lotId, e);
            return Optional.empty();
        }
    }

    public void store(UUID lotId, ParkingLotMapResponse response) {
        try {
            String json = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(KEY_PREFIX + lotId, json, TTL);
        } catch (Exception e) {
            log.warn("Map cache store failed for lot {}", lotId, e);
        }
    }

    public void evict(UUID lotId) {
        try {
            redisTemplate.delete(KEY_PREFIX + lotId);
        } catch (Exception e) {
            log.warn("Map cache evict failed for lot {}", lotId, e);
        }
    }
}
