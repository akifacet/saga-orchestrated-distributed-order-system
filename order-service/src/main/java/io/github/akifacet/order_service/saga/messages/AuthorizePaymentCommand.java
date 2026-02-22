package io.github.akifacet.order_service.saga.messages;

import java.math.BigDecimal;

public record AuthorizePaymentCommand(
        String type,
        String sagaId,
        Long orderId,
        String customerId,
        BigDecimal amount
) {
    public static final String TYPE = "AuthorizePaymentCommand";

    public static AuthorizePaymentCommand of(String sagaId, Long orderId, String customerId, BigDecimal amount) {
        return new AuthorizePaymentCommand(TYPE, sagaId, orderId, customerId, amount);
    }
}

