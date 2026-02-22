package io.github.akifacet.shipping_service.messages;

public record ShipmentFailedEvent(
        String type,
        String sagaId,
        Long orderId,
        String reason
) {
    public static final String TYPE = "ShipmentFailedEvent";
}

