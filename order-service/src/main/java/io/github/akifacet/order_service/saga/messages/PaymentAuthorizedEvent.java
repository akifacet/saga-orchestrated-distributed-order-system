package io.github.akifacet.order_service.saga.messages;

import java.math.BigDecimal;

public record PaymentAuthorizedEvent(
        String type,
        String sagaId,
        Long orderId,
        String customerId,
        BigDecimal amount
) {
    public static final String TYPE = "PaymentAuthorizedEvent";
}

