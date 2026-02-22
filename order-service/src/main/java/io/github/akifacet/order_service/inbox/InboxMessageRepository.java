package io.github.akifacet.order_service.inbox;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InboxMessageRepository extends JpaRepository<InboxMessage, Long> {
    boolean existsByMessageId(String messageId);
}
