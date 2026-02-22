package io.github.akifacet.order_service.saga.messages;

public record ReleaseStockCommand(
        String type,
        String sagaId,
        Long orderId
) {
    public static final String TYPE = "ReleaseStockCommand";

    public static ReleaseStockCommand of(String sagaId, Long orderId) {
        return new ReleaseStockCommand(TYPE, sagaId, orderId);
    }
}

