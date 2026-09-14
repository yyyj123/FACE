package com.face.platform.servicecare;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceModuleBoundaryContractTest {

    @Test
    void serviceCareModuleDoesNotWriteAppointmentPrivateTablesDirectly() throws Exception {
        Path source = Path.of(
            "src/main/java/com/face/platform/servicecare/ServiceRecordService.java"
        );
        String java = Files.readString(source, StandardCharsets.UTF_8)
            .toUpperCase()
            .replaceAll("\\s+", " ");

        assertThat(java).doesNotContain("UPDATE APPOINTMENT ");
        assertThat(java).doesNotContain("INSERT INTO APPOINTMENT_STATUS_HISTORY");
        assertThat(java).contains("APPOINTMENTLIFECYCLESERVICE");
    }
}
