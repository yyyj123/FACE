package com.face.platform.checkout;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class Sc4OrderBenefitsMigrationContractTest {

    @Test
    void migrationExtendsTheExistingOrderAndPackageFactsWithoutEnteringSc5() throws Exception {
        String resource = "db/migration/V2026080304__sc4_order_cards_coupon_benefits.sql";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertThat(input).as("SC4 Flyway migration").isNotNull();
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8).toUpperCase();

            assertThat(sql).contains("ALTER TABLE `PACKAGE_PRODUCT`");
            assertThat(sql).contains("`CARD_TYPE`");
            assertThat(sql).contains("ALTER TABLE `PACKAGE_INSTANCE`");
            assertThat(sql).contains("`SOURCE_TYPE`");
            assertThat(sql).contains("CREATE TABLE `STORED_VALUE_BATCH`");
            assertThat(sql).contains("CREATE TABLE `STORED_VALUE_LEDGER`");
            assertThat(sql).contains("CREATE TABLE `BENEFIT_RESERVATION`");
            assertThat(sql).contains("CREATE TABLE `COUPON_TEMPLATE`");
            assertThat(sql).contains("CREATE TABLE `MEMBER_COUPON`");
            assertThat(sql).contains("CREATE TABLE `COUPON_LEDGER`");
            assertThat(sql).contains("CREATE TABLE `ORDER_PRICING_DECISION`");
            assertThat(sql).contains("CREATE TABLE `CHECKOUT_FULFILLMENT`");
            assertThat(sql).contains("'ZERO_AMOUNT'");
            assertThat(sql).contains("'COMBO_TIMES'", "'STORED_VALUE'", "'DISCOUNT'");
            assertThat(sql).contains("'ONLINE_PURCHASE'", "'OFFLINE_SALE'", "'GIFT'", "'REISSUE'");
            assertThat(sql).doesNotContain("CREATE TABLE `SALES_ORDER`");
            assertThat(sql).doesNotContain("CREATE TABLE `POINT_BATCH`");
            assertThat(sql).doesNotContain("CREATE TABLE `SHOPPING_CART`");
            assertThat(sql).doesNotContain("CREATE TABLE `COMMERCE_ORDER`");
            assertThat(sql).doesNotContain("DROP TABLE");
        }
    }
}
