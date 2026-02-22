package io.github.akifacet.order_service.dto;

import java.math.BigDecimal;

/**
 * Çıkış (DTO) modeli.
 * Entity detaylarını gizlemek ve dış kontratı stabilize etmek için kullanılır.
 */
public record OrderResponse(
        Long id,
        String customerId,
        String productCode,
        BigDecimal price,
        String status
) {
}


