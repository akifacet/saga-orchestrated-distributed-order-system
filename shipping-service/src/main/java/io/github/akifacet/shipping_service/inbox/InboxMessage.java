package io.github.akifacet.shipping_service.inbox;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "inbox_messages", indexes = {
        @Index(name = "ux_inbox_message_id", columnList = "messageId", unique = true)
})
public class InboxMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String messageId;

    private Instant processedAt;

    public static InboxMessage processed(String messageId) {
        InboxMessage m = new InboxMessage();
        m.messageId = messageId;
        m.processedAt = Instant.now();
        return m;
    }

    public Long getId() {
        return id;
    }

    public String getMessageId() {
        return messageId;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
