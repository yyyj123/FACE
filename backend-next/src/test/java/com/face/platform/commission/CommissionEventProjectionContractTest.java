package com.face.platform.commission;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CommissionEventProjectionContractTest {

    @Test
    void projectionConsumesExistingDomainEventsWithoutOtherModulesWritingPrivateTables() throws Exception {
        String projection = Files.readString(
            Path.of("src/main/java/com/face/platform/commission/CommissionEventProjectionService.java"),
            StandardCharsets.UTF_8
        );
        String service = Files.readString(
            Path.of("src/main/java/com/face/platform/servicecare/ServiceRecordService.java"),
            StandardCharsets.UTF_8
        );
        String transaction = Files.readString(
            Path.of("src/main/java/com/face/platform/transaction/TransactionService.java"),
            StandardCharsets.UTF_8
        );
        String refund = Files.readString(
            Path.of("src/main/java/com/face/platform/transaction/RefundApplicationService.java"),
            StandardCharsets.UTF_8
        );

        assertThat(projection).contains("PaymentSucceeded");
        assertThat(projection).contains("ServiceRecordCompleted");
        assertThat(projection).contains("RefundCompleted");
        assertThat(projection).contains("commission_event_projection");
        assertThat(service).doesNotContain("commission_entry");
        assertThat(transaction).doesNotContain("commission_entry");
        assertThat(refund).doesNotContain("commission_entry");
    }
}
