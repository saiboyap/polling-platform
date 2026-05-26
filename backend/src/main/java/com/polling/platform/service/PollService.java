package com.polling.platform.service;

import com.polling.platform.annotation.AuditLogged;
import com.polling.platform.dto.event.PollCreatedEvent;
import com.polling.platform.dto.request.CreatePollRequest;
import com.polling.platform.dto.response.PollOptionResponse;
import com.polling.platform.dto.response.PollResponse;
import com.polling.platform.entity.*;
import com.polling.platform.exception.ResourceNotFoundException;
import com.polling.platform.exception.UnauthorizedException;
import com.polling.platform.kafka.producer.PollEventProducer;
import com.polling.platform.repository.PollRepository;
import com.polling.platform.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PollService {

    private final PollRepository pollRepository;
    private final UserRepository userRepository;
    private final PollEventProducer pollEventProducer;
    private final RedisVoteCacheService cacheService;

    @AuditLogged(event = "POLL_CREATED", entityType = "POLL")
    @Transactional
    public PollResponse createPoll(CreatePollRequest request, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));

        validateCreateRequest(request);

        Poll poll = Poll.builder()
                .question(request.getQuestion())
                .createdBy(user)
                .status(PollStatus.ACTIVE)
                .pollType(request.getPollType())
                .maxChoices(resolveMaxChoices(request))
                .expiresAt(request.getExpiresAt())
                .build();

        if (request.getPollType() != PollType.FREE_TEXT) {
            List<PollOption> opts = request.getOptions().stream()
                    .map(text -> PollOption.builder().poll(poll).optionText(text).build())
                    .collect(Collectors.toList());
            poll.setOptions(opts);
        }

        Poll saved = pollRepository.save(poll);

        pollEventProducer.publishPollCreated(PollCreatedEvent.builder()
                .pollId(saved.getId().toString())
                .question(saved.getQuestion())
                .createdBy(username)
                .createdAt(saved.getCreatedAt())
                .build());

        log.info("Poll created: id={} type={} by={}", saved.getId(), saved.getPollType(), username);
        return toPollResponse(saved, Map.of());
    }

    @Transactional(readOnly = true)
    public Page<PollResponse> getActivePolls(Pageable pageable) {
        return pollRepository.findByStatus(PollStatus.ACTIVE, pageable)
                .map(poll -> {
                    Map<String, Long> counts = poll.getPollType() != PollType.FREE_TEXT
                            ? cacheService.getAllVoteCounts(poll.getId().toString())
                            : Map.of();
                    return toPollResponse(poll, counts);
                });
    }

    @Transactional(readOnly = true)
    public PollResponse getPollById(UUID id) {
        Poll poll = pollRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Poll", "id", id.toString()));

        Map<String, Long> counts = poll.getPollType() != PollType.FREE_TEXT
                ? cacheService.getAllVoteCounts(id.toString())
                : Map.of();
        return toPollResponse(poll, counts);
    }

    @AuditLogged(event = "POLL_CLOSED", entityType = "POLL")
    @Transactional
    public void closePoll(UUID id, String username) {
        Poll poll = pollRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Poll", "id", id.toString()));

        if (!poll.getCreatedBy().getUsername().equals(username)) {
            throw new UnauthorizedException("You can only close your own polls");
        }

        poll.setStatus(PollStatus.CLOSED);
        pollRepository.save(poll);
        log.info("Poll {} closed by {}", id, username);
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private void validateCreateRequest(CreatePollRequest request) {
        if (request.getPollType() != PollType.FREE_TEXT) {
            List<String> opts = request.getOptions();
            if (opts == null || opts.size() < 2) {
                throw new IllegalArgumentException("SINGLE_CHOICE and MULTI_CHOICE polls require at least 2 options");
            }
        }
        if (request.getPollType() == PollType.MULTI_CHOICE && request.getMaxChoices() < 1) {
            throw new IllegalArgumentException("maxChoices must be at least 1 for MULTI_CHOICE polls");
        }
    }

    private int resolveMaxChoices(CreatePollRequest request) {
        return switch (request.getPollType()) {
            case SINGLE_CHOICE -> 1;
            case MULTI_CHOICE  -> request.getMaxChoices();
            case FREE_TEXT     -> 0;
        };
    }

    private PollResponse toPollResponse(Poll poll, Map<String, Long> voteCounts) {
        List<PollOptionResponse> options = poll.getPollType() != PollType.FREE_TEXT
                ? poll.getOptions().stream()
                        .map(opt -> PollOptionResponse.builder()
                                .id(opt.getId())
                                .optionText(opt.getOptionText())
                                .voteCount(voteCounts.getOrDefault(opt.getId().toString(), 0L))
                                .build())
                        .collect(Collectors.toList())
                : Collections.emptyList();

        long totalVotes = options.stream().mapToLong(PollOptionResponse::getVoteCount).sum();

        return PollResponse.builder()
                .id(poll.getId())
                .question(poll.getQuestion())
                .createdBy(poll.getCreatedBy().getUsername())
                .status(poll.getStatus().name())
                .pollType(poll.getPollType().name())
                .maxChoices(poll.getMaxChoices())
                .options(options)
                .expiresAt(poll.getExpiresAt())
                .createdAt(poll.getCreatedAt())
                .totalVotes(totalVotes)
                .build();
    }
}
