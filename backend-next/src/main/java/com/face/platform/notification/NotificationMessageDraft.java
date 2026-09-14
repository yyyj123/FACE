package com.face.platform.notification;

public record NotificationMessageDraft(
    long recipientAccountId,
    Long shopId,
    String category,
    String title,
    String safeSummary,
    String actionPath
) {
}
