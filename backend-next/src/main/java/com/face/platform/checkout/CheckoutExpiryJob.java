package com.face.platform.checkout;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CheckoutExpiryJob {

    private final PaymentCompletionApplicationService completionService;

    public CheckoutExpiryJob(PaymentCompletionApplicationService completionService) {
        this.completionService = completionService;
    }

    @Scheduled(
        fixedDelayString = "${face.checkout.expiry-delay-ms:5000}",
        initialDelayString = "${face.checkout.expiry-initial-delay-ms:5000}"
    )
    public void releaseExpiredCheckouts() {
        for (Long paymentId : completionService.expiredPendingPaymentIds(50)) {
            completionService.expirePending(paymentId);
        }
    }
}
