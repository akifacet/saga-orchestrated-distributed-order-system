package io.github.akifacet.inventory_service.messages;

public record StockReleasedEvent(
        String type,
        String sagaId,
        Long orderId
) {
    public static final String TYPE = "StockReleasedEvent";

    public static StockReleasedEvent of(ReleaseStockCommand cmd) {
        return new StockReleasedEvent(TYPE, cmd.sagaId(), cmd.orderId());
    }
}

