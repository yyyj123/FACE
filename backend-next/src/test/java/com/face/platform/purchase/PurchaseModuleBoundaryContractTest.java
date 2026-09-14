package com.face.platform.purchase;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PurchaseModuleBoundaryContractTest {

    @Test
    void purchaseModuleUsesInventoryApplicationServiceInsteadOfWritingPrivateTables()
        throws Exception {
        String java = Files.readString(
            Path.of(
                "src/main/java/com/face/platform/purchase/"
                    + "PurchaseOrderApplicationService.java"
            ),
            StandardCharsets.UTF_8
        );
        String normalized = java.toUpperCase().replaceAll("\\s+", " ");

        assertThat(java).contains("InventoryReceiptApplicationService");
        assertThat(normalized).doesNotContain("INSERT INTO STOCK_BATCH");
        assertThat(normalized).doesNotContain("UPDATE STOCK_BATCH");
        assertThat(normalized).doesNotContain("INSERT INTO STOCK_BATCH_MOVEMENT");
        assertThat(normalized).doesNotContain("UPDATE STOCK_BALANCE");
        assertThat(normalized).doesNotContain("INSERT INTO INVENTORY_MOVEMENT");
    }
}
