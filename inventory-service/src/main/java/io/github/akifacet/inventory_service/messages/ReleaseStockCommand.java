package io.github.akifacet.inventory_service.messages;

public record ReleaseStockCommand(
        String type,
        String sagaId,
        Long orderId
) {
    public static final String TYPE = "ReleaseStockCommand";
}

