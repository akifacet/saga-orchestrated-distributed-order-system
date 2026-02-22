package io.github.akifacet.payment_service.messages;

import java.math.BigDecimal;

public record AuthorizePaymentCommand(
        String type,
        String sagaId,
        Long orderId,
        String customerId,
        BigDecimal amount
) {
    public static final String TYPE = "AuthorizePaymentCommand";
}

