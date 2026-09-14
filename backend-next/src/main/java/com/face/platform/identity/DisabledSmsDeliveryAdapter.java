package com.face.platform.identity;

import com.face.platform.api.ApiException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@Profile("!demo")
@ConditionalOnProperty(name = "face.sms.mode", havingValue = "disabled", matchIfMissing = true)
public class DisabledSmsDeliveryAdapter implements SmsDeliveryPort {

    @Override
    public String mode() {
        return "DISABLED";
    }

    @Override
    public boolean demo() {
        return false;
    }

    @Override
    public DeliveryReceipt send(String phone, String code, String purpose) {
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "短信服务尚未配置，请联系管理员");
    }
}
