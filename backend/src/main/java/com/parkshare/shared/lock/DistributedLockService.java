package com.parkshare.shared.lock;

import java.time.Duration;
import java.util.Collections;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
public class DistributedLockService {

    private static final String KEY_PREFIX = "lock:";
    private final StringRedisTemplate redisTemplate;

    private static final String UNLOCK_LUA =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "return redis.call('del', KEYS[1]) " +
            "else return 0 end";

    public DistributedLockService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean tryLock(String lockKey, String token, Duration ttl) {
        return Boolean.TRUE.equals(
                redisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + lockKey, token, ttl));
    }

    public void unlock(String lockKey, String token) {
        redisTemplate.execute(
                new DefaultRedisScript<>(UNLOCK_LUA, Long.class),
                Collections.singletonList(KEY_PREFIX + lockKey),
                token
        );
    }
}
