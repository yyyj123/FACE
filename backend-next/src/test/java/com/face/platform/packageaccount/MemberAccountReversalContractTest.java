package com.face.platform.packageaccount;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MemberAccountReversalContractTest {

    @Test
    void reversalQueryKeepsLedgerAndAccountIdentifiersDistinct() throws Exception {
        String java = Files.readString(
            Path.of(
                "src/main/java/com/face/platform/packageaccount/"
                    + "MemberAccountApplicationService.java"
            ),
            StandardCharsets.UTF_8
        );

        assertThat(java).contains("mal.id AS ledgerId");
        assertThat(java).contains("ma.id AS id");
    }
}
