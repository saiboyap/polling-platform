package com.polling.platform.service;

import com.polling.platform.annotation.AuditLogged;
import com.polling.platform.dto.event.VoteSubmittedEvent;
import com.polling.platform.dto.request.CastVoteRequest;
import com.polling.platform.dto.response.OptionResultResponse;
import com.polling.platform.dto.response.PollResultsResponse;
import com.polling.platform.entity.*;
import com.polling.platform.exception.DuplicateVoteException;
import com.polling.platform.exception.PollClosedException;
import com.polling.platform.exception.ResourceNotFoundException;
import com.polling.platform.kafka.producer.PollEventProducer;
import com.polling.platform.repository.*;
import com.polling.platform.websocket.WebSocketEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class VoteService {

    private final VoteRepository voteRepository;
    private final PollRepository pollRepository;
    private final PollOptionRepository pollOptionRepository;
    private final FreeTextVoteRepository freeTextVoteRepository;
    private final UserRepository userRepository;
    private final WebSocketEventPublisher webSocketEventPublisher;

    @Autowired(required = false)
    @Nullable
    private RedisVoteCacheService cacheService;

    @Autowired(required = false)
    @Nullable
    private TrendingPollService trendingPollService;

    @Autowired(required = false)
    @Nullable
    private PollEventProducer pollEventProducer;

    // ---------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------

    @AuditLogged(event = "VOTE_CAST", entityType = "POLL")
    @Transactional
    public PollResultsResponse castVote(UUID pollId, CastVoteRequest request, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));

        Poll poll = pollRepository.findByIdWithDetails(pollId)
                .orElseThrow(() -> new ResourceNotFoundException("Poll", "id", pollId.toString()));

        validatePollActive(poll);

        return switch (poll.getPollType()) {
            case SINGLE_CHOICE -> castSingleChoice(poll, user, request);
            case MULTI_CHOICE  -> castMultiChoice(poll, user, request);
            case FREE_TEXT     -> castFreeText(poll, user, request);
        };
    }

    @Transactional(readOnly = true)
    public PollResultsResponse getResults(UUID pollId) {
        Poll poll = pollRepository.findByIdWithDetails(pollId)
                .orElseThrow(() -> new ResourceNotFoundException("Poll", "id", pollId.toString()));

        if (poll.getPollType() == PollType.FREE_TEXT) {
            return buildFreeTextResults(poll);
        }

        Map<String, Long> counts = resolvedCounts(poll);
        return buildOptionResults(poll, counts);
    }

    // ---------------------------------------------------------------
    // Single-choice
    // ---------------------------------------------------------------

    private PollResultsResponse castSingleChoice(Poll poll, User user, CastVoteRequest request) {
        if (voteRepository.existsByPoll_IdAndUser_Id(poll.getId(), user.getId())) {
            throw new DuplicateVoteException("You have already voted on this poll");
        }

        List<UUID> optionIds = validateOptionIds(request, 1, 1);
        UUID optionId = optionIds.get(0);
        PollOption option = resolveOption(poll, optionId);

        voteRepository.save(Vote.builder().poll(poll).user(user).option(option).build());
        if (cacheService != null) cacheService.incrementVoteCount(poll.getId().toString(), optionId.toString());
        if (trendingPollService != null) trendingPollService.recordVote(poll.getId().toString(), 1);

        Map<String, Long> counts = resolvedCounts(poll);
        publishVoteEvents(poll, user.getUsername(), optionId.toString(), counts);

        log.info("Single-choice vote: poll={} option={} user={}", poll.getId(), optionId, user.getUsername());
        return buildOptionResults(poll, counts);
    }

    // ---------------------------------------------------------------
    // Multi-choice
    // ---------------------------------------------------------------

    private PollResultsResponse castMultiChoice(Poll poll, User user, CastVoteRequest request) {
        if (voteRepository.existsByPoll_IdAndUser_Id(poll.getId(), user.getId())) {
            throw new DuplicateVoteException("You have already voted on this poll");
        }

        List<UUID> optionIds = validateOptionIds(request, 1, poll.getMaxChoices());

        for (UUID optionId : optionIds) {
            PollOption option = resolveOption(poll, optionId);
            voteRepository.save(Vote.builder().poll(poll).user(user).option(option).build());
            if (cacheService != null) cacheService.incrementVoteCount(poll.getId().toString(), optionId.toString());
        }
        if (trendingPollService != null) trendingPollService.recordVote(poll.getId().toString(), 1);

        Map<String, Long> counts = resolvedCounts(poll);
        String ref = optionIds.stream().map(UUID::toString).collect(Collectors.joining(","));
        publishVoteEvents(poll, user.getUsername(), ref, counts);

        log.info("Multi-choice vote: poll={} options={} user={}", poll.getId(), ref, user.getUsername());
        return buildOptionResults(poll, counts);
    }

    // ---------------------------------------------------------------
    // Free-text
    // ---------------------------------------------------------------

    private PollResultsResponse castFreeText(Poll poll, User user, CastVoteRequest request) {
        if (freeTextVoteRepository.existsByPoll_IdAndUser_Id(poll.getId(), user.getId())) {
            throw new DuplicateVoteException("You have already submitted a response");
        }
        if (request.getFreeText() == null || request.getFreeText().isBlank()) {
            throw new IllegalArgumentException("Response text is required for free-text polls");
        }

        FreeTextVote ftv = FreeTextVote.builder()
                .poll(poll)
                .user(user)
                .responseText(request.getFreeText().trim())
                .build();
        freeTextVoteRepository.save(ftv);
        if (trendingPollService != null) trendingPollService.recordVote(poll.getId().toString(), 1);

        long total = freeTextVoteRepository.countByPoll_Id(poll.getId());
        webSocketEventPublisher.publishFreeTextUpdate(poll.getId().toString(), total);

        log.info("Free-text vote: poll={} user={}", poll.getId(), user.getUsername());
        return buildFreeTextResults(poll);
    }

    // ---------------------------------------------------------------
    // Result builders
    // ---------------------------------------------------------------

    private PollResultsResponse buildOptionResults(Poll poll, Map<String, Long> counts) {
        long total = counts.values().stream().mapToLong(Long::longValue).sum();

        List<OptionResultResponse> results = poll.getOptions().stream()
                .map(opt -> {
                    long count = counts.getOrDefault(opt.getId().toString(), 0L);
                    double pct = total > 0 ? Math.round((count * 1000.0 / total)) / 10.0 : 0.0;
                    return OptionResultResponse.builder()
                            .optionId(opt.getId())
                            .optionText(opt.getOptionText())
                            .voteCount(count)
                            .percentage(pct)
                            .build();
                })
                .sorted(Comparator.comparingLong(OptionResultResponse::getVoteCount).reversed())
                .collect(Collectors.toList());

        return PollResultsResponse.builder()
                .pollId(poll.getId())
                .question(poll.getQuestion())
                .pollType(poll.getPollType().name())
                .status(poll.getStatus().name())
                .maxChoices(poll.getMaxChoices())
                .totalResponses(total)
                .optionResults(results)
                .build();
    }

    private PollResultsResponse buildFreeTextResults(Poll poll) {
        List<String> responses = freeTextVoteRepository
                .findByPoll_IdOrderByVotedAtDesc(poll.getId())
                .stream()
                .map(FreeTextVote::getResponseText)
                .collect(Collectors.toList());

        return PollResultsResponse.builder()
                .pollId(poll.getId())
                .question(poll.getQuestion())
                .pollType(poll.getPollType().name())
                .status(poll.getStatus().name())
                .maxChoices(0)
                .totalResponses(responses.size())
                .freeTextResponses(responses)
                .build();
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private Map<String, Long> resolvedCounts(Poll poll) {
        if (cacheService != null) {
            Map<String, Long> counts = cacheService.getAllVoteCounts(poll.getId().toString());
            if (!counts.isEmpty()) return counts;
        }
        log.debug("Redis unavailable or cache miss for poll {}, falling back to DB", poll.getId());
        List<Object[]> rows = voteRepository.countVotesByOption(poll.getId());
        Map<String, Long> dbCounts = new HashMap<>();
        for (Object[] row : rows) {
            dbCounts.put(row[0].toString(), (Long) row[1]);
        }
        if (cacheService != null && !dbCounts.isEmpty()) {
            cacheService.seedFromDatabase(poll.getId().toString(), dbCounts);
        }
        return dbCounts;
    }

    private List<UUID> validateOptionIds(CastVoteRequest request, int min, int max) {
        List<UUID> ids = request.getOptionIds();
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("At least one option must be selected");
        }
        if (ids.size() < min) {
            throw new IllegalArgumentException("Select at least " + min + " option(s)");
        }
        if (ids.size() > max) {
            throw new IllegalArgumentException("You may select at most " + max + " option(s)");
        }
        return ids;
    }

    private PollOption resolveOption(Poll poll, UUID optionId) {
        return poll.getOptions().stream()
                .filter(o -> o.getId().equals(optionId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("PollOption", "id", optionId.toString()));
    }

    private void validatePollActive(Poll poll) {
        if (poll.getStatus() != PollStatus.ACTIVE) {
            throw new PollClosedException("This poll is no longer accepting votes");
        }
        if (poll.getExpiresAt() != null && poll.getExpiresAt().isBefore(LocalDateTime.now())) {
            poll.setStatus(PollStatus.EXPIRED);
            pollRepository.save(poll);
            throw new PollClosedException("This poll has expired");
        }
    }

    private void publishVoteEvents(Poll poll, String username, String optionRef, Map<String, Long> counts) {
        if (pollEventProducer != null) {
            pollEventProducer.publishVoteSubmitted(VoteSubmittedEvent.builder()
                    .pollId(poll.getId().toString())
                    .optionId(optionRef)
                    .username(username)
                    .votedAt(LocalDateTime.now())
                    .currentVoteCounts(counts)
                    .build());
        }
        webSocketEventPublisher.publishVoteUpdate(poll.getId().toString(), counts);
    }
}
