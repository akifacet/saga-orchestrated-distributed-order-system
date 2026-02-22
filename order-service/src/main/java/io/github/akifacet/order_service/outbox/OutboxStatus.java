package io.github.akifacet.order_service.outbox;

public enum OutboxStatus {
    NEW,
    PROCESSING,
    SENT,
    FAILED
}


