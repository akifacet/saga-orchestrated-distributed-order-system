package io.github.akifacet.shipping_service.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    public static final String SHIPPING_COMMANDS_TOPIC = "shipping-commands";
    public static final String SHIPPING_EVENTS_TOPIC = "shipping-events";

    @Bean
    public NewTopic shippingCommandsTopic() {
        return TopicBuilder.name(SHIPPING_COMMANDS_TOPIC).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic shippingEventsTopic() {
        return TopicBuilder.name(SHIPPING_EVENTS_TOPIC).partitions(3).replicas(1).build();
    }
}

