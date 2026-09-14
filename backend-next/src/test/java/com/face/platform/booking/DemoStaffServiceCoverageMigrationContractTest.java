package com.face.platform.booking;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DemoStaffServiceCoverageMigrationContractTest {

    @Test
    void mapsActiveDemoTechniciansToActiveServicesWithoutIncludingTheStoreManager() throws Exception {
        String sql = Files.readString(Path.of(
            "src/main/resources/db/migration/V2026081802__demo_staff_service_coverage.sql"
        ));

        assertThat(sql).contains("st.staff_no LIKE 'DEMO-ST-%'");
        assertThat(sql).contains("JOIN staff_shop_assignment");
        assertThat(sql).contains("si.status = 'ACTIVE'");
        assertThat(sql).contains("NOT EXISTS");
        assertThat(sql).doesNotContain("st.id = 1");
    }
}
