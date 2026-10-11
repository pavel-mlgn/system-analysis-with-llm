package com.example.transfer.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.LongSerializer;
import org.apache.kafka.common.serialization.Serializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
public class KafkaErrorHandlingConfig {

    @Bean
    public NewTopic transfersDlt() {
        return TopicBuilder.name("transfers.DLT").partitions(3).build();
    }

    @Bean
    public NewTopic limitResultsDlt() {
        return TopicBuilder.name("limit-results.DLT").partitions(3).build();
    }

    @Bean
    public NewTopic screeningResultsDlt() {
        return TopicBuilder.name("screening-results.DLT").partitions(3).build();
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(ProducerFactory<?, ?> producerFactory) {
        Map<String, Object> producerConfig = producerFactory.getConfigurationProperties();
        KafkaTemplate<Object, byte[]> rawTemplate = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(
                producerConfig, keySerializer(), new ByteArraySerializer()));
        KafkaTemplate<Object, Object> jsonTemplate = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(
                producerConfig, keySerializer(), new JsonSerializer<>()));

        Map<Class<?>, KafkaOperations<?, ?>> templates = new LinkedHashMap<>();
        templates.put(byte[].class, rawTemplate);
        templates.put(Object.class, jsonTemplate);

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(templates,
                (record, ex) -> new TopicPartition(record.topic() + ".DLT", record.partition()));
        recoverer.setFailIfSendResultIsError(false);
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3L));
    }

    private static Serializer<Object> keySerializer() {
        return new DelegatingByTypeSerializer(Map.of(
                byte[].class, new ByteArraySerializer(),
                Long.class, new LongSerializer()));
    }
}
