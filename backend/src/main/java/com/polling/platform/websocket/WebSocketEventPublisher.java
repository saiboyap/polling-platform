package com.polling.platform.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;
    private final SseEmitterRegistry sseEmitterRegistry;

    /** Pushes option-level vote counts for SINGLE_CHOICE / MULTI_CHOICE polls. */
    public void publishVoteUpdate(String pollId, Map<String, Long> voteCounts) {
        String destination = "/topic/polls/" + pollId + "/votes";
        messagingTemplate.convertAndSend(destination, voteCounts);
        sseEmitterRegistry.broadcast(pollId, voteCounts);
        log.debug("Published vote-count update to {} + SSE ({})", destination, voteCounts.size());
    }

    /** Pushes a total-response count for FREE_TEXT polls. */
    public void publishFreeTextUpdate(String pollId, long totalResponses) {
        String destination = "/topic/polls/" + pollId + "/votes";
        Map<String, Object> payload = Map.of("_freeTextTotal", totalResponses);
        messagingTemplate.convertAndSend(destination, payload);
        sseEmitterRegistry.broadcast(pollId, payload);
        log.debug("Published free-text update ({} responses) to {} + SSE", totalResponses, destination);
    }
}
