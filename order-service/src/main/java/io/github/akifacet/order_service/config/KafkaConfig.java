package io.github.akifacet.order_service.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka ile ilgili temel konfigürasyonlar.
 * Topic tanımı gibi merkezi ayarları burada topluyoruz.
 */
@Configuration
public class KafkaConfig {

    public static final String INVENTORY_COMMANDS_TOPIC = "inventory-commands";
    public static final String INVENTORY_EVENTS_TOPIC = "inventory-events";

    public static final String PAYMENT_COMMANDS_TOPIC = "payment-commands";
    public static final String PAYMENT_EVENTS_TOPIC = "payment-events";

    public static final String SHIPPING_COMMANDS_TOPIC = "shipping-commands";
    public static final String SHIPPING_EVENTS_TOPIC = "shipping-events";

    @Bean
    public NewTopic inventoryCommandsTopic() {
        return TopicBuilder
                .name(INVENTORY_COMMANDS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic inventoryEventsTopic() {
        return TopicBuilder
                .name(INVENTORY_EVENTS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic paymentCommandsTopic() {
        return TopicBuilder
                .name(PAYMENT_COMMANDS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic paymentEventsTopic() {
        return TopicBuilder
                .name(PAYMENT_EVENTS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic shippingCommandsTopic() {
        return TopicBuilder
                .name(SHIPPING_COMMANDS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic shippingEventsTopic() {
        return TopicBuilder
                .name(SHIPPING_EVENTS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}


