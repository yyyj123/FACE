package com.face.platform.commission;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CommissionEventProjectionJob {

    private final CommissionEventProjectionService projectionService;

    public CommissionEventProjectionJob(
        CommissionEventProjectionService projectionService
    ) {
        this.projectionService = projectionService;
    }

    @Scheduled(
        fixedDelayString = "${face.commission.projection-delay-ms:3000}",
        initialDelayString = "${face.commission.projection-initial-delay-ms:3000}"
    )
    public void projectPendingEvents() {
        for (Long eventId : projectionService.pendingEventIds(20)) {
            try {
                projectionService.processEvent(eventId);
            } catch (RuntimeException exception) {
                projectionService.markFailure(eventId, exception);
            }
        }
    }
}

