package io.github.akifacet.inventory_service.messages;

public record StockReserveFailedEvent(
        String type,
        String sagaId,
        Long orderId,
        String reason
) {
    public static final String TYPE = "StockReserveFailedEvent";
}

