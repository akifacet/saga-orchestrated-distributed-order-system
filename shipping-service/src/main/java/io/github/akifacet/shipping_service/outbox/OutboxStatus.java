package io.github.akifacet.shipping_service.outbox;

public enum OutboxStatus {
    NEW,
    PROCESSING,
    SENT,
    FAILED
}

