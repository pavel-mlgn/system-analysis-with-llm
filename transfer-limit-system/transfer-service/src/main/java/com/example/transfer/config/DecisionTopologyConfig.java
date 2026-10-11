package com.example.transfer.config;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.JoinWindows;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.StreamJoined;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaStreams;
import org.springframework.kafka.support.serializer.JsonSerde;
import com.example.common.Transfer;
import com.example.transfer.service.TransferDecisionService;

import java.time.Duration;

@Configuration
@EnableKafkaStreams
public class DecisionTopologyConfig {

    private static final Logger LOG = LoggerFactory.getLogger(DecisionTopologyConfig.class);

    @Bean
    public KStream<Long, Transfer> decisionStream(StreamsBuilder builder, TransferDecisionService decisionService) {
        JsonSerde<Transfer> transferSerde = new JsonSerde<>(Transfer.class);
        KStream<Long, Transfer> limitResults = builder
                .stream("limit-results", Consumed.with(Serdes.Long(), transferSerde));

        limitResults.join(
                        builder.stream("screening-results", Consumed.with(Serdes.Long(), transferSerde)),
                        decisionService::decide,
                        JoinWindows.ofTimeDifferenceWithNoGrace(Duration.ofSeconds(10)),
                        StreamJoined.with(Serdes.Long(), transferSerde, transferSerde))
                .peek((key, decision) -> LOG.info("Decision for transfer {}: {}", decision.id(), decision.status()))
                .to("transfers");

        return limitResults;
    }
}
