package io.github.akifacet.order_service.saga.messages;

import java.math.BigDecimal;

public record VoidPaymentCommand(
        String type,
        String sagaId,
        Long orderId,
        BigDecimal amount
) {
    public static final String TYPE = "VoidPaymentCommand";

    public static VoidPaymentCommand of(String sagaId, Long orderId, BigDecimal amount) {
        return new VoidPaymentCommand(TYPE, sagaId, orderId, amount);
    }
}

