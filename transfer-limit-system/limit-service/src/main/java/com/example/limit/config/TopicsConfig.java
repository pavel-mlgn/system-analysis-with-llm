package com.example.limit.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class TopicsConfig {

    @Bean
    public NewTopic transfersTopic() {
        return TopicBuilder.name("transfers").partitions(3).compact().build();
    }

    @Bean
    public NewTopic limitResultsTopic() {
        return TopicBuilder.name("limit-results").partitions(3).compact().build();
    }

    @Bean
    public NewTopic screeningResultsTopic() {
        return TopicBuilder.name("screening-results").partitions(3).compact().build();
    }
}
