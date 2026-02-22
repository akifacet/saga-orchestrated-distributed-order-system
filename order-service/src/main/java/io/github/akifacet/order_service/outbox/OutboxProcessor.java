package io.github.akifacet.order_service.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Outbox tablosundaki kayıtları periyodik olarak okuyup Kafka'ya publish eden komponent.
 * Bu sınıf sayesinde DB transaction'ı ile Kafka publish süreci gevşek bağlanmış olur.
 */
@Service
public class OutboxProcessor {

    private static final Logger log = LoggerFactory.getLogger(OutboxProcessor.class);

    private static final int BATCH_SIZE = 50;
    private static final int MAX_RETRY = 12;
    private static final Duration SEND_TIMEOUT = Duration.ofSeconds(5);

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxProcessor(OutboxEventRepository outboxEventRepository,
                           KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void processOutbox() {
        Instant now = Instant.now();
        List<OutboxEvent> events = outboxEventRepository.findNextBatch(now)
                .stream()
                .limit(BATCH_SIZE)
                .toList();

        if (events.isEmpty()) {
            return;
        }

        log.info("Processing {} outbox events", events.size());

        for (OutboxEvent event : events) {
            try {
                if (event.getRetryCount() >= MAX_RETRY) {
                    log.error("Outbox event id={} exceeded max retry, skipping", event.getId());
                    event.setStatus(OutboxStatus.FAILED);
                    continue;
                }

                if (event.getTopic() == null || event.getTopic().isBlank()) {
                    throw new IllegalStateException("Outbox event topic is blank");
                }

                event.setStatus(OutboxStatus.PROCESSING);

                var future = kafkaTemplate.send(
                        event.getTopic(),
                        event.getKey(),
                        event.getPayload()
                );

                var result = future.get(SEND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
                log.info("Sent outbox id={} topic={} partition={} offset={}",
                        event.getId(),
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());

                event.setStatus(OutboxStatus.SENT);
                event.setLastError(null);
            } catch (Exception e) {
                log.error("Error while processing outbox event id={}", event.getId(), e);
                event.setStatus(OutboxStatus.FAILED);
                event.setRetryCount(event.getRetryCount() + 1);
                event.setLastError(e.getClass().getSimpleName() + ": " + e.getMessage());
                event.setNextAttemptAt(Instant.now().plusSeconds(backoffSeconds(event.getRetryCount())));
            }
        }
    }

    private long backoffSeconds(int retryCount) {
        // 2^n backoff, max 5 minutes
        long seconds = (long) Math.min(300, Math.pow(2, Math.max(1, retryCount)));
        return seconds;
    }
}


