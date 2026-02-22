package io.github.akifacet.order_service.saga.messages;

public record StockReservedEvent(
        String type,
        String sagaId,
        Long orderId,
        String productCode,
        int quantity,
        String customerId,
        java.math.BigDecimal price
) {
    public static final String TYPE = "StockReservedEvent";
}

