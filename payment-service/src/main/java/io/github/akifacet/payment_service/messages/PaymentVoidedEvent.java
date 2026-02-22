package io.github.akifacet.payment_service.messages;

import java.math.BigDecimal;

public record PaymentVoidedEvent(
        String type,
        String sagaId,
        Long orderId,
        BigDecimal amount
) {
    public static final String TYPE = "PaymentVoidedEvent";
}

