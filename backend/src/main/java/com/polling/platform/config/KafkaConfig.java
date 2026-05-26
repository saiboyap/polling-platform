package com.polling.platform.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    public static final String POLL_CREATED_TOPIC  = "poll-created-topic";
    public static final String VOTE_SUBMITTED_TOPIC = "vote-submitted-topic";
    public static final String DLQ_TOPIC            = "dlq-topic";

    @Bean
    public NewTopic pollCreatedTopic() {
        return TopicBuilder.name(POLL_CREATED_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic voteSubmittedTopic() {
        return TopicBuilder.name(VOTE_SUBMITTED_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic dlqTopic() {
        return TopicBuilder.name(DLQ_TOPIC)
                .partitions(1)
                .replicas(1)
                .build();
    }
}
