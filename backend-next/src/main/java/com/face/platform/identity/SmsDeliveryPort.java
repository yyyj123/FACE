package com.face.platform.identity;

public interface SmsDeliveryPort {

    String mode();

    boolean demo();

    DeliveryReceipt send(String phone, String code, String purpose);

    record DeliveryReceipt(String message) {
    }
}
