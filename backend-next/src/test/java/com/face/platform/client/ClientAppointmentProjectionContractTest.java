package com.face.platform.client;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ClientAppointmentProjectionContractTest {

    @Test
    void appointmentProjectionKeepsOneRowPerAppointmentWithoutAggregateJoinGrouping()
        throws Exception {
        Path source = Path.of(
            "src/main/java/com/face/platform/client/ClientPortalService.java"
        );
        String java = Files.readString(source, StandardCharsets.UTF_8)
            .toUpperCase()
            .replaceAll("\\s+", " ");

        assertThat(java).contains(
            "FROM APPOINTMENT_ITEM AI WHERE AI.APPOINTMENT_ID = A.ID"
        );
        assertThat(java).contains(
            "SO.ID = ( SELECT MAX(SO2.ID) FROM SALES_ORDER SO2"
        );
        assertThat(java).doesNotContain(
            "LEFT JOIN APPOINTMENT_ITEM AI ON AI.APPOINTMENT_ID = A.ID"
        );
        assertThat(java).doesNotContain(" GROUP BY A.ID");
    }
}
