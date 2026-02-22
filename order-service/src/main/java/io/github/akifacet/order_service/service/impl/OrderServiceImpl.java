package io.github.akifacet.order_service.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.akifacet.order_service.config.KafkaConfig;
import io.github.akifacet.order_service.dto.OrderRequest;
import io.github.akifacet.order_service.dto.OrderResponse;
import io.github.akifacet.order_service.entity.Order;
import io.github.akifacet.order_service.outbox.OutboxEvent;
import io.github.akifacet.order_service.outbox.OutboxEventRepository;
import io.github.akifacet.order_service.outbox.OutboxStatus;
import io.github.akifacet.order_service.repository.OrderRepository;
import io.github.akifacet.order_service.service.OrderService;
import io.github.akifacet.order_service.saga.messages.ReserveStockCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OrderServiceImpl implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

    private static final String ORDER_AGGREGATE = "ORDER";
    private static final String SAGA_RESERVE_STOCK_COMMAND_TYPE = "ReserveStockCommand";

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public OrderServiceImpl(OrderRepository orderRepository,
                            OutboxEventRepository outboxEventRepository,
                            ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public OrderResponse createOrder(OrderRequest request) {
        Order order = new Order();
        order.setCustomerId(request.customerId());
        order.setProductCode(request.productCode());
        order.setPrice(request.price());
        order.setStatus("PENDING");

        // 1. Adım: Siparişi kaydet
        Order saved = orderRepository.save(order);

        // 2. Adım: Orchestrated Saga başlat (ilk komut: stok rezervasyonu)
        String sagaId = saved.getId().toString();
        ReserveStockCommand command = ReserveStockCommand.of(
                sagaId,
                saved.getId(),
                saved.getProductCode(),
                1,
                saved.getCustomerId(),
                saved.getPrice()
        );

        String payload;
        try {
            payload = objectMapper.writeValueAsString(command);
        } catch (JsonProcessingException e) {
            // Kurumsal projelerde genellikle custom exception + global handler olur.
            log.error("Failed to serialize saga command for orderId={}", saved.getId(), e);
            throw new IllegalStateException("Event serialization failed", e);
        }

        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setAggregateType(ORDER_AGGREGATE);
        outboxEvent.setAggregateId(saved.getId());
        outboxEvent.setTopic(KafkaConfig.INVENTORY_COMMANDS_TOPIC);
        outboxEvent.setKey(saved.getId().toString());
        outboxEvent.setType(SAGA_RESERVE_STOCK_COMMAND_TYPE);
        outboxEvent.setPayload(payload);
        outboxEvent.setStatus(OutboxStatus.NEW);

        outboxEventRepository.save(outboxEvent);

        return mapToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> getAllOrders() {
        return orderRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    private OrderResponse mapToResponse(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getProductCode(),
                order.getPrice(),
                order.getStatus()
        );
    }
}

