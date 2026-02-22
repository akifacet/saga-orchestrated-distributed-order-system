package io.github.akifacet.shipping_service.messages;

public record ShipmentCreatedEvent(
        String type,
        String sagaId,
        Long orderId,
        String shipmentId
) {
    public static final String TYPE = "ShipmentCreatedEvent";
}

