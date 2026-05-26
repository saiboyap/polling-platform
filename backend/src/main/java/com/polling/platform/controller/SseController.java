package com.polling.platform.controller;

import com.polling.platform.websocket.SseEmitterRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/polls")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Streaming", description = "Server-Sent Events fallback for real-time vote updates")
public class SseController {

    private final SseEmitterRegistry registry;

    @GetMapping(value = "/{pollId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(
            summary = "Stream live vote updates (SSE)",
            description = "Hold this connection open to receive vote-count updates as Server-Sent Events. " +
                          "Used automatically by the frontend as a WebSocket fallback."
    )
    @ApiResponse(responseCode = "200", description = "Stream opened — events arrive as JSON data lines")
    public SseEmitter streamVotes(@PathVariable String pollId) {
        log.info("SSE client connected for poll {}", pollId);
        return registry.register(pollId);
    }
}
