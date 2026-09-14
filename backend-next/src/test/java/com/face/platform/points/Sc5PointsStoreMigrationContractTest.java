package com.face.platform.points;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class Sc5PointsStoreMigrationContractTest {

    @Test
    void migrationDefinesBatchPointsSharedSkuInventoryAndTraceableSplitPackages() throws Exception {
        String sql = Files.readString(
            Path.of("src/main/resources/db/migration/V2026080305__sc5_points_store_inventory.sql"),
            StandardCharsets.UTF_8
        );

        assertThat(sql)
            .contains("CREATE TABLE `points_batch`")
            .contains("CREATE TABLE `points_ledger`")
            .contains("CREATE TABLE `points_reservation_allocation`")
            .contains("CREATE TABLE `mall_sku_inventory`")
            .contains("CREATE TABLE `mall_inventory_ledger`")
            .contains("CREATE TABLE `mall_order`")
            .contains("CREATE TABLE `mall_sub_order`")
            .contains("CREATE TABLE `mall_package_sub_order`")
            .contains("'CASH', 'POINTS', 'COMBINATION'")
            .contains("'POINTS'" );

        String fallback = Files.readString(
            Path.of("src/main/resources/db/migration/V2026080307__sc5_default_points_seed_fallback.sql"),
            StandardCharsets.UTF_8
        );
        assertThat(fallback)
            .contains("'SUPER_ADMIN', 'ADMIN'")
            .contains("'GLOBAL_REDEEM'")
            .contains("'DAILY_CHECKIN'");

        String legacyOwner = Files.readString(
            Path.of("src/main/resources/db/migration/V2026080308__sc5_legacy_owner_points_seed.sql"),
            StandardCharsets.UTF_8
        );
        assertThat(legacyOwner)
            .contains("'SUPER_ADMIN', 'ADMIN', 'OWNER'")
            .contains("'GLOBAL_EARN'")
            .contains("'DAILY_CHECKIN'");

        String closure = Files.readString(
            Path.of("src/main/resources/db/migration/V2026080309__sc5_points_earn_and_mall_payment_callbacks.sql"),
            StandardCharsets.UTF_8
        );
        assertThat(closure)
            .contains("CREATE TABLE `points_clawback`")
            .contains("CREATE TABLE `mall_payment_callback_event`")
            .contains("`channel_event_id`");
    }
}
