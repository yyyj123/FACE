package com.face.platform.packageaccount;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PackageAccountModuleBoundaryContractTest {

    @Test
    void packageModuleUsesApplicationServicesInsteadOfOtherModulesPrivateTables() throws Exception {
        String packages = Files.readString(
            Path.of("src/main/java/com/face/platform/packageaccount/PackageAccountService.java"),
            StandardCharsets.UTF_8
        );
        String transaction = Files.readString(
            Path.of("src/main/java/com/face/platform/transaction/TransactionService.java"),
            StandardCharsets.UTF_8
        );

        assertThat(packages).contains("ServiceRecordService");
        assertThat(packages).contains("TransactionPackageContextService");
        assertThat(packages).doesNotContain("FROM service_record");
        assertThat(packages).doesNotContain("JOIN service_record");
        assertThat(packages).doesNotContain("FROM sales_order");
        assertThat(packages).doesNotContain("JOIN sales_order");
        assertThat(packages).doesNotContain("UPDATE member_account");
        assertThat(packages).doesNotContain("INSERT INTO member_account_ledger");

        assertThat(transaction).contains("MemberAccountApplicationService");
        assertThat(transaction).doesNotContain("UPDATE member_account");
        assertThat(transaction).doesNotContain("INSERT INTO member_account_ledger");
    }
}
