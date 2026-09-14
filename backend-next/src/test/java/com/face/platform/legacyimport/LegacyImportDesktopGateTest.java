package com.face.platform.legacyimport;

import com.face.platform.api.ApiException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LegacyImportDesktopGateTest {

    @Test
    void batchMutationsRequireExplicitDesktopSurface() {
        assertThrows(ApiException.class, () -> LegacyImportController.requireDesktop(null));
        assertThrows(ApiException.class, () -> LegacyImportController.requireDesktop("MOBILE"));
        assertDoesNotThrow(() -> LegacyImportController.requireDesktop("DESKTOP"));
    }
}
