package com.face.platform.media;

import com.face.platform.api.ApiException;
import com.face.platform.security.TenantPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MediaStorageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void storesDetectedPngWithGeneratedTenantFileName() throws Exception {
        byte[] png = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1};
        MediaStorageService service = new MediaStorageService(tempDir.toString());

        MediaStorageService.StoredImage stored = service.storeImage(
            admin(),
            new MockMultipartFile("file", "wrong.jpg", "image/jpeg", png)
        );

        assertTrue(stored.fileName().startsWith("t7-"));
        assertTrue(stored.fileName().endsWith(".png"));
        assertEquals("/face-next/upload/" + stored.fileName(), stored.url());
        assertTrue(Files.exists(tempDir.resolve(stored.fileName())));
    }

    @Test
    void rejectsUnsupportedContentAndNonAdmin() {
        MediaStorageService service = new MediaStorageService(tempDir.toString());
        MockMultipartFile text = new MockMultipartFile("file", "fake.png", "image/png", "no".getBytes());
        assertThrows(ApiException.class, () -> service.storeImage(admin(), text));

        byte[] png = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        assertThrows(ApiException.class, () -> service.storeImage(
            new TenantPrincipal(1, 7, 1L, "member", List.of("MEMBER"), Set.of(), Set.of(1L), false),
            new MockMultipartFile("file", "image.png", "image/png", png)
        ));
    }

    private TenantPrincipal admin() {
        return new TenantPrincipal(1, 7, 1L, "admin", List.of("ADMIN"), Set.of(), Set.of(1L), false);
    }
}
