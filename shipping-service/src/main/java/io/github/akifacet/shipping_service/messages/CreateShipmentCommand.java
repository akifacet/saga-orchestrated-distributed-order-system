package io.github.akifacet.shipping_service.messages;

public record CreateShipmentCommand(
        String type,
        String sagaId,
        Long orderId,
        String customerId,
        String productCode
) {
    public static final String TYPE = "CreateShipmentCommand";
}

