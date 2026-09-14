package com.face.platform.v3.payment;

import com.face.platform.transaction.PaymentApplicationService;
import com.face.platform.v3.api.V3ApiResponse;
import com.face.platform.v3.api.V3RequestSupport;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v3/payment-channels")
public class V3PaymentCallbackController {

    private final PaymentApplicationService paymentService;

    public V3PaymentCallbackController(PaymentApplicationService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/{channelCode}/callbacks")
    public V3ApiResponse<Map<String, Object>> callback(
        @PathVariable String channelCode,
        @RequestHeader("X-Payment-Timestamp") long timestamp,
        @RequestHeader("X-Payment-Event-Id") String eventId,
        @RequestHeader("X-Payment-Signature") String signature,
        @RequestBody String rawBody,
        HttpServletRequest request
    ) {
        return V3ApiResponse.success(
            paymentService.handleCallback(
                channelCode,
                timestamp,
                eventId,
                signature,
                rawBody
            ),
            V3RequestSupport.requestId(request)
        );
    }
}

