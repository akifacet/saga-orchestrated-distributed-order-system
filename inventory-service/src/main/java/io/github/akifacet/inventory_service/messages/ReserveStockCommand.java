package io.github.akifacet.inventory_service.messages;

import java.math.BigDecimal;

public record ReserveStockCommand(
        String type,
        String sagaId,
        Long orderId,
        String productCode,
        int quantity,
        String customerId,
        BigDecimal price
) {
    public static final String TYPE = "ReserveStockCommand";
}

