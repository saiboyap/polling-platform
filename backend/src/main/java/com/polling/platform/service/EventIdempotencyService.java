package com.polling.platform.service;

import com.polling.platform.entity.ProcessedEvent;
import com.polling.platform.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventIdempotencyService {

    private final ProcessedEventRepository processedEventRepository;

    /**
     * Runs {@code action} exactly once per {@code eventId}.
     * Returns {@code true} if the action was executed, {@code false} if it was a duplicate.
     * Throws if the action itself throws — the caller's container factory will then retry.
     */
    @Transactional
    public boolean processIdempotently(String eventId, String eventType, String payload, Runnable action) {
        if (processedEventRepository.existsById(eventId)) {
            log.info("Duplicate event skipped: id={} type={}", eventId, eventType);
            return false;
        }

        action.run();

        try {
            processedEventRepository.save(ProcessedEvent.builder()
                    .id(eventId)
                    .eventType(eventType)
                    .processedAt(LocalDateTime.now(ZoneOffset.UTC))
                    .payload(payload)
                    .build());
        } catch (DataIntegrityViolationException e) {
            // Race condition: another pod processed this event simultaneously — treat as duplicate
            log.info("Concurrent duplicate event detected: id={} type={}", eventId, eventType);
            return false;
        }

        return true;
    }
}
