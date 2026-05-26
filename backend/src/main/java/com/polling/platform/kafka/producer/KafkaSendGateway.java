package com.polling.platform.kafka.producer;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Thin gateway around KafkaTemplate that adds a circuit breaker and retry policy.
 * Making send() synchronous (via .get()) is intentional — it lets Resilience4j
 * track real publish failures instead of fire-and-forget losses.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaSendGateway {

    private final KafkaTemplate<String, String> kafkaTemplate;

    @CircuitBreaker(name = "kafka-producer", fallbackMethod = "sendFallback")
    @Retry(name = "kafka-producer")
    public void send(String topic, String key, String json) throws Exception {
        kafkaTemplate.send(topic, key, json).get(2, TimeUnit.SECONDS);
        log.debug("Published event: topic={} key={}", topic, key);
    }

    private void sendFallback(String topic, String key, String json, Exception e) {
        log.error("Kafka circuit open — event dropped: topic={} key={} cause={}",
                topic, key, e.getMessage());
    }
}
