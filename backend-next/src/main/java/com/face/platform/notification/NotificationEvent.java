package com.face.platform.notification;

public record NotificationEvent(
    long outboxRowId,
    String eventId,
    long tenantId,
    Long shopId,
    String aggregateType,
    String aggregateId,
    String eventType
) {
}
