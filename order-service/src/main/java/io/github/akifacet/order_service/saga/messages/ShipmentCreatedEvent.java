package io.github.akifacet.order_service.saga.messages;

public record ShipmentCreatedEvent(
        String type,
        String sagaId,
        Long orderId,
        String shipmentId
) {
    public static final String TYPE = "ShipmentCreatedEvent";
}

