package com.face.platform.notification;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NotificationProjectionJob {

    private final NotificationProjectionService projectionService;

    public NotificationProjectionJob(NotificationProjectionService projectionService) {
        this.projectionService = projectionService;
    }

    @Scheduled(
        initialDelayString = "${face.notification.initial-delay-ms:2500}",
        fixedDelayString = "${face.notification.projection-delay-ms:3000}"
    )
    public void project() {
        projectionService.projectPending();
    }
}
