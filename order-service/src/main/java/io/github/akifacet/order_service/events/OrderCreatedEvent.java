package io.github.akifacet.order_service.events;

import java.math.BigDecimal;

/**
 * Kafka üzerinden diğer servislere gönderilecek domain eventi.
 * Entity değil, sadece dış sistemlerin ihtiyaç duyacağı alanları içerir.
 */
public record OrderCreatedEvent(
        Long orderId,
        String customerId,
        String productCode,
        BigDecimal price,
        String status
) {
}


