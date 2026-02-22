package io.github.akifacet.order_service.dto;

import java.math.BigDecimal;

/**
 * Giriş (DTO) modeli.
 * Kurumsal projelerde entity yerine genellikle DTO'lar dış dünyaya açılır.
 */
public record OrderRequest(
        String customerId,
        String productCode,
        BigDecimal price
) {
}


