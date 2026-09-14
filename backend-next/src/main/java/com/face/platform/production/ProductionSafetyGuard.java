package com.face.platform.production;

import com.face.platform.identity.SmsDeliveryPort;
import com.face.platform.payment.PaymentAdapterRegistry;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Clock;

@Component
@Profile("prod")
public class ProductionSafetyGuard implements ApplicationRunner {

    private final Environment environment;
    private final PaymentAdapterRegistry paymentAdapters;
    private final SmsDeliveryPort smsDelivery;

    public ProductionSafetyGuard(
        Environment environment,
        PaymentAdapterRegistry paymentAdapters,
        SmsDeliveryPort smsDelivery
    ) {
        this.environment = environment;
        this.paymentAdapters = paymentAdapters;
        this.smsDelivery = smsDelivery;
    }

    @Override
    public void run(ApplicationArguments args) {
        String paymentChannel = property("face.production.channels.payment", "disabled");
        String smsMode = property("face.sms.mode", "disabled");
        new ProductionSafetyPolicy(Clock.systemUTC()).validate(new ProductionSafetyPolicy.Configuration(
            environment.getActiveProfiles(),
            Boolean.parseBoolean(property("face.payment.demo-mock.enabled", "false")),
            property("face.payment.sandbox-secret", ""),
            smsMode,
            property("face.object-storage.endpoint", ""),
            property("face.object-storage.bucket", ""),
            paymentChannel,
            property("face.production.channels.logistics", "disabled"),
            Path.of(property("face.production.adapter-directory", "/app/adapters")),
            Path.of(property("face.production.channel-evidence-directory", "/run/channel-evidence")),
            paymentAdapters.configured(paymentChannel),
            !smsDelivery.demo() && smsMode.equalsIgnoreCase(smsDelivery.mode())
        ));
    }

    private String property(String name, String fallback) {
        return environment.getProperty(name, fallback);
    }
}
