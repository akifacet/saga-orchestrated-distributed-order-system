package io.github.akifacet.payment_service.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.akifacet.payment_service.config.KafkaConfig;
import io.github.akifacet.payment_service.domain.Payment;
import io.github.akifacet.payment_service.domain.PaymentRepository;
import io.github.akifacet.payment_service.inbox.InboxMessage;
import io.github.akifacet.payment_service.inbox.InboxMessageRepository;
import io.github.akifacet.payment_service.messages.AuthorizePaymentCommand;
import io.github.akifacet.payment_service.messages.PaymentAuthorizedEvent;
import io.github.akifacet.payment_service.messages.PaymentFailedEvent;
import io.github.akifacet.payment_service.messages.PaymentVoidedEvent;
import io.github.akifacet.payment_service.messages.VoidPaymentCommand;
import io.github.akifacet.payment_service.outbox.OutboxEvent;
import io.github.akifacet.payment_service.outbox.OutboxEventRepository;
import io.github.akifacet.payment_service.outbox.OutboxStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PaymentCommandListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentCommandListener.class);
    private static final String AGGREGATE = "PAYMENT";

    private final ObjectMapper objectMapper;
    private final PaymentRepository paymentRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final InboxMessageRepository inboxMessageRepository;

    public PaymentCommandListener(ObjectMapper objectMapper,
                                 PaymentRepository paymentRepository,
                                 OutboxEventRepository outboxEventRepository,
                                 InboxMessageRepository inboxMessageRepository) {
        this.objectMapper = objectMapper;
        this.paymentRepository = paymentRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.inboxMessageRepository = inboxMessageRepository;
    }

    @KafkaListener(topics = KafkaConfig.PAYMENT_COMMANDS_TOPIC, groupId = "payment-service")
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

        if (AuthorizePaymentCommand.TYPE.equals(type)) {
            handleAuthorize(root);
            return;
        }

        if (VoidPaymentCommand.TYPE.equals(type)) {
            handleVoid(root);
            return;
        }

        log.warn("Unknown command type={}, payload={}", type, message);
    }

    private void handleAuthorize(JsonNode root) throws Exception {
        AuthorizePaymentCommand cmd = objectMapper.treeToValue(root, AuthorizePaymentCommand.class);

        var existing = paymentRepository.findByOrderId(cmd.orderId());
        if (existing.isPresent() && "AUTHORIZED".equals(existing.get().getStatus())) {
            log.info("Idempotent authorize: already authorized orderId={}", cmd.orderId());
            publishEvent(cmd.orderId(), PaymentAuthorizedEvent.TYPE, PaymentAuthorizedEvent.of(cmd));
            return;
        }

        // demo: her zaman başarılı. (ileride fail injection ekleriz)
        Payment p = existing.orElseGet(Payment::new);
        p.setOrderId(cmd.orderId());
        p.setSagaId(cmd.sagaId());
        p.setCustomerId(cmd.customerId());
        p.setAmount(cmd.amount());
        p.setStatus("AUTHORIZED");
        paymentRepository.save(p);

        publishEvent(cmd.orderId(), PaymentAuthorizedEvent.TYPE, PaymentAuthorizedEvent.of(cmd));
    }

    private void handleVoid(JsonNode root) throws Exception {
        VoidPaymentCommand cmd = objectMapper.treeToValue(root, VoidPaymentCommand.class);
        var existing = paymentRepository.findByOrderId(cmd.orderId());
        if (existing.isPresent()) {
            existing.get().setStatus("VOIDED");
            paymentRepository.save(existing.get());
        }
        PaymentVoidedEvent evt = new PaymentVoidedEvent(PaymentVoidedEvent.TYPE, cmd.sagaId(), cmd.orderId(), cmd.amount());
        publishEvent(cmd.orderId(), PaymentVoidedEvent.TYPE, evt);
    }

    private void publishEvent(Long orderId, String type, Object evt) throws Exception {
        String payload = objectMapper.writeValueAsString(evt);
        OutboxEvent e = new OutboxEvent();
        e.setAggregateType(AGGREGATE);
        e.setAggregateId(orderId);
        e.setTopic(KafkaConfig.PAYMENT_EVENTS_TOPIC);
        e.setKey(orderId.toString());
        e.setType(type);
        e.setPayload(payload);
        e.setStatus(OutboxStatus.NEW);
        outboxEventRepository.save(e);
        log.info("Outbox enqueued event={} orderId={}", type, orderId);
    }

    @SuppressWarnings("unused")
    private void publishFailure(Long orderId, String sagaId, String reason) throws Exception {
        PaymentFailedEvent evt = new PaymentFailedEvent(PaymentFailedEvent.TYPE, sagaId, orderId, reason);
        publishEvent(orderId, PaymentFailedEvent.TYPE, evt);
    }
}

