package com.polling.platform.kafka.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polling.platform.config.KafkaConfig;
import com.polling.platform.dto.event.PollCreatedEvent;
import com.polling.platform.dto.event.VoteSubmittedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class PollEventProducer {

    private final KafkaSendGateway kafkaSendGateway;
    private final ObjectMapper     objectMapper;

    public void publishPollCreated(PollCreatedEvent event) {
        if (event.getEventId() == null) event.setEventId(UUID.randomUUID().toString());
        send(KafkaConfig.POLL_CREATED_TOPIC, event.getPollId(), event);
    }

    public void publishVoteSubmitted(VoteSubmittedEvent event) {
        if (event.getEventId() == null) event.setEventId(UUID.randomUUID().toString());
        send(KafkaConfig.VOTE_SUBMITTED_TOPIC, event.getPollId(), event);
    }

    private void send(String topic, String key, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            kafkaSendGateway.send(topic, key, json);
        } catch (JsonProcessingException e) {
            log.error("Serialization error for topic {}: {}", topic, e.getMessage());
        } catch (Exception e) {
            log.error("Send failed for topic={} key={}: {}", topic, key, e.getMessage());
        }
    }
}
