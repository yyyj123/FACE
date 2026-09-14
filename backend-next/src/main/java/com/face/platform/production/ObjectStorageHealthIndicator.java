package com.face.platform.production;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component("objectStorage")
@Profile("prod")
public class ObjectStorageHealthIndicator implements HealthIndicator {

    private final HttpClient client;
    private final URI readinessUri;

    public ObjectStorageHealthIndicator(@Value("${face.object-storage.endpoint}") String endpoint) {
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        this.readinessUri = URI.create(endpoint.replaceAll("/+$", "") + "/minio/health/ready");
    }

    @Override
    public Health health() {
        try {
            HttpRequest request = HttpRequest.newBuilder(readinessUri)
                .timeout(Duration.ofSeconds(3))
                .GET()
                .build();
            int status = client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            return status >= 200 && status < 300
                ? Health.up().withDetail("endpoint", readinessUri.getHost()).build()
                : Health.down().withDetail("status", status).build();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Health.down().withDetail("reason", "object storage probe interrupted").build();
        } catch (Exception exception) {
            return Health.down().withDetail("reason", "object storage readiness unavailable").build();
        }
    }
}
