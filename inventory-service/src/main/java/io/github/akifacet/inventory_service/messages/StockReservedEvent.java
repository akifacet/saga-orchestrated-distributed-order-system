package io.github.akifacet.inventory_service.messages;

import java.math.BigDecimal;

public record StockReservedEvent(
        String type,
        String sagaId,
        Long orderId,
        String productCode,
        int quantity,
        String customerId,
        BigDecimal price
) {
    public static final String TYPE = "StockReservedEvent";

    public static StockReservedEvent of(ReserveStockCommand cmd) {
        return new StockReservedEvent(TYPE, cmd.sagaId(), cmd.orderId(), cmd.productCode(), cmd.quantity(), cmd.customerId(), cmd.price());
    }
}

