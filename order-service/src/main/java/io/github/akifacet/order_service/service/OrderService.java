package io.github.akifacet.order_service.service;

import io.github.akifacet.order_service.dto.OrderRequest;
import io.github.akifacet.order_service.dto.OrderResponse;

import java.util.List;

public interface OrderService {

    OrderResponse createOrder(OrderRequest request);

    List<OrderResponse> getAllOrders();
}


