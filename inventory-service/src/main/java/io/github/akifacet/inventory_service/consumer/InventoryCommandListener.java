package io.github.akifacet.inventory_service.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.akifacet.inventory_service.config.KafkaConfig;
import io.github.akifacet.inventory_service.domain.Reservation;
import io.github.akifacet.inventory_service.domain.ReservationRepository;
import io.github.akifacet.inventory_service.inbox.InboxMessage;
import io.github.akifacet.inventory_service.inbox.InboxMessageRepository;
import io.github.akifacet.inventory_service.messages.ReserveStockCommand;
import io.github.akifacet.inventory_service.messages.ReleaseStockCommand;
import io.github.akifacet.inventory_service.messages.StockReserveFailedEvent;
import io.github.akifacet.inventory_service.messages.StockReservedEvent;
import io.github.akifacet.inventory_service.messages.StockReleasedEvent;
import io.github.akifacet.inventory_service.outbox.OutboxEvent;
import io.github.akifacet.inventory_service.outbox.OutboxEventRepository;
import io.github.akifacet.inventory_service.outbox.OutboxStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.messaging.handler.annotation.Header;

@Component
public class InventoryCommandListener {

    private static final Logger log = LoggerFactory.getLogger(InventoryCommandListener.class);
    private static final String AGGREGATE = "RESERVATION";

    private final ObjectMapper objectMapper;
    private final ReservationRepository reservationRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final InboxMessageRepository inboxMessageRepository;

    public InventoryCommandListener(ObjectMapper objectMapper,
                                   ReservationRepository reservationRepository,
                                   OutboxEventRepository outboxEventRepository,
                                   InboxMessageRepository inboxMessageRepository) {
        this.objectMapper = objectMapper;
        this.reservationRepository = reservationRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.inboxMessageRepository = inboxMessageRepository;
    }

    @KafkaListener(topics = KafkaConfig.INVENTORY_COMMANDS_TOPIC, groupId = "inventory-service")
    @Transactional
    public void onMessage(String message,
                          @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                          @Header(name = KafkaHeaders.RECEIVED_KEY, required = false) String key) throws Exception {
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

        if (ReserveStockCommand.TYPE.equals(type)) {
            handleReserve(root);
            return;
        }

        if (ReleaseStockCommand.TYPE.equals(type)) {
            handleRelease(root);
            return;
        }

        log.warn("Unknown command type={}, payload={}", type, message);
    }

    private void handleReserve(JsonNode root) throws Exception {
        ReserveStockCommand cmd = objectMapper.treeToValue(root, ReserveStockCommand.class);

        var existing = reservationRepository.findByOrderId(cmd.orderId());
        if (existing.isPresent() && "RESERVED".equals(existing.get().getStatus())) {
            log.info("Idempotent reserve: already reserved orderId={}", cmd.orderId());
            publishEvent(cmd.orderId(), cmd.sagaId(), StockReservedEvent.TYPE, StockReservedEvent.of(cmd));
            return;
        }

        // demo: her zaman başarılı. (ileride stok yok senaryosu ekleriz)
        Reservation r = existing.orElseGet(Reservation::new);
        r.setOrderId(cmd.orderId());
        r.setSagaId(cmd.sagaId());
        r.setProductCode(cmd.productCode());
        r.setQuantity(cmd.quantity());
        r.setStatus("RESERVED");
        reservationRepository.save(r);

        publishEvent(cmd.orderId(), cmd.sagaId(), StockReservedEvent.TYPE, StockReservedEvent.of(cmd));
    }

    private void handleRelease(JsonNode root) throws Exception {
        ReleaseStockCommand cmd = objectMapper.treeToValue(root, ReleaseStockCommand.class);
        var existing = reservationRepository.findByOrderId(cmd.orderId());
        if (existing.isPresent()) {
            existing.get().setStatus("RELEASED");
            reservationRepository.save(existing.get());
        }
        publishEvent(cmd.orderId(), cmd.sagaId(), StockReleasedEvent.TYPE, StockReleasedEvent.of(cmd));
    }

    private void publishEvent(Long orderId, String sagaId, String type, Object evt) throws Exception {
        String payload = objectMapper.writeValueAsString(evt);
        OutboxEvent e = new OutboxEvent();
        e.setAggregateType(AGGREGATE);
        e.setAggregateId(orderId);
        e.setTopic(KafkaConfig.INVENTORY_EVENTS_TOPIC);
        e.setKey(orderId.toString());
        e.setType(type);
        e.setPayload(payload);
        e.setStatus(OutboxStatus.NEW);
        outboxEventRepository.save(e);
        log.info("Outbox enqueued event={} orderId={} sagaId={}", type, orderId, sagaId);
    }

    @SuppressWarnings("unused")
    private void publishFailure(Long orderId, String sagaId, String reason) throws Exception {
        StockReserveFailedEvent evt = new StockReserveFailedEvent(StockReserveFailedEvent.TYPE, sagaId, orderId, reason);
        publishEvent(orderId, sagaId, StockReserveFailedEvent.TYPE, evt);
    }
}

