package io.github.akifacet.order_service.saga.messages;

public record CreateShipmentCommand(
        String type,
        String sagaId,
        Long orderId,
        String customerId,
        String productCode
) {
    public static final String TYPE = "CreateShipmentCommand";

    public static CreateShipmentCommand of(String sagaId, Long orderId, String customerId, String productCode) {
        return new CreateShipmentCommand(TYPE, sagaId, orderId, customerId, productCode);
    }
}

