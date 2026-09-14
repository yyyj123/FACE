package com.face.platform.production;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ObjectStorageHealthIndicatorTest {

    @Test
    void reportsReadyEndpointAndFailsClosedWhenUnavailable() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/minio/health/ready", exchange -> {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        var indicator = new ObjectStorageHealthIndicator(
            "http://127.0.0.1:" + server.getAddress().getPort()
        );
        try {
            assertEquals(Status.UP, indicator.health().getStatus());
        } finally {
            server.stop(0);
        }
        assertEquals(Status.DOWN, indicator.health().getStatus());
    }
}
