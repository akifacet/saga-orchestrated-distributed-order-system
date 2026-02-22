package io.github.akifacet.payment_service.outbox;

public enum OutboxStatus {
    NEW,
    PROCESSING,
    SENT,
    FAILED
}

