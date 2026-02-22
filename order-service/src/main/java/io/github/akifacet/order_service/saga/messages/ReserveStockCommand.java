package io.github.akifacet.order_service.saga.messages;

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

    public static ReserveStockCommand of(String sagaId,
                                         Long orderId,
                                         String productCode,
                                         int quantity,
                                         String customerId,
                                         BigDecimal price) {
        return new ReserveStockCommand(TYPE, sagaId, orderId, productCode, quantity, customerId, price);
    }
}

