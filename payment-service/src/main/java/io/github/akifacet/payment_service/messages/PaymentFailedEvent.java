package io.github.akifacet.payment_service.messages;

public record PaymentFailedEvent(
        String type,
        String sagaId,
        Long orderId,
        String reason
) {
    public static final String TYPE = "PaymentFailedEvent";
}

