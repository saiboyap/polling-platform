package com.polling.platform.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.polling.platform.config.KafkaConfig;
import com.polling.platform.dto.event.PollCreatedEvent;
import com.polling.platform.dto.event.VoteSubmittedEvent;
import com.polling.platform.service.EventIdempotencyService;
import com.polling.platform.service.RedisVoteCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PollEventConsumer {

    private final ObjectMapper objectMapper;
    private final RedisVoteCacheService cacheService;
    private final EventIdempotencyService idempotencyService;

    @KafkaListener(
            topics = KafkaConfig.POLL_CREATED_TOPIC,
            groupId = "polling-platform-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumePollCreated(ConsumerRecord<String, String> record) throws Exception {
        PollCreatedEvent event = objectMapper.readValue(record.value(), PollCreatedEvent.class);
        log.info("Processing poll-created: eventId={} pollId={}", event.getEventId(), event.getPollId());

        idempotencyService.processIdempotently(
                event.getEventId(),
                "POLL_CREATED",
                record.value(),
                () -> cacheService.initializePollCache(event.getPollId())
        );
    }

    @KafkaListener(
            topics = KafkaConfig.VOTE_SUBMITTED_TOPIC,
            groupId = "polling-platform-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeVoteSubmitted(ConsumerRecord<String, String> record) throws Exception {
        VoteSubmittedEvent event = objectMapper.readValue(record.value(), VoteSubmittedEvent.class);
        log.info("Processing vote-submitted: eventId={} pollId={} user={}",
                event.getEventId(), event.getPollId(), event.getUsername());

        idempotencyService.processIdempotently(
                event.getEventId(),
                "VOTE_SUBMITTED",
                record.value(),
                () -> log.debug("Vote event acknowledged for poll {}", event.getPollId())
        );
    }

    @KafkaListener(
            topics = KafkaConfig.DLQ_TOPIC,
            groupId = "polling-platform-dlq-group",
            containerFactory = "dlqKafkaListenerContainerFactory"
    )
    public void consumeDlq(ConsumerRecord<String, String> record) {
        log.error("DLQ message received: topic={} key={} value={}",
                record.topic(), record.key(), record.value());
    }
}
