package com.face.platform.identity;

import com.face.platform.api.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
@Profile("!demo")
@ConditionalOnProperty(name = "face.sms.mode", havingValue = "http")
public class HttpSmsDeliveryAdapter implements SmsDeliveryPort {

    private final HttpClient httpClient;
    private final URI endpoint;
    private final String bearerToken;

    public HttpSmsDeliveryAdapter(
        @Value("${face.sms.http.endpoint:}") String endpoint,
        @Value("${face.sms.http.bearer-token:}") String bearerToken
    ) {
        if (endpoint == null || endpoint.isBlank() || bearerToken == null || bearerToken.isBlank()) {
            throw new IllegalStateException("HTTP SMS endpoint and bearer token are required");
        }
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        this.endpoint = URI.create(endpoint);
        this.bearerToken = bearerToken;
    }

    @Override
    public String mode() {
        return "HTTP";
    }

    @Override
    public boolean demo() {
        return false;
    }

    @Override
    public DeliveryReceipt send(String phone, String code, String purpose) {
        String body = "{\"phone\":\"" + phone + "\",\"code\":\"" + code
            + "\",\"purpose\":\"" + purpose + "\"}";
        HttpRequest request = HttpRequest.newBuilder(endpoint)
            .timeout(Duration.ofSeconds(8))
            .header("Authorization", "Bearer " + bearerToken)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
        try {
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw unavailable();
            }
            return new DeliveryReceipt("验证码已发送");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (Exception exception) {
            throw unavailable();
        }
    }

    private ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "验证码发送失败，请稍后重试");
    }
}
