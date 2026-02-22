package io.github.akifacet.order_service.saga.messages;

public record ShipmentFailedEvent(
        String type,
        String sagaId,
        Long orderId,
        String reason
) {
    public static final String TYPE = "ShipmentFailedEvent";
}

