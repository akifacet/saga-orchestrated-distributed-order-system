package io.github.akifacet.order_service.saga;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.akifacet.order_service.config.KafkaConfig;
import io.github.akifacet.order_service.inbox.InboxMessage;
import io.github.akifacet.order_service.inbox.InboxMessageRepository;
import io.github.akifacet.order_service.outbox.OutboxEvent;
import io.github.akifacet.order_service.outbox.OutboxEventRepository;
import io.github.akifacet.order_service.outbox.OutboxStatus;
import io.github.akifacet.order_service.repository.OrderRepository;
import io.github.akifacet.order_service.saga.messages.AuthorizePaymentCommand;
import io.github.akifacet.order_service.saga.messages.CreateShipmentCommand;
import io.github.akifacet.order_service.saga.messages.PaymentAuthorizedEvent;
import io.github.akifacet.order_service.saga.messages.PaymentFailedEvent;
import io.github.akifacet.order_service.saga.messages.ReleaseStockCommand;
import io.github.akifacet.order_service.saga.messages.VoidPaymentCommand;
import io.github.akifacet.order_service.saga.messages.ShipmentCreatedEvent;
import io.github.akifacet.order_service.saga.messages.ShipmentFailedEvent;
import io.github.akifacet.order_service.saga.messages.StockReserveFailedEvent;
import io.github.akifacet.order_service.saga.messages.StockReservedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OrderSagaListener {

    private static final Logger log = LoggerFactory.getLogger(OrderSagaListener.class);
    private static final String ORDER_AGGREGATE = "ORDER";

    private final ObjectMapper objectMapper;
    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final InboxMessageRepository inboxMessageRepository;

    public OrderSagaListener(ObjectMapper objectMapper,
                             OrderRepository orderRepository,
                             OutboxEventRepository outboxEventRepository,
                             InboxMessageRepository inboxMessageRepository) {
        this.objectMapper = objectMapper;
        this.orderRepository = orderRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.inboxMessageRepository = inboxMessageRepository;
    }

    @KafkaListener(topics = KafkaConfig.INVENTORY_EVENTS_TOPIC, groupId = "order-service")
    @Transactional
    public void onInventoryEvent(String message,
                                 @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) throws Exception {
        JsonNode root = objectMapper.readTree(message);
        String type = root.path("type").asText();
        String sagaId = root.has("sagaId") ? root.path("sagaId").asText() : "";
        Long orderId = root.has("orderId") ? root.path("orderId").asLong() : null;
        String messageId = topic + ":" + type + ":" + sagaId + ":" + (orderId != null ? orderId : "");
        if (inboxMessageRepository.existsByMessageId(messageId)) {
            log.info("Saga idempotent: skipping duplicate inventory event messageId={}", messageId);
            return;
        }
        inboxMessageRepository.save(InboxMessage.processed(messageId));

        if (StockReservedEvent.TYPE.equals(type)) {
            StockReservedEvent evt = objectMapper.treeToValue(root, StockReservedEvent.class);
            log.info("Inventory reserved stock for orderId={} sagaId={}", evt.orderId(), evt.sagaId());
            // next: authorize payment
            AuthorizePaymentCommand cmd = AuthorizePaymentCommand.of(
                    evt.sagaId(),
                    evt.orderId(),
                    evt.customerId(),
                    evt.price()
            );
            publish(KafkaConfig.PAYMENT_COMMANDS_TOPIC, evt.orderId().toString(), "AuthorizePaymentCommand", cmd, evt.orderId());
            return;
        }

        if (StockReserveFailedEvent.TYPE.equals(type)) {
            StockReserveFailedEvent evt = objectMapper.treeToValue(root, StockReserveFailedEvent.class);
            log.info("Inventory reserve failed for orderId={} sagaId={} reason={}", evt.orderId(), evt.sagaId(), evt.reason());
            orderRepository.findById(evt.orderId()).ifPresent(order -> order.setStatus("CANCELLED"));
            return;
        }

        log.warn("Unknown inventory event type={}, payload={}", type, message);
    }

    @KafkaListener(topics = KafkaConfig.PAYMENT_EVENTS_TOPIC, groupId = "order-service")
    @Transactional
    public void onPaymentEvent(String message,
                               @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) throws Exception {
        JsonNode root = objectMapper.readTree(message);
        String type = root.path("type").asText();
        String sagaId = root.has("sagaId") ? root.path("sagaId").asText() : "";
        Long orderId = root.has("orderId") ? root.path("orderId").asLong() : null;
        String messageId = topic + ":" + type + ":" + sagaId + ":" + (orderId != null ? orderId : "");
        if (inboxMessageRepository.existsByMessageId(messageId)) {
            log.info("Saga idempotent: skipping duplicate payment event messageId={}", messageId);
            return;
        }
        inboxMessageRepository.save(InboxMessage.processed(messageId));

        if (PaymentAuthorizedEvent.TYPE.equals(type)) {
            PaymentAuthorizedEvent evt = objectMapper.treeToValue(root, PaymentAuthorizedEvent.class);
            log.info("Payment authorized for orderId={} sagaId={}", evt.orderId(), evt.sagaId());
            // next: create shipment
            var order = orderRepository.findById(evt.orderId()).orElse(null);
            String productCode = order != null ? order.getProductCode() : null;
            CreateShipmentCommand cmd = CreateShipmentCommand.of(evt.sagaId(), evt.orderId(), evt.customerId(), productCode);
            publish(KafkaConfig.SHIPPING_COMMANDS_TOPIC, evt.orderId().toString(), "CreateShipmentCommand", cmd, evt.orderId());
            return;
        }

        if (PaymentFailedEvent.TYPE.equals(type)) {
            PaymentFailedEvent evt = objectMapper.treeToValue(root, PaymentFailedEvent.class);
            log.info("Payment failed for orderId={} sagaId={} reason={}", evt.orderId(), evt.sagaId(), evt.reason());
            // compensate: release stock, cancel order
            ReleaseStockCommand release = ReleaseStockCommand.of(evt.sagaId(), evt.orderId());
            publish(KafkaConfig.INVENTORY_COMMANDS_TOPIC, evt.orderId().toString(), ReleaseStockCommand.TYPE, release, evt.orderId());
            orderRepository.findById(evt.orderId()).ifPresent(order -> order.setStatus("CANCELLED"));
            return;
        }

        log.warn("Unknown payment event type={}, payload={}", type, message);
    }

    @KafkaListener(topics = KafkaConfig.SHIPPING_EVENTS_TOPIC, groupId = "order-service")
    @Transactional
    public void onShippingEvent(String message,
                                @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) throws Exception {
        JsonNode root = objectMapper.readTree(message);
        String type = root.path("type").asText();
        String sagaId = root.has("sagaId") ? root.path("sagaId").asText() : "";
        Long orderId = root.has("orderId") ? root.path("orderId").asLong() : null;
        String messageId = topic + ":" + type + ":" + sagaId + ":" + (orderId != null ? orderId : "");
        if (inboxMessageRepository.existsByMessageId(messageId)) {
            log.info("Saga idempotent: skipping duplicate shipping event messageId={}", messageId);
            return;
        }
        inboxMessageRepository.save(InboxMessage.processed(messageId));

        if (ShipmentCreatedEvent.TYPE.equals(type)) {
            ShipmentCreatedEvent evt = objectMapper.treeToValue(root, ShipmentCreatedEvent.class);
            log.info("Shipment created for orderId={} sagaId={} shipmentId={}", evt.orderId(), evt.sagaId(), evt.shipmentId());
            orderRepository.findById(evt.orderId()).ifPresent(order -> order.setStatus("CONFIRMED"));
            return;
        }

        if (ShipmentFailedEvent.TYPE.equals(type)) {
            ShipmentFailedEvent evt = objectMapper.treeToValue(root, ShipmentFailedEvent.class);
            log.info("Shipment failed for orderId={} sagaId={} reason={}", evt.orderId(), evt.sagaId(), evt.reason());
            // compensate: void payment + release stock, cancel order
            var order = orderRepository.findById(evt.orderId()).orElse(null);
            if (order != null) {
                VoidPaymentCommand voidCmd = VoidPaymentCommand.of(evt.sagaId(), evt.orderId(), order.getPrice());
                publish(KafkaConfig.PAYMENT_COMMANDS_TOPIC, evt.orderId().toString(), VoidPaymentCommand.TYPE, voidCmd, evt.orderId());
            }

            ReleaseStockCommand release = ReleaseStockCommand.of(evt.sagaId(), evt.orderId());
            publish(KafkaConfig.INVENTORY_COMMANDS_TOPIC, evt.orderId().toString(), ReleaseStockCommand.TYPE, release, evt.orderId());
            orderRepository.findById(evt.orderId()).ifPresent(o -> o.setStatus("CANCELLED"));
            return;
        }

        log.warn("Unknown shipping event type={}, payload={}", type, message);
    }

    private void publish(String topic, String key, String type, Object message, Long aggregateId) throws Exception {
        String payload = objectMapper.writeValueAsString(message);
        OutboxEvent e = new OutboxEvent();
        e.setAggregateType(ORDER_AGGREGATE);
        e.setAggregateId(aggregateId);
        e.setTopic(topic);
        e.setKey(key);
        e.setType(type);
        e.setPayload(payload);
        e.setStatus(OutboxStatus.NEW);
        outboxEventRepository.save(e);
    }
}

