package com.face.platform.sc6;

import com.face.platform.servicecare.CustomerConfirmationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class Sc6FulfillmentJob {

    private final CustomerConfirmationService confirmationService;
    private final Sc6AfterSaleApplicationService afterSaleService;

    public Sc6FulfillmentJob(
        CustomerConfirmationService confirmationService,
        Sc6AfterSaleApplicationService afterSaleService
    ) {
        this.confirmationService = confirmationService;
        this.afterSaleService = afterSaleService;
    }

    @Scheduled(fixedDelayString = "${face.sc6.fulfillment-scan-ms:300000}")
    public void closeExpiredWork() {
        confirmationService.processExpired(100);
        afterSaleService.releaseCompletedRefunds(100);
        afterSaleService.closeExpiredCustomerResponses(100);
    }
}
