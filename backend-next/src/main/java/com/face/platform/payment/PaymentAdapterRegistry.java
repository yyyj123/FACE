package com.face.platform.payment;

import com.face.platform.api.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class PaymentAdapterRegistry {

    private final Map<String, PaymentChannelAdapter> adapters;

    public PaymentAdapterRegistry(List<PaymentChannelAdapter> adapterList) {
        Map<String, PaymentChannelAdapter> indexed = new LinkedHashMap<>();
        for (PaymentChannelAdapter adapter : adapterList) {
            indexed.put(adapter.channelCode().toUpperCase(Locale.ROOT), adapter);
        }
        this.adapters = Map.copyOf(indexed);
    }

    public PaymentChannelAdapter requireConfigured(String channelCode) {
        String normalized = channelCode == null
            ? ""
            : channelCode.trim().toUpperCase(Locale.ROOT);
        PaymentChannelAdapter adapter = adapters.get(normalized);
        if (adapter == null || !adapter.configured()) {
            throw new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "支付通道未配置，未改变订单、支付或退款状态"
            );
        }
        return adapter;
    }

    public boolean configured(String channelCode) {
        String normalized = channelCode == null
            ? ""
            : channelCode.trim().toUpperCase(Locale.ROOT);
        PaymentChannelAdapter adapter = adapters.get(normalized);
        return adapter != null && adapter.configured();
    }
}
