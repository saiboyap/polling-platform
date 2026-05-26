package com.polling.platform.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "redis.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class RedisVoteCacheService {

    private final StringRedisTemplate stringRedisTemplate;
    private static final String VOTE_KEY_PREFIX = "poll:votes:";
    private static final Duration CACHE_TTL = Duration.ofDays(7);

    @CircuitBreaker(name = "redis", fallbackMethod = "initializePollCacheFallback")
    public void initializePollCache(String pollId) {
        String key = VOTE_KEY_PREFIX + pollId;
        if (Boolean.FALSE.equals(stringRedisTemplate.hasKey(key))) {
            stringRedisTemplate.opsForHash().put(key, "_init", "1");
            stringRedisTemplate.expire(key, CACHE_TTL);
            log.debug("Initialized vote cache for poll {}", pollId);
        }
    }

    private void initializePollCacheFallback(String pollId, Exception e) {
        log.warn("Redis circuit open — cache init skipped for poll {}: {}", pollId, e.getMessage());
    }

    @CircuitBreaker(name = "redis", fallbackMethod = "incrementVoteCountFallback")
    public void incrementVoteCount(String pollId, String optionId) {
        String key = VOTE_KEY_PREFIX + pollId;
        stringRedisTemplate.opsForHash().increment(key, optionId, 1);
        stringRedisTemplate.expire(key, CACHE_TTL);
        log.debug("Incremented vote count for poll={} option={}", pollId, optionId);
    }

    private void incrementVoteCountFallback(String pollId, String optionId, Exception e) {
        log.warn("Redis circuit open — vote count not updated for poll={} option={}: {}",
                pollId, optionId, e.getMessage());
    }

    @CircuitBreaker(name = "redis", fallbackMethod = "getVoteCountFallback")
    public long getVoteCount(String pollId, String optionId) {
        String key = VOTE_KEY_PREFIX + pollId;
        Object count = stringRedisTemplate.opsForHash().get(key, optionId);
        if (count == null) return 0L;
        return Long.parseLong(count.toString());
    }

    private long getVoteCountFallback(String pollId, String optionId, Exception e) {
        log.warn("Redis circuit open — returning 0 for poll={} option={}", pollId, optionId);
        return 0L;
    }

    @CircuitBreaker(name = "redis", fallbackMethod = "getAllVoteCountsFallback")
    public Map<String, Long> getAllVoteCounts(String pollId) {
        String key = VOTE_KEY_PREFIX + pollId;
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(key);
        Map<String, Long> result = new HashMap<>();
        for (Map.Entry<Object, Object> entry : entries.entrySet()) {
            String k = entry.getKey().toString();
            if (k.startsWith("_")) continue;
            try {
                result.put(k, Long.parseLong(entry.getValue().toString()));
            } catch (NumberFormatException ex) {
                result.put(k, 0L);
            }
        }
        return result;
    }

    private Map<String, Long> getAllVoteCountsFallback(String pollId, Exception e) {
        log.warn("Redis circuit open — returning empty counts for poll {}: {}", pollId, e.getMessage());
        return Map.of();
    }

    @CircuitBreaker(name = "redis", fallbackMethod = "seedFromDatabaseFallback")
    public void seedFromDatabase(String pollId, Map<String, Long> counts) {
        String key = VOTE_KEY_PREFIX + pollId;
        counts.forEach((optionId, count) ->
                stringRedisTemplate.opsForHash().put(key, optionId, String.valueOf(count)));
        stringRedisTemplate.expire(key, CACHE_TTL);
        log.info("Seeded vote cache for poll {} ({} options)", pollId, counts.size());
    }

    private void seedFromDatabaseFallback(String pollId, Map<String, Long> counts, Exception e) {
        log.warn("Redis circuit open — DB seed skipped for poll {}: {}", pollId, e.getMessage());
    }
}
