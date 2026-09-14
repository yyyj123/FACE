package com.face.platform.identity;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("demo")
@ConditionalOnProperty(name = "face.sms.mode", havingValue = "demo", matchIfMissing = true)
public class DemoSmsDeliveryAdapter implements SmsDeliveryPort {

    private static final String DEMO_CODE = "888888";

    @Override
    public String mode() {
        return "DEMO";
    }

    @Override
    public boolean demo() {
        return true;
    }

    @Override
    public DeliveryReceipt send(String phone, String code, String purpose) {
        if (!DEMO_CODE.equals(code)) {
            throw new IllegalArgumentException("demo verification code must be fixed");
        }
        return new DeliveryReceipt("演示验证码，未发送真实短信");
    }
}
