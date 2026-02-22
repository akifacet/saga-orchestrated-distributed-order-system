package io.github.akifacet.shipping_service.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.akifacet.shipping_service.config.KafkaConfig;
import io.github.akifacet.shipping_service.domain.Shipment;
import io.github.akifacet.shipping_service.domain.ShipmentRepository;
import io.github.akifacet.shipping_service.inbox.InboxMessage;
import io.github.akifacet.shipping_service.inbox.InboxMessageRepository;
import io.github.akifacet.shipping_service.messages.CreateShipmentCommand;
import io.github.akifacet.shipping_service.messages.ShipmentCreatedEvent;
import io.github.akifacet.shipping_service.messages.ShipmentFailedEvent;
import io.github.akifacet.shipping_service.outbox.OutboxEvent;
import io.github.akifacet.shipping_service.outbox.OutboxEventRepository;
import io.github.akifacet.shipping_service.outbox.OutboxStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class ShippingCommandListener {

    private static final Logger log = LoggerFactory.getLogger(ShippingCommandListener.class);
    private static final String AGGREGATE = "SHIPMENT";

    private final ObjectMapper objectMapper;
    private final ShipmentRepository shipmentRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final InboxMessageRepository inboxMessageRepository;

    public ShippingCommandListener(ObjectMapper objectMapper,
                                  ShipmentRepository shipmentRepository,
                                  OutboxEventRepository outboxEventRepository,
                                  InboxMessageRepository inboxMessageRepository) {
        this.objectMapper = objectMapper;
        this.shipmentRepository = shipmentRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.inboxMessageRepository = inboxMessageRepository;
    }

    @KafkaListener(topics = KafkaConfig.SHIPPING_COMMANDS_TOPIC, groupId = "shipping-service")
    @Transactional
    public void onMessage(String message,
                          @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) throws Exception {
        JsonNode root = objectMapper.readTree(message);
        String type = root.path("type").asText();
        String sagaId = root.has("sagaId") ? root.path("sagaId").asText() : "";
        Long orderId = root.has("orderId") ? root.path("orderId").asLong() : null;
        String messageId = topic + ":" + type + ":" + sagaId + ":" + (orderId != null ? orderId : "");
        if (inboxMessageRepository.existsByMessageId(messageId)) {
            log.info("Inbox dedup: skipping duplicate messageId={}", messageId);
            return;
        }
        inboxMessageRepository.save(InboxMessage.processed(messageId));

        if (!CreateShipmentCommand.TYPE.equals(type)) {
            log.warn("Unknown command type={}, payload={}", type, message);
            return;
        }

        CreateShipmentCommand cmd = objectMapper.treeToValue(root, CreateShipmentCommand.class);

        var existing = shipmentRepository.findByOrderId(cmd.orderId());
        if (existing.isPresent() && "CREATED".equals(existing.get().getStatus())) {
            log.info("Idempotent shipment: already created orderId={}", cmd.orderId());
            publishEvent(cmd.orderId(), ShipmentCreatedEvent.TYPE,
                    new ShipmentCreatedEvent(ShipmentCreatedEvent.TYPE, cmd.sagaId(), cmd.orderId(), existing.get().getShipmentId()));
            return;
        }

        // Fail injection: productCode FAIL_SHIP -> fail
        if ("FAIL_SHIP".equalsIgnoreCase(cmd.productCode())) {
            ShipmentFailedEvent evt = new ShipmentFailedEvent(ShipmentFailedEvent.TYPE, cmd.sagaId(), cmd.orderId(), "Forced shipping failure");
            publishEvent(cmd.orderId(), ShipmentFailedEvent.TYPE, evt);
            return;
        }

        // demo: başarılı path
        Shipment s = existing.orElseGet(Shipment::new);
        s.setOrderId(cmd.orderId());
        s.setSagaId(cmd.sagaId());
        s.setCustomerId(cmd.customerId());
        s.setStatus("CREATED");
        s.setShipmentId("SHP-" + UUID.randomUUID());
        shipmentRepository.save(s);

        publishEvent(cmd.orderId(), ShipmentCreatedEvent.TYPE,
                new ShipmentCreatedEvent(ShipmentCreatedEvent.TYPE, cmd.sagaId(), cmd.orderId(), s.getShipmentId()));
    }

    private void publishEvent(Long orderId, String type, Object evt) throws Exception {
        String payload = objectMapper.writeValueAsString(evt);
        OutboxEvent e = new OutboxEvent();
        e.setAggregateType(AGGREGATE);
        e.setAggregateId(orderId);
        e.setTopic(KafkaConfig.SHIPPING_EVENTS_TOPIC);
        e.setKey(orderId.toString());
        e.setType(type);
        e.setPayload(payload);
        e.setStatus(OutboxStatus.NEW);
        outboxEventRepository.save(e);
        log.info("Outbox enqueued event={} orderId={}", type, orderId);
    }

    @SuppressWarnings("unused")
    private void publishFailure(Long orderId, String sagaId, String reason) throws Exception {
        ShipmentFailedEvent evt = new ShipmentFailedEvent(ShipmentFailedEvent.TYPE, sagaId, orderId, reason);
        publishEvent(orderId, ShipmentFailedEvent.TYPE, evt);
    }
}

