package com.polling.platform.service;

import com.polling.platform.dto.response.TrendingPollResponse;
import com.polling.platform.entity.Poll;
import com.polling.platform.repository.PollRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TrendingPollService {

    private final StringRedisTemplate redisTemplate;
    private final PollRepository      pollRepository;

    private static final String TRENDING_KEY = "trending:polls";

    @CircuitBreaker(name = "redis", fallbackMethod = "recordVoteFallback")
    public void recordVote(String pollId, long delta) {
        redisTemplate.opsForZSet().incrementScore(TRENDING_KEY, pollId, delta);
    }

    private void recordVoteFallback(String pollId, long delta, Exception e) {
        log.warn("Redis circuit open — trending not updated for poll {}", pollId);
    }

    @CircuitBreaker(name = "redis", fallbackMethod = "removePollFallback")
    public void removePoll(String pollId) {
        redisTemplate.opsForZSet().remove(TRENDING_KEY, (Object) pollId);
    }

    private void removePollFallback(String pollId, Exception e) {
        log.warn("Redis circuit open — poll {} not removed from trending", pollId);
    }

    @CircuitBreaker(name = "redis", fallbackMethod = "getTopPollsFallback")
    @Transactional(readOnly = true)
    public List<TrendingPollResponse> getTopPolls(int limit) {
        Set<ZSetOperations.TypedTuple<String>> entries =
                redisTemplate.opsForZSet().reverseRangeWithScores(TRENDING_KEY, 0, limit - 1);

        if (entries == null || entries.isEmpty()) return List.of();

        List<UUID> pollUuids = entries.stream()
                .map(ZSetOperations.TypedTuple::getValue)
                .filter(Objects::nonNull)
                .map(UUID::fromString)
                .collect(Collectors.toList());

        Map<String, Poll> pollMap = pollRepository.findAllByIdWithCreatedBy(pollUuids)
                .stream()
                .collect(Collectors.toMap(p -> p.getId().toString(), p -> p));

        List<TrendingPollResponse> result = new ArrayList<>();
        int rank = 0;
        for (ZSetOperations.TypedTuple<String> entry : entries) {
            String pollId = entry.getValue();
            Poll poll = pollMap.get(pollId);
            if (poll == null) continue;

            result.add(TrendingPollResponse.builder()
                    .pollId(pollId)
                    .question(poll.getQuestion())
                    .createdBy(poll.getCreatedBy().getUsername())
                    .status(poll.getStatus().name())
                    .pollType(poll.getPollType().name())
                    .totalVotes(entry.getScore() == null ? 0L : entry.getScore().longValue())
                    .rank(++rank)
                    .build());
        }
        return result;
    }

    private List<TrendingPollResponse> getTopPollsFallback(int limit, Exception e) {
        log.warn("Redis circuit open — returning empty trending list: {}", e.getMessage());
        return List.of();
    }
}
