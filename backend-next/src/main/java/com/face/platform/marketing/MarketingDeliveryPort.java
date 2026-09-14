package com.face.platform.marketing;

public interface MarketingDeliveryPort {

    void deliverInApp(Message message);

    record Message(
        long tenantId,
        long shopId,
        long campaignId,
        long recipientAccountId,
        String eventId,
        String title,
        String safeSummary,
        String actionPath
    ) {
    }
}
