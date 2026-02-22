package io.github.akifacet.payment_service.messages;

import java.math.BigDecimal;

public record PaymentAuthorizedEvent(
        String type,
        String sagaId,
        Long orderId,
        String customerId,
        BigDecimal amount
) {
    public static final String TYPE = "PaymentAuthorizedEvent";

    public static PaymentAuthorizedEvent of(AuthorizePaymentCommand cmd) {
        return new PaymentAuthorizedEvent(TYPE, cmd.sagaId(), cmd.orderId(), cmd.customerId(), cmd.amount());
    }
}

