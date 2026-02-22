package io.github.akifacet.inventory_service.outbox;

public enum OutboxStatus {
    NEW,
    PROCESSING,
    SENT,
    FAILED
}

