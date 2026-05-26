package com.polling.platform.service;

import com.polling.platform.entity.AuditLog;
import com.polling.platform.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    /**
     * Persists an audit entry on a background thread so it never adds latency
     * to the main request path. Failures are swallowed — audit must never break
     * business operations.
     */
    @Async
    public void record(String eventType, String actor, String entityType,
                       String entityId, String ipAddress) {
        try {
            auditLogRepository.save(AuditLog.builder()
                    .eventType(eventType)
                    .actor(actor)
                    .entityType(entityType)
                    .entityId(entityId != null ? entityId : "")
                    .ipAddress(ipAddress)
                    .createdAt(LocalDateTime.now(ZoneOffset.UTC))
                    .build());
        } catch (Exception e) {
            log.warn("Audit write failed: event={} actor={} cause={}", eventType, actor, e.getMessage());
        }
    }
}
