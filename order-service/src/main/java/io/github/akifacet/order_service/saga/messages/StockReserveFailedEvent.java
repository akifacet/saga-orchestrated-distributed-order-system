package io.github.akifacet.order_service.saga.messages;

public record StockReserveFailedEvent(
        String type,
        String sagaId,
        Long orderId,
        String reason
) {
    public static final String TYPE = "StockReserveFailedEvent";
}

